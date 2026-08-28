package com.nuvio.tv.data.trailer

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TrailerSettingsDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbVideoResult
import com.nuvio.tv.data.remote.api.TrailerApi
import java.time.Clock
import java.net.URI
import java.time.Instant
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TrailerService"
private const val TMDB_TRAILER_FALLBACK_LANGUAGE = "en-US"
private val YOUTUBE_SOURCE_CACHE_TTL: Duration = Duration.ofHours(3)
private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

@Singleton
class TrailerService(
    private val trailerApi: TrailerApi,
    private val tmdbApi: TmdbApi,
    private val inAppYouTubeExtractor: InAppYouTubeExtractor,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
    private val tmdbService: TmdbService,
    private val clock: Clock,
    private val trailerSettingsDataStore: TrailerSettingsDataStore? = null
) {
    @Inject
    constructor(
        trailerApi: TrailerApi,
        tmdbApi: TmdbApi,
        inAppYouTubeExtractor: InAppYouTubeExtractor,
        tmdbSettingsDataStore: TmdbSettingsDataStore,
        tmdbService: TmdbService,
        trailerSettingsDataStore: TrailerSettingsDataStore
    ) : this(
        trailerApi = trailerApi,
        tmdbApi = tmdbApi,
        inAppYouTubeExtractor = inAppYouTubeExtractor,
        tmdbSettingsDataStore = tmdbSettingsDataStore,
        tmdbService = tmdbService,
        clock = Clock.systemUTC(),
        trailerSettingsDataStore = trailerSettingsDataStore
    )

    constructor(
        trailerApi: TrailerApi,
        tmdbApi: TmdbApi,
        inAppYouTubeExtractor: InAppYouTubeExtractor,
        tmdbSettingsDataStore: TmdbSettingsDataStore,
        tmdbService: TmdbService
    ) : this(
        trailerApi = trailerApi,
        tmdbApi = tmdbApi,
        inAppYouTubeExtractor = inAppYouTubeExtractor,
        tmdbSettingsDataStore = tmdbSettingsDataStore,
        tmdbService = tmdbService,
        clock = Clock.systemUTC(),
        trailerSettingsDataStore = null
    )

    // Cache: "title|year|tmdbId|type" -> trailer playback source (NEGATIVE_CACHE sentinel for misses)
    private val cache = ConcurrentHashMap<String, TrailerPlaybackSource>()
    private val NEGATIVE_CACHE = TrailerPlaybackSource(videoUrl = "")
    // Time-bound cache: youtubeVideoId -> resolved playback source (success-only)
    private val youtubeSourceCache = ConcurrentHashMap<String, CachedTrailerPlaybackSource>()
    // In-flight resolutions per YouTube id. Without this, two focus events for the
    // same card extracted in parallel and returned the same media on different CDN
    // hosts; the second URL replaced the first and restarted playback mid-frame.
    private val inFlightYoutubeSources = ConcurrentHashMap<String, Deferred<TrailerPlaybackSource?>>()
    // Detached from callers: a cancelled focus event must not cancel a resolution
    // that another focus event is already awaiting.
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val minResolutionHeight = AtomicInteger(720)

    init {
        val store = trailerSettingsDataStore
        if (store != null) {
            resolveScope.launch {
                store.settings
                    .map { it.minResolution.height }
                    .distinctUntilChanged()
                    .collect { height ->
                        minResolutionHeight.set(height)
                        youtubeSourceCache.clear()
                        cache.clear()
                    }
            }
        }
    }

    private fun qualityScopedKey(base: String): String = "$base@${minResolutionHeight.get()}"

    /**
     * Search for a trailer by title, year, tmdbId, and type.
     * Returns the trailer playback source (video URL + optional separate audio URL) or null.
     */
    suspend fun getTrailerPlaybackSource(
        title: String,
        year: String? = null,
        tmdbId: String? = null,
        type: String? = null,
        ignoreUseTrailersGate: Boolean = false
    ): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        // Read the TMDB settings once and reuse for both the "Disable Trailers"
        // gate and the trailer language lookup below. The gate respects the
        // user's "Disable Trailers in TMDB Enrichment" toggle: the TMDB path
        // below is the only trailer source surfaced through this function,
        // so when the toggle is off we return no trailer at all rather than
        // silently falling through to TMDB's /videos endpoint. See #1647.
        // Post-play recommendations bypass this gate because they have no
        // meta-addon trailer to fall back on.
        val tmdbSettings = runCatching { tmdbSettingsDataStore.settings.first() }.getOrNull()
        if (!ignoreUseTrailersGate && tmdbSettings?.useTrailers != true) {
            Log.d(TAG, "Trailers disabled in TMDB enrichment settings; skipping lookup")
            return@withContext null
        }
        val tmdbLanguage = normalizeTmdbTrailerLanguage(tmdbSettings?.language)

        val cacheKey = qualityScopedKey("$title|$year|$tmdbId|$type")

        cache[cacheKey]?.let { cached ->
            val hit = cached !== NEGATIVE_CACHE
            Log.d(TAG, "Cache hit for $cacheKey: $hit")
            return@withContext if (hit) cached else null
        }

        try {
            Log.d(TAG, "Searching trailer: title=$title, year=$year, tmdbId=$tmdbId, type=$type")

            // TMDB-first path. Gated on `useTrailers` above so the
            // user's toggle in TMDB enrichment settings is honored.
            val triedVideoIds = mutableSetOf<String>()
            val tmdbSource = getTrailerPlaybackSourceFromTmdbId(
                tmdbId = tmdbId,
                type = type,
                title = title,
                year = year,
                languageOverride = tmdbLanguage,
                triedVideoIds = triedVideoIds
            )
            if (tmdbSource != null) {
                cache[cacheKey] = tmdbSource
                return@withContext tmdbSource
            }

            val searchSource = getTrailerPlaybackSourceFromYouTubeSearch(
                title = title,
                year = year,
                skipVideoIds = triedVideoIds
            )
            if (searchSource != null) {
                cache[cacheKey] = searchSource
                return@withContext searchSource
            }

            Log.w(TAG, "No official trailer resolved via TMDB or YouTube search for $title")
            // Only cache negative result if tmdbId was available — if null, enrichment
            // may not have completed yet and a retry with tmdbId could succeed.
            if (tmdbId != null) {
                cache[cacheKey] = NEGATIVE_CACHE
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trailer for $title: ${e.message}", e)
            null
        }
    }

    /**
     * Search for a trailer and return its primary video URL for existing call sites.
     */
    suspend fun getTrailerUrl(
        title: String,
        year: String? = null,
        tmdbId: String? = null,
        type: String? = null
    ): String? {
        return getTrailerPlaybackSource(
            title = title,
            year = year,
            tmdbId = tmdbId,
            type = type
        )?.videoUrl
    }

    suspend fun getExternalTrailerUrl(
        tmdbId: String?,
        type: String?
    ): String? = withContext(Dispatchers.IO) {
        // Parse the id first so an invalid/null tmdbId short-circuits without
        // touching the settings DataStore at all.
        val numericTmdbId = tmdbId?.toIntOrNull() ?: return@withContext null
        // Read settings once and use for both the "Disable Trailers" gate and
        // the trailer language. See #1647 for the gate rationale.
        val tmdbSettings = runCatching { tmdbSettingsDataStore.settings.first() }.getOrNull()
        if (tmdbSettings?.useTrailers != true) {
            return@withContext null
        }
        val mediaType = normalizeTmdbMediaType(type)
        val tmdbLanguage = normalizeTmdbTrailerLanguage(tmdbSettings.language)
        val tmdbResults = when (mediaType) {
            "movie" -> fetchTmdbMovieVideos(numericTmdbId, tmdbLanguage)
            "tv" -> fetchTmdbTvVideos(numericTmdbId, tmdbLanguage)
            else -> fetchTmdbMovieVideos(numericTmdbId, tmdbLanguage) + fetchTmdbTvVideos(numericTmdbId, tmdbLanguage)
        }
        rankTmdbVideoCandidates(tmdbResults)
            .firstOrNull()
            ?.key
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://www.youtube.com/watch?v=$it" }
    }

    /**
     * TMDB-first resolution using /movie/{id}/videos or /tv/{id}/videos.
     */
    suspend fun getTrailerPlaybackSourceFromTmdbId(
        tmdbId: String?,
        type: String?,
        title: String? = null,
        year: String? = null,
        languageOverride: String? = null,
        triedVideoIds: MutableSet<String>? = null
    ): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        val numericTmdbId = tmdbId?.toIntOrNull() ?: return@withContext null
        val mediaType = normalizeTmdbMediaType(type)
        val tmdbLanguage = languageOverride ?: getPreferredTmdbTrailerLanguage()
        Log.d(
            TAG,
            "TMDB trailer lookup start: tmdbId=$numericTmdbId type=${mediaType ?: "unknown"} language=$tmdbLanguage"
        )

        val tmdbResults = when (mediaType) {
            "movie" -> fetchTmdbMovieVideos(numericTmdbId, tmdbLanguage)
            "tv" -> fetchTmdbTvVideos(numericTmdbId, tmdbLanguage)
            else -> fetchTmdbMovieVideos(numericTmdbId, tmdbLanguage) + fetchTmdbTvVideos(numericTmdbId, tmdbLanguage)
        }

        val candidates = rankTmdbVideoCandidates(tmdbResults, title, year)
        Log.d(TAG, "TMDB candidate count: ${candidates.size}")

        for (candidate in candidates) {
            val key = candidate.key?.trim().orEmpty()
            if (key.isBlank()) continue
            triedVideoIds?.add(key)
            Log.d(
                TAG,
                "TMDB selected candidate: type=${candidate.type.orEmpty()} " +
                    "name=${candidate.name.orEmpty()} official=${candidate.official == true} " +
                    "key=${obfuscateYoutubeKey(key)}"
            )

            val youtubeUrl = "https://www.youtube.com/watch?v=$key"
            val source = getTrailerPlaybackSourceFromYouTubeUrl(
                youtubeUrl = youtubeUrl,
                title = title,
                year = year
            )
            if (source != null) {
                return@withContext source
            }

            Log.d(
                TAG,
                "TMDB candidate extraction failed, trying next: key=${obfuscateYoutubeKey(key)}"
            )
        }

        null
    }

    private suspend fun getTrailerPlaybackSourceFromYouTubeSearch(
        title: String,
        year: String?,
        skipVideoIds: Set<String>
    ): TrailerPlaybackSource? {
        if (title.isBlank()) return null
        val collected = mutableListOf<YouTubeTrailerSearchHit>()
        val seenHits = mutableSetOf<String>()
        val attempted = skipVideoIds.toMutableSet()
        var extracts = 0
        for (query in TrailerOfficialSearch.searchQueries(title, year)) {
            val page = try {
                inAppYouTubeExtractor.searchTrailerVideos(query)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            for (hit in page) {
                if (seenHits.add(hit.videoId)) {
                    collected += hit
                }
            }
            val ranked = TrailerOfficialSearch.rankYouTubeHits(collected, title, year)
            if (ranked.isEmpty()) continue
            Log.d(
                TAG,
                "YouTube search ranked ${ranked.size} trailer hits for '$query' " +
                    "(top=${ranked.first().title})"
            )
            for (hit in ranked) {
                if (!attempted.add(hit.videoId)) continue
                val source = getTrailerPlaybackSourceFromYouTubeUrl(
                    youtubeUrl = "https://www.youtube.com/watch?v=${hit.videoId}",
                    title = title,
                    year = year
                )
                extracts++
                if (source != null) {
                    Log.d(TAG, "Using YouTube search hit: ${hit.title}")
                    return source
                }
                if (extracts >= TrailerOfficialSearch.MAX_SEARCH_EXTRACTS) return null
            }
        }
        return null
    }

    /**
     * Resolve a YouTube trailer URL to a playback source (prefers in-app extraction).
     */
    suspend fun getTrailerPlaybackSourceFromYouTubeUrl(
        youtubeUrl: String,
        title: String? = null,
        year: String? = null
    ): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        val youtubeKey = extractYouTubeVideoId(youtubeUrl)
        if (youtubeKey.isNullOrBlank()) {
            return@withContext resolveYouTubeSource(youtubeUrl, null, title, year)
        }
        val scopedKey = qualityScopedKey(youtubeKey)

        getValidCachedYoutubeSource(scopedKey)?.let { cached ->
            Log.d(TAG, "YouTube cache hit for key=${obfuscateYoutubeKey(youtubeKey)}")
            return@withContext cached
        }

        // Share a single resolution per video id so concurrent focus events cannot
        // hand back two different URLs for the same trailer.
        val pending = inFlightYoutubeSources.computeIfAbsent(scopedKey) {
            resolveScope.async {
                try {
                    resolveYouTubeSource(youtubeUrl, scopedKey, title, year)
                } finally {
                    inFlightYoutubeSources.remove(scopedKey)
                }
            }
        }
        try {
            pending.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error awaiting trailer resolution: ${e.message}", e)
            null
        }
    }

    private suspend fun resolveYouTubeSource(
        youtubeUrl: String,
        youtubeKey: String?,
        title: String?,
        year: String?
    ): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Attempting in-app YouTube extraction for ${summarizeUrl(youtubeUrl)}")
            val localSource = inAppYouTubeExtractor.extractPlaybackSource(youtubeUrl)
            if (localSource != null) {
                if (!youtubeKey.isNullOrBlank()) {
                    youtubeSourceCache[youtubeKey] = CachedTrailerPlaybackSource(
                        playbackSource = localSource,
                        cachedAt = Instant.now(clock),
                        expiresAt = extractUrlExpireInstant(localSource)
                    )
                }
                Log.d(
                    TAG,
                    "Using in-app YouTube source for ${summarizeUrl(youtubeUrl)} " +
                        "(audioPresent=${!localSource.audioUrl.isNullOrBlank()})"
                )
                return@withContext localSource
            }

            Log.w(
                TAG,
                "In-app extraction found no trailer at ${minResolutionHeight.get()}p for ${summarizeUrl(youtubeUrl)}"
            )
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error getting trailer from YouTube: ${e.message}", e)
            null
        }
    }

    /**
     * Compatibility method for existing callers expecting a single URL.
     */
    suspend fun getTrailerFromYouTubeUrl(
        youtubeUrl: String,
        title: String? = null,
        year: String? = null
    ): String? {
        return getTrailerPlaybackSourceFromYouTubeUrl(
            youtubeUrl = youtubeUrl,
            title = title,
            year = year
        )?.videoUrl
    }

    private suspend fun fetchTmdbMovieVideos(tmdbId: Int, preferredLanguage: String): List<TmdbVideoResult> {
        val localized = fetchTmdbMovieVideosOnce(tmdbId, preferredLanguage)
        if (localized.isNotEmpty() || preferredLanguage.equals(TMDB_TRAILER_FALLBACK_LANGUAGE, ignoreCase = true)) {
            return localized
        }
        Log.d(TAG, "TMDB movie videos localized miss for $tmdbId ($preferredLanguage), retrying $TMDB_TRAILER_FALLBACK_LANGUAGE")
        return fetchTmdbMovieVideosOnce(tmdbId, TMDB_TRAILER_FALLBACK_LANGUAGE)
    }

    private suspend fun fetchTmdbTvVideos(tmdbId: Int, preferredLanguage: String): List<TmdbVideoResult> {
        val localized = fetchTmdbTvVideosOnce(tmdbId, preferredLanguage)
        if (localized.isNotEmpty() || preferredLanguage.equals(TMDB_TRAILER_FALLBACK_LANGUAGE, ignoreCase = true)) {
            return localized
        }
        Log.d(TAG, "TMDB tv videos localized miss for $tmdbId ($preferredLanguage), retrying $TMDB_TRAILER_FALLBACK_LANGUAGE")
        return fetchTmdbTvVideosOnce(tmdbId, TMDB_TRAILER_FALLBACK_LANGUAGE)
    }

    private suspend fun fetchTmdbMovieVideosOnce(tmdbId: Int, language: String): List<TmdbVideoResult> {
        return try {
            val response = tmdbApi.getMovieVideos(
                movieId = tmdbId,
                apiKey = tmdbService.apiKey(),
                language = language
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB movie videos request failed ($tmdbId/$language): ${response.code()}")
                emptyList()
            } else {
                response.body()?.results.orEmpty()
            }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB movie videos error ($tmdbId/$language): ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchTmdbTvVideosOnce(tmdbId: Int, language: String): List<TmdbVideoResult> {
        return try {
            val response = tmdbApi.getTvVideos(
                tvId = tmdbId,
                apiKey = tmdbService.apiKey(),
                language = language
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB tv videos request failed ($tmdbId/$language): ${response.code()}")
                emptyList()
            } else {
                response.body()?.results.orEmpty()
            }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB tv videos error ($tmdbId/$language): ${e.message}")
            emptyList()
        }
    }

    private suspend fun getPreferredTmdbTrailerLanguage(): String {
        val rawLanguage = runCatching { tmdbSettingsDataStore.settings.first().language }.getOrNull()
        return normalizeTmdbTrailerLanguage(rawLanguage)
    }

    private fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun summarizeUrl(url: String): String {
        return runCatching {
            val uri = URI(url)
            val host = uri.host ?: "unknown-host"
            val path = uri.path ?: "/"
            "$host$path"
        }.getOrDefault(url.take(80))
    }

    private fun obfuscateYoutubeKey(key: String): String {
        if (key.length <= 4) return "****"
        return "***${key.takeLast(4)}"
    }

    private fun getValidCachedYoutubeSource(youtubeKey: String): TrailerPlaybackSource? {
        val cached = youtubeSourceCache[youtubeKey] ?: return null
        val now = Instant.now(clock)

        // Use URL expire timestamp if available, otherwise fall back to TTL
        val expired = cached.expiresAt?.let { now.isAfter(it) }
            ?: (Duration.between(cached.cachedAt, now) > YOUTUBE_SOURCE_CACHE_TTL)

        if (!expired) {
            return cached.playbackSource
        }

        youtubeSourceCache.remove(youtubeKey, cached)
        return null
    }

    fun clearCache() {
        cache.clear()
        youtubeSourceCache.clear()
    }

    /**
     * Extracts the YouTube URL expiration timestamp from `/expire/EPOCH/` or `expire=EPOCH`.
     */
    private fun extractUrlExpireInstant(source: TrailerPlaybackSource): Instant? {
        val url = source.videoUrl
        val epoch = Regex("/expire/(\\d+)/").find(url)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
            ?: return null
        return Instant.ofEpochSecond(epoch)
    }

    private fun extractYouTubeVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(YOUTUBE_VIDEO_ID_REGEX)) return trimmed

        return runCatching {
            val uri = URI(trimmed)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return@runCatching null
            when {
                host == "youtu.be" -> {
                    val id = uri.path?.trim('/')?.substringBefore('/')?.trim().orEmpty()
                    id.takeIf { it.matches(YOUTUBE_VIDEO_ID_REGEX) }
                }

                host == "youtube.com" || host.endsWith(".youtube.com") -> {
                    val path = uri.path.orEmpty()
                    val query = uri.rawQuery.orEmpty()

                    if (path.startsWith("/watch")) {
                        query.split("&")
                            .asSequence()
                            .mapNotNull { entry ->
                                val index = entry.indexOf('=')
                                if (index <= 0) return@mapNotNull null
                                val key = entry.substring(0, index)
                                val value = entry.substring(index + 1)
                                if (key == "v") value else null
                            }
                            .firstOrNull { it.matches(YOUTUBE_VIDEO_ID_REGEX) }
                    } else {
                        val segments = path.trim('/').split("/")
                        val candidate = when (segments.firstOrNull()?.lowercase()) {
                            "embed", "shorts", "live" -> segments.getOrNull(1)
                            else -> null
                        }
                        candidate?.takeIf { it.matches(YOUTUBE_VIDEO_ID_REGEX) }
                    }
                }

                else -> null
            }
        }.getOrNull()
    }

    private data class CachedTrailerPlaybackSource(
        val playbackSource: TrailerPlaybackSource,
        val cachedAt: Instant,
        val expiresAt: Instant? = null
    )
}

