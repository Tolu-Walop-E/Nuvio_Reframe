package com.nuvio.tv.data.trailer

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.local.TrailerSettingsDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InAppYouTubeExtractor"
private const val EXTRACTOR_TIMEOUT_MS = 30_000L
private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
private const val IOS_USER_AGENT =
    "com.google.ios.youtube/20.10.1 (iPhone16,2; U; CPU iOS 17_4 like Mac OS X)"
private const val PREFERRED_SEPARATE_CLIENT = "ios"
/** Total time allowed for probing adaptive candidates before falling back to HLS. */
private const val ADAPTIVE_VERIFY_BUDGET_MS = 4_000L
private const val MAX_ADAPTIVE_VERIFY_CANDIDATES = 8
private const val SEARCH_TIMEOUT_MS = 8_000L
private const val GOOGLEVIDEO_PROBE_BYTES = 1024L
private const val GOOGLEVIDEO_CONTINUATION_PROBE_START = 1024L * 1024

private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
private val API_KEY_REGEX = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
private val VISITOR_DATA_REGEX = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
private val QUALITY_LABEL_REGEX = Regex("(\\d{2,4})p")
private val CLEN_QUERY_REGEX = Regex("""(?:^|[?&])clen=(\d+)""")

private data class YouTubeClient(
    val key: String,
    val id: String,
    val version: String,
    val userAgent: String,
    val context: Map<String, Any>,
    val priority: Int
)

private data class WatchConfig(
    val apiKey: String?,
    val visitorData: String?
)

private data class StreamCandidate(
    val client: String,
    val priority: Int,
    val url: String,
    val score: Double,
    val hasN: Boolean,
    val itag: String,
    val height: Int,
    val fps: Int,
    val ext: String
)

private data class ManifestBestVariant(
    val url: String,
    val width: Int,
    val height: Int,
    val bandwidth: Long
)

private data class ManifestCandidate(
    val client: String,
    val priority: Int,
    val manifestUrl: String,
    val selectedVariantUrl: String,
    val height: Int,
    val bandwidth: Long
)

private val DEFAULT_HEADERS = mapOf(
    "accept-language" to "en-US,en;q=0.9",
    "user-agent" to DEFAULT_USER_AGENT
)

private val CLIENTS = listOf(
    YouTubeClient(
        key = "ios",
        id = "5",
        version = "20.10.1",
        userAgent = IOS_USER_AGENT,
        context = mapOf(
            "clientName" to "IOS",
            "clientVersion" to "20.10.1",
            "deviceModel" to "iPhone16,2",
            "osName" to "iPhone",
            "osVersion" to "17.4.0.21E219",
            "platform" to "MOBILE",
            "hl" to "en",
            "gl" to "US"
        ),
        priority = 0
    ),
    YouTubeClient(
        key = "tvhtml5",
        id = "7",
        version = "7.20250323.00.00",
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        context = mapOf(
            "clientName" to "TVHTML5",
            "clientVersion" to "7.20250323.00.00",
            "hl" to "en",
            "gl" to "US",
            "platform" to "TV"
        ),
        priority = 1
    ),
    YouTubeClient(
        key = "android",
        id = "3",
        version = "20.10.35",
        userAgent = "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip",
        context = mapOf(
            "clientName" to "ANDROID",
            "clientVersion" to "20.10.35",
            "osName" to "Android",
            "osVersion" to "14",
            "platform" to "MOBILE",
            "androidSdkVersion" to 34,
            "hl" to "en",
            "gl" to "US"
        ),
        priority = 2
    )
)