internal fun normalizeTmdbTrailerLanguage(language: String?): String {
    val normalized = language
        ?.trim()
        ?.replace('_', '-')
        ?.takeIf { it.isNotBlank() }
        ?: return TMDB_TRAILER_FALLBACK_LANGUAGE

    val formatted = if (normalized.contains('-')) {
        val parts = normalized.split("-", limit = 2)
        val locale = parts[0].lowercase()
        val region = parts.getOrNull(1)?.uppercase()?.takeIf { it.isNotBlank() }
        if (region != null) "$locale-$region" else locale
    } else {
        normalized.lowercase()
    }

    if (formatted == "en") return TMDB_TRAILER_FALLBACK_LANGUAGE

    // Map codes unsupported by TMDB to their closest equivalent
    return when (formatted) {
        "es-419" -> "es-MX"
        else -> formatted
    }
}

internal fun normalizeTmdbMediaType(type: String?): String? {
    return when (type?.lowercase()) {
        "movie", "film" -> "movie"
        "tv", "series", "show", "tvshow" -> "tv"
        else -> null
    }
}

internal fun rankTmdbVideoCandidates(
    results: List<TmdbVideoResult>,
    title: String? = null,
    year: String? = null
): List<TmdbVideoResult> = TrailerOfficialSearch.rankTmdb(results, title, year)