@Singleton
class InAppYouTubeExtractor @Inject constructor(
    trailerSettingsDataStore: TrailerSettingsDataStore
) {
    private val gson = Gson()
    private val qualityPolicy = AtomicReference(TrailerPreviewQualityPolicy.P720)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            trailerSettingsDataStore.settings.collect { settings ->
                qualityPolicy.set(TrailerPreviewQualityPolicy.from(settings.minResolution))
            }
        }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(com.nuvio.tv.core.network.IPv4FirstDns())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // --- Cached watch config (api key + visitor data) ---
    private data class CachedConfig(
        val apiKey: String,
        val visitorData: String?,
        val fetchedAt: Long = System.currentTimeMillis()
    )

    private val cachedConfig = AtomicReference<CachedConfig?>(null)
    private val configMutex = Mutex()

    companion object {
        /** How long cached visitor_data stays valid before a proactive refresh. */
        private const val CONFIG_TTL_MS = 3 * 60 * 60 * 1000L // 3 hours
    }

    /**
     * Returns cached watch config, fetching from watch page only if:
     *  - No cached config exists yet (first call)
     *  - Cache is older than CONFIG_TTL_MS
     *  - [forceRefresh] is true (e.g. after LOGIN_REQUIRED)
     */
    private suspend fun ensureWatchConfig(forceRefresh: Boolean = false): CachedConfig {
        // Fast path: return valid cache without locking
        if (!forceRefresh) {
            val current = cachedConfig.get()
            if (current != null && !isConfigStale(current)) {
                return current
            }
        }

        // Slow path: fetch new config under mutex (only one fetch at a time)
        return configMutex.withLock {
            // Double-check after acquiring lock
            if (!forceRefresh) {
                val current = cachedConfig.get()
                if (current != null && !isConfigStale(current)) {
                    return@withLock current
                }
            }

            Log.d(TAG, "Fetching watch page for visitor_data (forceRefresh=$forceRefresh)")
            val watchUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&hl=en"
            val watchResponse = performRequest(
                url = watchUrl,
                method = "GET",
                headers = DEFAULT_HEADERS
            )
            if (!watchResponse.ok) {
                // If we have a stale config, prefer it over failing
                val stale = cachedConfig.get()
                if (stale != null) {
                    Log.w(TAG, "Watch page failed (${watchResponse.status}), using stale config")
                    return@withLock stale
                }
                throw IllegalStateException("Failed to fetch watch page (${watchResponse.status})")
            }

            val parsed = getWatchConfig(watchResponse.body)
            val apiKey = parsed.apiKey ?: "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8" // fallback key
            val newConfig = CachedConfig(
                apiKey = apiKey,
                visitorData = parsed.visitorData
            )
            cachedConfig.set(newConfig)
            Log.d(TAG, "Watch config cached (visitor=${!parsed.visitorData.isNullOrBlank()})")
            newConfig
        }
    }

    private fun isConfigStale(config: CachedConfig): Boolean {
        return System.currentTimeMillis() - config.fetchedAt > CONFIG_TTL_MS
    }

    /** Invalidate cached config so next extraction re-fetches watch page. */
    fun invalidateConfig() {
        cachedConfig.set(null)
        Log.d(TAG, "Watch config invalidated")
    }

    suspend fun extractPlaybackSource(youtubeUrl: String): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        if (youtubeUrl.isBlank()) return@withContext null

        Log.d(TAG, "Starting Kotlin extraction for ${summarizeUrl(youtubeUrl)}")
        var source: TrailerPlaybackSource? = null
        try {
            source = withTimeout(EXTRACTOR_TIMEOUT_MS) {
                extractPlaybackSourceInternal(youtubeUrl, forceRefreshConfig = false)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (error: Exception) {
            Log.w(TAG, "Kotlin extractor failed for $youtubeUrl: ${error.message}")
        }

        // Retry with fresh config if first attempt returned nothing
        if (source == null) {
            Log.d(TAG, "First attempt failed, retrying with fresh watch config...")
            try {
                source = withTimeout(EXTRACTOR_TIMEOUT_MS) {
                    extractPlaybackSourceInternal(youtubeUrl, forceRefreshConfig = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Exception) {
                Log.w(TAG, "Kotlin extractor retry failed for $youtubeUrl: ${error.message}")
            }
        }

        if (source == null) {
            Log.w(TAG, "Kotlin extraction returned no playable source for ${summarizeUrl(youtubeUrl)}")
        } else {
            Log.d(
                TAG,
                "Kotlin extraction success for ${summarizeUrl(youtubeUrl)} " +
                    "(video=${summarizeUrl(source.videoUrl)}, audioPresent=${!source.audioUrl.isNullOrBlank()})"
            )
        }

        source
    }

    /**
     * InnerTube search for a query like "Dune 2021 official trailer".
     * Returns raw hits; [TrailerOfficialSearch] ranks them.
     */
    internal suspend fun searchTrailerVideos(query: String): List<YouTubeTrailerSearchHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            withTimeout(SEARCH_TIMEOUT_MS) {
                searchTrailerVideosInternal(query)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "YouTube trailer search timed out for '$query'")
            emptyList()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (error: Exception) {
            Log.w(TAG, "YouTube trailer search failed for '$query': ${error.message}")
            emptyList()
        }
    }

    private suspend fun searchTrailerVideosInternal(query: String): List<YouTubeTrailerSearchHit> {
        val config = ensureWatchConfig(forceRefresh = false)
        val client = CLIENTS.firstOrNull { it.key == "android" } ?: CLIENTS.first()
        val endpoint = "https://www.youtube.com/youtubei/v1/search?key=${Uri.encode(config.apiKey)}"
        val headers = buildMap {
            putAll(DEFAULT_HEADERS)
            put("content-type", "application/json")
            put("origin", "https://www.youtube.com")
            put("x-youtube-client-name", client.id)
            put("x-youtube-client-version", client.version)
            put("user-agent", client.userAgent)
            if (!config.visitorData.isNullOrBlank()) put("x-goog-visitor-id", config.visitorData)
        }
        val payload = buildMap<String, Any> {
            put("query", query)
            put("params", "EgIQAQ==")
            put("context", mapOf("client" to client.context))
        }
        val response = performRequest(
            url = endpoint,
            method = "POST",
            headers = headers,
            body = gson.toJson(payload)
        )
        if (!response.ok) {
            Log.w(TAG, "YouTube search API failed (${response.status}) for '$query'")
            return emptyList()
        }
        val parsed = gson.fromJson(response.body, Map::class.java) ?: return emptyList()
        val hits = mutableListOf<YouTubeTrailerSearchHit>()
        collectSearchHits(parsed, hits)
        Log.d(TAG, "YouTube search '$query' returned ${hits.size} video hits")
        return hits.distinctBy { it.videoId }
    }

    private fun collectSearchHits(node: Any?, out: MutableList<YouTubeTrailerSearchHit>) {
        when (node) {
            is Map<*, *> -> {
                val videoId = node["videoId"] as? String
                if (videoId != null && VIDEO_ID_REGEX.matches(videoId)) {
                    val title = richText(node, "title").ifBlank { richText(node, "headline") }
                    if (title.isNotBlank() && !isLiveSearchResult(node)) {
                        out += YouTubeTrailerSearchHit(
                            videoId = videoId,
                            title = title,
                            channel = richText(node, "ownerText").ifBlank {
                                richText(node, "shortBylineText")
                            },
                            durationSeconds = TrailerOfficialSearch.parseDurationSeconds(
                                richText(node, "lengthText")
                            )
                        )
                    }
                }
                node.values.forEach { collectSearchHits(it, out) }
            }
            is List<*> -> node.forEach { collectSearchHits(it, out) }
        }
    }

    private fun isLiveSearchResult(node: Map<*, *>): Boolean {
        return node.listMapValue("badges").any { badge ->
            badge.toString().contains("LIVE", ignoreCase = true)
        }
    }

    private fun richText(node: Map<*, *>, key: String): String {
        val value = node[key] ?: return ""
        return when (value) {
            is String -> value
            is Map<*, *> -> {
                val simple = value["simpleText"]?.toString().orEmpty()
                if (simple.isNotBlank()) return simple
                val runs = value["runs"] as? List<*> ?: return ""
                runs.mapNotNull { run ->
                    (run as? Map<*, *>)?.get("text")?.toString()
                }.joinToString("")
            }
            else -> ""
        }
    }

    private suspend fun extractPlaybackSourceInternal(
        youtubeUrl: String,
        forceRefreshConfig: Boolean
    ): TrailerPlaybackSource? {
        val videoId = extractVideoId(youtubeUrl) ?: return null

        // Use cached config instead of fetching watch page every time
        val config = ensureWatchConfig(forceRefresh = forceRefreshConfig)
        Log.d(TAG, "Using config: apiKey=${config.apiKey.take(10)}... visitor=${!config.visitorData.isNullOrBlank()}")

        val progressive = mutableListOf<StreamCandidate>()
        val adaptiveVideo = mutableListOf<StreamCandidate>()
        val adaptiveAudio = mutableListOf<StreamCandidate>()
        val manifestUrls = mutableListOf<Triple<String, Int, String>>()
        var loginRequiredCount = 0

        for (client in CLIENTS) {
            kotlinx.coroutines.yield()
            try {
                val playerResponse = fetchPlayerResponse(
                    apiKey = config.apiKey,
                    videoId = videoId,
                    client = client,
                    visitorData = config.visitorData,
                    cookieHeader = null
                )

                // Check for LOGIN_REQUIRED which means visitor_data is stale
                val playabilityStatus = playerResponse.mapValue("playabilityStatus")
                val status = playabilityStatus?.stringValue("status")
                if (status == "LOGIN_REQUIRED") {
                    loginRequiredCount++
                    Log.w(TAG, "Client ${client.key}: LOGIN_REQUIRED")
                    continue
                }
                if (status != null && status != "OK") {
                    continue
                }

                val streamingData = playerResponse.mapValue("streamingData") ?: continue
                val hlsManifestUrl = streamingData.stringValue("hlsManifestUrl")
                if (!hlsManifestUrl.isNullOrBlank()) {
                    manifestUrls += Triple(client.key, client.priority, hlsManifestUrl)
                }

                for (format in streamingData.listMapValue("formats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    if (!mimeType.contains("video/") && mimeType.isNotBlank()) continue
                    if (!isHardwareDecodable(mimeType)) continue
                    // Progressive must include audio — video-only "formats" play silent cards.
                    val hasMuxedAudio =
                        format.stringValue("audioQuality") != null ||
                            format.numberValue("audioSampleRate") != null ||
                            format.numberValue("audioChannels") != null ||
                            mimeType.contains("mp4a") ||
                            mimeType.contains("opus")
                    if (!hasMuxedAudio) continue

                    val height = (format.numberValue("height")
                        ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble()
                        ?: 0.0).toInt()
                    val fps = (format.numberValue("fps") ?: 0.0).toInt()
                    val bitrate = format.numberValue("bitrate")
                        ?: format.numberValue("averageBitrate")
                        ?: 0.0

                    progressive += StreamCandidate(
                        client = client.key,
                        priority = client.priority,
                        url = url,
                        score = videoScore(height, fps, bitrate),
                        hasN = hasNParam(url),
                        itag = format.stringValue("itag").orEmpty(),
                        height = height,
                        fps = fps,
                        ext = if (mimeType.contains("webm")) "webm" else "mp4"
                    )
                }

                for (format in streamingData.listMapValue("adaptiveFormats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    val hasVideo = mimeType.contains("video/")
                    val hasAudio = mimeType.contains("audio/") || mimeType.startsWith("audio/")

                    if (hasVideo) {
                        // A codec without a hardware decoder (AV1 on Shield) leaves the
                        // video renderer at 0x0 while audio keeps playing — the card
                        // shows its poster with sound. Drop those candidates.
                        if (!isHardwareDecodable(mimeType)) {
                            continue
                        }
                        val height = (format.numberValue("height")
                            ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble()
                            ?: 0.0).toInt()
                        val fps = (format.numberValue("fps") ?: 0.0).toInt()
                        val bitrate = format.numberValue("bitrate")
                            ?: format.numberValue("averageBitrate")
                            ?: 0.0

                        adaptiveVideo += StreamCandidate(
                            client = client.key,
                            priority = client.priority,
                            url = url,
                            score = videoScore(height, fps, bitrate),
                            hasN = hasNParam(url),
                            itag = format.stringValue("itag").orEmpty(),
                            height = height,
                            fps = fps,
                            ext = if (mimeType.contains("webm")) "webm" else "mp4"
                        )
                    } else if (hasAudio) {
                        val bitrate = format.numberValue("bitrate")
                            ?: format.numberValue("averageBitrate")
                            ?: 0.0
                        val asr = format.numberValue("audioSampleRate") ?: 0.0

                        adaptiveAudio += StreamCandidate(
                            client = client.key,
                            priority = client.priority,
                            url = url,
                            score = audioScore(bitrate, asr),
                            hasN = hasNParam(url),
                            itag = format.stringValue("itag").orEmpty(),
                            height = 0,
                            fps = 0,
                            ext = if (mimeType.contains("webm")) "webm" else "m4a"
                        )
                    }
                }
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Client ${client.key} failed: ${error.message}")
                }
            }

        }

        // If all clients returned LOGIN_REQUIRED, invalidate config for next attempt
        if (loginRequiredCount == CLIENTS.size) {
            Log.w(TAG, "All ${CLIENTS.size} clients returned LOGIN_REQUIRED, invalidating config")
            invalidateConfig()
            return null
        }

        if (manifestUrls.isEmpty() && progressive.isEmpty() && adaptiveVideo.isEmpty() && adaptiveAudio.isEmpty()) {
            return null
        }

        var bestManifest: ManifestCandidate? = null
        for ((clientKey, priority, manifestUrl) in manifestUrls) {
            try {
                val variant = parseHlsManifest(manifestUrl, clientUserAgent(clientKey)) ?: continue
                val candidate = ManifestCandidate(
                    client = clientKey,
                    priority = priority,
                    manifestUrl = manifestUrl,
                    selectedVariantUrl = variant.url,
                    height = variant.height,
                    bandwidth = variant.bandwidth
                )
                if (
                    bestManifest == null ||
                    isBetterPreviewHeight(candidate.height, bestManifest.height) ||
                    (candidate.height == bestManifest.height && candidate.bandwidth > bestManifest.bandwidth)
                ) {
                    bestManifest = candidate
                }
            } catch (error: Exception) {
                Log.w(TAG, "Manifest parse failed client=$clientKey: ${error.message}")
            }
        }

        val bestProgressive = sortCandidates(progressive)
            .firstOrNull { TrailerPreviewQuality.isPreferred(it.height, policy()) }

        val preferredAdaptive = adaptiveVideo.count { TrailerPreviewQuality.isPreferred(it.height, policy()) }
        Log.i(
            TAG,
            "Streams for $videoId: hlsHeight=${bestManifest?.height ?: 0} " +
                "adaptive=${adaptiveVideo.size} preferredAdaptive=$preferredAdaptive " +
                "progressive=${progressive.size} policy=${policy().minHeight}-${policy().maxHeight}"
        )

        // iOS HLS is muxed and survives on Shield when googlevideo adaptive URLs 403.
        // Try it before spending the adaptive probe budget.
        if (bestManifest != null && TrailerPreviewQuality.isPreferred(bestManifest.height, policy())) {
            Log.i(TAG, "Using HLS muxed manifest height=${bestManifest.height} client=${bestManifest.client}")
            val resolvedManifestVariant = resolveReachableManifestUrl(
                bestManifest.selectedVariantUrl,
                clientUserAgent(bestManifest.client)
            )
            if (resolvedManifestVariant != null) {
                return TrailerPlaybackSource(videoUrl = resolvedManifestVariant, audioUrl = null)
            }
            Log.w(TAG, "Preferred HLS variant failed probe client=${bestManifest.client}")
        }

        // A verified progressive rendition is muxed, so it avoids the separate
        // adaptive audio URL whose CDN commonly rejects continuation ranges.
        // Prefer it whenever it still meets the configured quality floor.
        val resolvedProgressive = bestProgressive?.url?.let {
            resolveReachableUrl(
                it,
                clientUserAgent(bestProgressive.client),
                requireContinuation = true
            )
        }
        if (resolvedProgressive != null) {
            Log.i(
                TAG,
                "Using verified progressive muxed ${bestProgressive.height}p " +
                    "itag=${bestProgressive.itag} client=${bestProgressive.client}"
            )
            return TrailerPlaybackSource(videoUrl = resolvedProgressive, audioUrl = null)
        }

        kotlinx.coroutines.yield()
        val verifiedPair = withTimeoutOrNull(ADAPTIVE_VERIFY_BUDGET_MS) {
            pickVerifiedAdaptivePair(adaptiveVideo, adaptiveAudio)
        }
        if (verifiedPair != null) {
            return TrailerPlaybackSource(videoUrl = verifiedPair.first, audioUrl = verifiedPair.second)
        }

        Log.w(
            TAG,
            "No verified trailer source for $videoId " +
                "(hls=${bestManifest?.height ?: 0} adaptivePreferred=$preferredAdaptive)"
        )
        return null
    }

    private fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (VIDEO_ID_REGEX.matches(trimmed)) return trimmed

        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        return runCatching {
            val uri = Uri.parse(normalized)
            val host = uri.host?.lowercase().orEmpty()
            if (host.endsWith("youtu.be")) {
                val id = uri.pathSegments.firstOrNull()
                if (!id.isNullOrBlank() && VIDEO_ID_REGEX.matches(id)) {
                    return id
                }
            }

            val queryId = uri.getQueryParameter("v")
            if (!queryId.isNullOrBlank() && VIDEO_ID_REGEX.matches(queryId)) {
                return queryId
            }

            val segments = uri.pathSegments
            if (segments.size >= 2) {
                val first = segments[0]
                val second = segments[1]
                if ((first == "embed" || first == "shorts" || first == "live") && VIDEO_ID_REGEX.matches(second)) {
                    return second
                }
            }

            null
        }.getOrNull()
    }

    private fun getWatchConfig(html: String): WatchConfig {
        val apiKey = API_KEY_REGEX.find(html)?.groupValues?.getOrNull(1)
        val visitorData = VISITOR_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)
        return WatchConfig(apiKey = apiKey, visitorData = visitorData)
    }

    private fun fetchPlayerResponse(
        apiKey: String,
        videoId: String,
        client: YouTubeClient,
        visitorData: String?,
        cookieHeader: String?
    ): Map<*, *> {
        val endpoint = "https://www.youtube.com/youtubei/v1/player?key=${Uri.encode(apiKey)}"

        val headers = buildMap {
            putAll(DEFAULT_HEADERS)
            put("content-type", "application/json")
            put("origin", "https://www.youtube.com")
            put("x-youtube-client-name", client.id)
            put("x-youtube-client-version", client.version)
            put("user-agent", client.userAgent)
            if (!visitorData.isNullOrBlank()) put("x-goog-visitor-id", visitorData)
            if (!cookieHeader.isNullOrBlank()) put("cookie", cookieHeader)
        }

        val payload = buildMap<String, Any> {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            val clientContext = client.context.toMutableMap()
            if (!visitorData.isNullOrBlank()) {
                clientContext["visitorData"] = visitorData
            }
            put("context", mapOf("client" to clientContext))
            put("playbackContext", mapOf(
                "contentPlaybackContext" to mapOf("html5Preference" to "HTML5_PREF_WANTS")
            ))
        }

        val response = performRequest(
            url = endpoint,
            method = "POST",
            headers = headers,
            body = gson.toJson(payload)
        )
        if (!response.ok) {
            throw IllegalStateException("player API ${client.key} failed (${response.status})")
        }

        val parsed = gson.fromJson(response.body, Map::class.java)
        return parsed ?: emptyMap<String, Any>()
    }

    private fun parseHlsManifest(manifestUrl: String, userAgent: String): ManifestBestVariant? {
        val response = performRequest(
            url = manifestUrl,
            method = "GET",
            headers = mapOf(
                "accept-language" to "en-US,en;q=0.9",
                "user-agent" to userAgent,
                "origin" to "https://www.youtube.com"
            )
        )
        if (!response.ok) {
            throw IllegalStateException("Failed to fetch HLS manifest (${response.status})")
        }

        val lines = response.body
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        var bestVariant: ManifestBestVariant? = null

        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue

            val attrs = parseHlsAttributeList(line)
            val nextLine = lines.getOrNull(i + 1) ?: continue
            if (nextLine.startsWith("#")) continue

            val resolution = attrs["RESOLUTION"].orEmpty()
            val (width, height) = parseResolution(resolution)
            val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L

            val candidate = ManifestBestVariant(
                url = absolutizeUrl(manifestUrl, nextLine),
                width = width,
                height = height,
                bandwidth = bandwidth
            )

            if (
                bestVariant == null ||
                isBetterPreviewHeight(candidate.height, bestVariant.height) ||
                (
                    candidate.height == bestVariant.height &&
                        candidate.bandwidth > bestVariant.bandwidth
                    ) ||
                (
                    candidate.height == bestVariant.height &&
                        candidate.bandwidth == bestVariant.bandwidth &&
                        candidate.width > bestVariant.width
                    )
            ) {
                bestVariant = candidate
            }
        }

        return bestVariant
    }

    private fun parseHlsAttributeList(line: String): Map<String, String> {
        val index = line.indexOf(':')
        if (index == -1) return emptyMap()

        val raw = line.substring(index + 1)
        val out = LinkedHashMap<String, String>()
        val key = StringBuilder()
        val value = StringBuilder()
        var inKey = true
        var inQuote = false

        for (ch in raw) {
            if (inKey) {
                if (ch == '=') {
                    inKey = false
                } else {
                    key.append(ch)
                }
                continue
            }

            if (ch == '"') {
                inQuote = !inQuote
                continue
            }

            if (ch == ',' && !inQuote) {
                val k = key.toString().trim()
                if (k.isNotEmpty()) {
                    out[k] = value.toString().trim()
                }
                key.clear()
                value.clear()
                inKey = true
                continue
            }

            value.append(ch)
        }

        val lastKey = key.toString().trim()
        if (lastKey.isNotEmpty()) {
            out[lastKey] = value.toString().trim()
        }

        return out
    }

    private fun parseResolution(raw: String): Pair<Int, Int> {
        val parts = raw.split('x')
        if (parts.size != 2) return 0 to 0
        val width = parts[0].toIntOrNull() ?: 0
        val height = parts[1].toIntOrNull() ?: 0
        return width to height
    }

    private fun parseQualityLabel(label: String?): Int? {
        if (label.isNullOrBlank()) return null
        val match = QUALITY_LABEL_REGEX.find(label) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun hasNParam(url: String): Boolean {
        return runCatching {
            !Uri.parse(url).getQueryParameter("n").isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun policy(): TrailerPreviewQualityPolicy = qualityPolicy.get()

    private fun videoScore(height: Int, fps: Int, bitrate: Double): Double {
        return TrailerPreviewQuality.heightScore(height, fps, bitrate, policy())
    }

    private fun isBetterPreviewHeight(candidate: Int, best: Int): Boolean {
        return TrailerPreviewQuality.isBetterHeight(candidate, best, policy())
    }

    private fun audioScore(bitrate: Double, audioSampleRate: Double): Double {
        return bitrate * 1_000_000.0 + audioSampleRate
    }

    private fun sortCandidates(items: List<StreamCandidate>): List<StreamCandidate> {
        return items.sortedWith(
            compareByDescending<StreamCandidate> { it.score }
                .thenBy { if (it.hasN) 1 else 0 }
                .thenBy { containerPreference(it.ext) }
                .thenBy { it.priority }
        )
    }

    private fun containerPreference(ext: String): Int {
        return when (ext.lowercase()) {
            "mp4", "m4a" -> 0
            "webm" -> 1
            else -> 2
        }
    }

    /**
     * Returns the first adaptive video+audio pair that is actually reachable.
     *
     * Only renditions that meet the configured minimum (720p or 1080p) are probed.
     * Below-floor streams are skipped so a missing 720p trailer does not play 360p.
     */
    private suspend fun pickVerifiedAdaptivePair(
        videos: List<StreamCandidate>,
        audios: List<StreamCandidate>
    ): Pair<String, String>? {
        if (videos.isEmpty() || audios.isEmpty()) return null
        val preferred = videos.filter { TrailerPreviewQuality.isPreferred(it.height, policy()) }
        if (preferred.isEmpty()) return null
        return probeAdaptiveWave(preferred, audios)
    }

    private suspend fun probeAdaptiveWave(
        videos: List<StreamCandidate>,
        audios: List<StreamCandidate>
    ): Pair<String, String>? {
        val orderedVideos = videos
            .sortedWith(
                compareBy<StreamCandidate> { clientTrust(it.client) }
                    .thenBy { if (it.hasN) 1 else 0 }
                    .thenByDescending { it.score }
                    .thenBy { containerPreference(it.ext) }
            )
            .take(MAX_ADAPTIVE_VERIFY_CANDIDATES)

        for (video in orderedVideos) {
            val sameClientAudio = audios.filter { it.client == video.client }
            val audioCandidates = (if (sameClientAudio.isNotEmpty()) sameClientAudio else audios)
                .sortedWith(
                    compareBy<StreamCandidate> { audioCompatibility(video.ext, it.ext) }
                        .thenBy { if (it.hasN) 1 else 0 }
                        .thenByDescending { it.score }
                )
                .take(2)
            if (audioCandidates.isEmpty()) continue

            val resolvedVideo = resolveReachableUrl(
                video.url,
                clientUserAgent(video.client),
                requireContinuation = true
            ) ?: continue
            for (audio in audioCandidates) {
                val resolvedAudio = resolveReachableUrl(
                    audio.url,
                    clientUserAgent(audio.client),
                    requireContinuation = true
                ) ?: continue
                Log.d(
                    TAG,
                    "Verified adaptive pair ${video.height}p v=${video.itag} " +
                        "a=${audio.itag} client=${video.client} hasN=${video.hasN}"
                )
                return resolvedVideo to resolvedAudio
            }
        }
        return null
    }

    /** Lower is more likely to play without a po_token. */
    private fun clientTrust(client: String): Int = when (client) {
        PREFERRED_SEPARATE_CLIENT -> 0
        "tvhtml5" -> 1
        "android" -> 2
        else -> 3
    }

    private fun clientUserAgent(clientKey: String): String {
        return CLIENTS.firstOrNull { it.key == clientKey }?.userAgent ?: IOS_USER_AGENT
    }

    private fun audioCompatibility(videoExt: String, audioExt: String): Int = when {
        videoExt == "mp4" && audioExt == "m4a" -> 0
        videoExt == "webm" && audioExt == "webm" -> 0
        else -> 1
    }

    /**
     * True when this device has a hardware decoder for the stream's video codec.
     *
     * Previews must start instantly, and a software fallback (or no decoder at all)
     * renders nothing while the audio track keeps playing. Unknown codecs are
     * allowed through so a mime string we fail to parse doesn't drop every stream —
     * the reachability probe and HLS fallback still cover us.
     */
    private fun isHardwareDecodable(mimeType: String): Boolean {
        val androidMime = when {
            mimeType.contains("av01", ignoreCase = true) -> "video/av01"
            mimeType.contains("vp9", ignoreCase = true) ||
                mimeType.contains("vp09", ignoreCase = true) -> "video/x-vnd.on2.vp9"
            mimeType.contains("avc1", ignoreCase = true) ||
                mimeType.contains("h264", ignoreCase = true) -> "video/avc"
            mimeType.contains("hvc1", ignoreCase = true) ||
                mimeType.contains("hev1", ignoreCase = true) -> "video/hevc"
            else -> return true
        }
        return hardwareVideoMimes.contains(androidMime)
    }

    private val hardwareVideoMimes: Set<String> by lazy {
        runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
                .codecInfos
                .asSequence()
                .filter { !it.isEncoder }
                .filter {
                    android.os.Build.VERSION.SDK_INT < 29 || it.isHardwareAccelerated
                }
                .flatMap { it.supportedTypes.asSequence() }
                .map { it.lowercase() }
                .toSet()
                .also { Log.d(TAG, "Hardware video decoders: ${it.filter { m -> m.startsWith("video/") }}") }
        }.getOrDefault(emptySet())
    }

    /**
     * Probes CDN nodes for the given googlevideo URL and returns the first reachable one.
     * Returns null if no CDN node responds successfully (all return 403/timeout).
     *
     * YouTube signs URLs to the InnerTube client that minted them. Probing with a
     * Chrome TV user-agent against an iOS URL is why official trailers listed
     * 720p and then failed the probe.
     */
    private suspend fun resolveReachableUrl(
        url: String,
        userAgent: String,
        requireContinuation: Boolean
    ): String? {
        if (!url.contains("googlevideo.com")) return url
        val candidates = cdnCandidates(url)

        // Always verify, even with a single candidate. Skipping the probe when the
        // URL advertised only one CDN node is how 403 video tracks reached the
        // player: the card got audio and a stuck 0x0 picture.
        if (candidates.size == 1) {
            return if (isUrlReachable(candidates[0], userAgent, requireContinuation)) {
                candidates[0]
            } else {
                null
            }
        }

        val result = CompletableDeferred<String>()
        val probeScope = CoroutineScope(Dispatchers.IO)
        candidates.forEach { candidate ->
            probeScope.launch {
                val reachable = isUrlReachable(candidate, userAgent, requireContinuation)
                if (reachable) result.complete(candidate)
            }
        }
        return try {
            withTimeoutOrNull(2_000L) { result.await() }
        } finally {
            probeScope.cancel()
        }
    }

    /** The original URL plus the alternate CDN hosts it advertises via `mn`. */
    private fun cdnCandidates(url: String): List<String> {
        val uri = Uri.parse(url)
        val host = uri.host ?: return listOf(url)
        val servers = uri.getQueryParameter("mn")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (servers.size < 2) return listOf(url)

        val candidates = mutableListOf(url)
        servers.forEachIndexed { index, server ->
            val altHost = host
                .replaceFirst(Regex("^rr\\d+---"), "rr${index + 1}---")
                .replaceFirst(Regex("sn-[a-z0-9]+-[a-z0-9]+"), server)
            if (altHost != host) {
                candidates += url.replace(host, altHost)
            }
        }
        return candidates.distinct()
    }

    private val probeClient by lazy {
        OkHttpClient.Builder()
            .dns(com.nuvio.tv.core.network.IPv4FirstDns())
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun resolveReachableManifestUrl(url: String, userAgent: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .headers(
                    buildHeaders(
                        mapOf(
                            "accept-language" to "en-US,en;q=0.9",
                            "user-agent" to userAgent,
                            "origin" to "https://www.youtube.com",
                            "referer" to "https://www.youtube.com/"
                        )
                    )
                )
                .build()
            probeClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) url else null
            }
        }.getOrDefault(null)
    }

    private fun isUrlReachable(
        url: String,
        userAgent: String,
        requireContinuation: Boolean
    ): Boolean {
        return runCatching {
            googlevideoProbeRanges(url, requireContinuation).all { (start, end) ->
                val request = Request.Builder()
                    .url(googlevideoRangeProbeUrl(url, start, end))
                    .get()
                    .headers(
                        buildHeaders(
                            mapOf(
                                "accept-language" to "en-US,en;q=0.9",
                                "user-agent" to userAgent,
                                "origin" to "https://www.youtube.com",
                                "referer" to "https://www.youtube.com/"
                            )
                        )
                    )
                    .build()
                probeClient.newCall(request).execute().use { response ->
                    val ok = response.code == 200 || response.code == 206
                    if (!ok) {
                        Log.d(
                            TAG,
                            "probe ${response.code} range=$start-$end ${summarizeUrl(url)}"
                        )
                    }
                    ok
                }
            }
        }.getOrDefault(false)
    }

    /**
     * Playback uses YouTube's `range=` query param, not an HTTP Range header.
     * A `Range: bytes=0-0` probe is what 403'd official 720p URLs on Shield.
     */
    private fun googlevideoRangeProbeUrl(url: String, start: Long, end: Long): String {
        if (!url.contains("googlevideo.com")) return url
        val uri = Uri.parse(url)
        val builder = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name.equals("range", ignoreCase = true)) continue
            for (value in uri.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value)
            }
        }
        builder.appendQueryParameter("range", "$start-$end")
        return builder.build().toString()
    }

    private fun absolutizeUrl(baseUrl: String, maybeRelative: String): String {
        return runCatching {
            URL(URL(baseUrl), maybeRelative).toString()
        }.getOrElse { maybeRelative }
    }

    private fun summarizeUrl(url: String): String {
        return runCatching {
            val parsed = URL(url)
            val host = parsed.host ?: "unknown-host"
            val path = parsed.path ?: "/"
            "$host$path"
        }.getOrDefault(url.take(80))
    }

    private fun performRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null
    ): RequestResponse {
        val requestBuilder = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers))

        when (method.uppercase()) {
            "POST" -> requestBuilder.post((body ?: "").toRequestBody())
            "PUT" -> requestBuilder.put((body ?: "").toRequestBody())
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            return RequestResponse(
                ok = response.isSuccessful,
                status = response.code,
                statusText = response.message,
                url = response.request.url.toString(),
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun buildHeaders(source: Map<String, String>): Headers {
        val headers = Headers.Builder()
        source.forEach { (name, value) ->
            if (!name.equals("Accept-Encoding", ignoreCase = true)) {
                headers.add(name, value)
            }
        }
        if (source.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            headers.add("User-Agent", DEFAULT_USER_AGENT)
        }
        return headers.build()
    }
}

private data class RequestResponse(
    val ok: Boolean,
    val status: Int,
    val statusText: String,
    val url: String,
    val body: String
)

internal fun googlevideoProbeRanges(url: String, requireContinuation: Boolean): List<Pair<Long, Long>> {
    if (!url.contains("googlevideo.com")) return listOf(0L to GOOGLEVIDEO_PROBE_BYTES - 1)

    val contentLength = CLEN_QUERY_REGEX.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
    val headRange = 0L to GOOGLEVIDEO_PROBE_BYTES - 1
    if (!requireContinuation) return listOf(headRange)
    if (contentLength != null && contentLength <= GOOGLEVIDEO_CONTINUATION_PROBE_START) {
        return listOf(headRange)
    }

    val continuationStart = GOOGLEVIDEO_CONTINUATION_PROBE_START
    val continuationEnd = if (contentLength != null) {
        minOf(contentLength - 1, continuationStart + GOOGLEVIDEO_PROBE_BYTES - 1)
    } else {
        continuationStart + GOOGLEVIDEO_PROBE_BYTES - 1
    }
    return listOf(headRange, continuationStart to continuationEnd)
}

private fun Map<*, *>.mapValue(key: String): Map<*, *>? {
    return this[key] as? Map<*, *>
}

private fun Map<*, *>.listMapValue(key: String): List<Map<*, *>> {
    val raw = this[key] as? List<*> ?: return emptyList()
    return raw.mapNotNull { it as? Map<*, *> }
}

private fun Map<*, *>.stringValue(key: String): String? {
    val value = this[key] ?: return null
    return value.toString()
}

private fun Map<*, *>.numberValue(key: String): Double? {
    val value = this[key] ?: return null
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
