package com.nuvio.tv.data.trailer

import com.nuvio.tv.data.remote.api.TmdbVideoResult

/**
 * Picks the actual official trailer from TMDB /videos or YouTube search hits.
 *
 * TMDB lists teasers, clips, and featurettes next to the theatrical trailer.
 * A YouTube search for the title also returns recaps, reactions, and uploads
 * of the same trailer. This ranks those candidates so playback tries the
 * theatrical trailer first.
 */
internal object TrailerOfficialSearch {
    private val STOP_WORDS = setOf(
        "the", "a", "an", "of", "and", "to", "in", "on", "for", "la", "el", "le", "les", "des"
    )
    private val JUNK_TITLE = Regex(
        """\b(""" +
            """behind[\s-]?the[\s-]?scenes|featurette|blooper|deleted\s+scene|interview|""" +
            """recap|reaction|review|ending\s+explained|soundtrack|gameplay|""" +
            """fan[\s-]?made|fanmade|parody|mashup|leaked|cam\s*rip|full\s+movie|""" +
            """watch\s+online|movie\s+clips?|clip\s+compilation|vfx\s+breakdown""" +
            """)\b""",
        RegexOption.IGNORE_CASE
    )
    private val OFFICIAL_TRAILER = Regex(
        """\bofficial\s+(teaser\s+)?trailer\b""",
        RegexOption.IGNORE_CASE
    )
    private val TRAILER_WORD = Regex("""\btrailers?\b""", RegexOption.IGNORE_CASE)
    private val TEASER_WORD = Regex("""\bteasers?\b""", RegexOption.IGNORE_CASE)
    private val YEAR_IN_TEXT = Regex("""\b(19|20)\d{2}\b""")

    const val MAX_SEARCH_EXTRACTS = 3
    const val MIN_TRAILER_SECONDS = 20
    const val MAX_TRAILER_SECONDS = 8 * 60

    fun searchQueries(title: String, year: String?): List<String> {
        val clean = title.trim()
        if (clean.isEmpty()) return emptyList()
        val yearToken = year?.trim()?.takeIf { it.matches(Regex("""(19|20)\d{2}""")) }
        return listOfNotNull(
            yearToken?.let { "$clean $it official trailer" },
            "$clean official trailer",
            yearToken?.let { "$clean $it trailer" }
        ).distinct()
    }

    fun rankTmdb(
        results: List<TmdbVideoResult>,
        title: String? = null,
        year: String? = null
    ): List<TmdbVideoResult> {
        return results
            .asSequence()
            .filter { (it.site ?: "").equals("YouTube", ignoreCase = true) }
            .filter { !it.key.isNullOrBlank() }
            .filter {
                val normalizedType = it.type?.trim()?.lowercase()
                normalizedType == "trailer" || normalizedType == "teaser"
            }
            .filter { !isJunkTitle(it.name.orEmpty()) }
            .sortedWith(
                compareByDescending<TmdbVideoResult> { tmdbScore(it, title, year) }
                    .thenByDescending { it.size ?: 0 }
                    .thenByDescending { it.publishedAt.orEmpty() }
            )
            .toList()
    }

    fun rankYouTubeHits(
        hits: List<YouTubeTrailerSearchHit>,
        title: String,
        year: String?
    ): List<YouTubeTrailerSearchHit> {
        return hits
            .asSequence()
            .filter { it.videoId.isNotBlank() }
            .distinctBy { it.videoId }
            .filter { looksLikeTrailer(it.title) }
            .filter { !isJunkTitle(it.title) }
            .filter { durationOk(it.durationSeconds) }
            .sortedByDescending { youtubeScore(it, title, year) }
            .toList()
    }

    internal fun tmdbScore(video: TmdbVideoResult, title: String?, year: String?): Int {
        val name = video.name.orEmpty()
        var score = 0
        score += when (video.type?.trim()?.lowercase()) {
            "trailer" -> 1_000
            "teaser" -> 200
            else -> 0
        }
        if (video.official == true) score += 500
        if (OFFICIAL_TRAILER.containsMatchIn(name)) score += 400
        else if (TRAILER_WORD.containsMatchIn(name)) score += 180
        else if (TEASER_WORD.containsMatchIn(name)) score += 40
        score += sizeBonus(video.size)
        score += titleOverlapBonus(name, title)
        score += yearBonus(name, year)
        if (video.iso6391.equals("en", ignoreCase = true)) score += 15
        return score
    }

    internal fun youtubeScore(hit: YouTubeTrailerSearchHit, title: String, year: String?): Int {
        var score = 0
        if (OFFICIAL_TRAILER.containsMatchIn(hit.title)) score += 400
        else if (TRAILER_WORD.containsMatchIn(hit.title)) score += 180
        else if (TEASER_WORD.containsMatchIn(hit.title)) score += 40
        if (hit.channel.contains("official", ignoreCase = true)) score += 80
        if (channelLooksStudio(hit.channel)) score += 60
        score += titleOverlapBonus(hit.title, title)
        score += yearBonus(hit.title, year)
        score += durationBonus(hit.durationSeconds)
        return score
    }

    internal fun isJunkTitle(name: String): Boolean = JUNK_TITLE.containsMatchIn(name)

    internal fun looksLikeTrailer(name: String): Boolean {
        return TRAILER_WORD.containsMatchIn(name) || TEASER_WORD.containsMatchIn(name)
    }

    internal fun durationOk(seconds: Int?): Boolean {
        if (seconds == null) return true
        return seconds in MIN_TRAILER_SECONDS..MAX_TRAILER_SECONDS
    }

    internal fun parseDurationSeconds(text: String?): Int? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val parts = raw.split(':')
        if (parts.size !in 2..3) return null
        val values = parts.map { it.toIntOrNull() ?: return null }
        return when (values.size) {
            2 -> values[0] * 60 + values[1]
            else -> values[0] * 3600 + values[1] * 60 + values[2]
        }
    }

    private fun sizeBonus(size: Int?): Int = when {
        size == null -> 0
        size >= 1080 -> 100
        size >= 720 -> 50
        else -> 0
    }

    private fun titleOverlapBonus(candidate: String, title: String?): Int {
        val tokens = titleTokens(title)
        if (tokens.isEmpty()) return 0
        val haystack = candidate.lowercase()
        val hits = tokens.count { haystack.contains(it) }
        return hits * 25
    }

    private fun yearBonus(text: String, year: String?): Int {
        val expected = year?.trim()?.takeIf { it.matches(Regex("""(19|20)\d{2}""")) } ?: return 0
        val found = YEAR_IN_TEXT.findAll(text).map { it.value }.toSet()
        return when {
            expected in found -> 80
            found.isNotEmpty() -> -40
            else -> 0
        }
    }

    private fun durationBonus(seconds: Int?): Int {
        if (seconds == null) return 10
        return when (seconds) {
            in 75..210 -> 50
            in 45..74 -> 25
            in 211..300 -> 20
            else -> 0
        }
    }

    private fun channelLooksStudio(channel: String): Boolean {
        val lower = channel.lowercase()
        return listOf(
            "pictures", "studios", "entertainment", "movies", "films", "warner",
            "disney", "universal", "sony", "paramount", "lionsgate", "netflix",
            "hbo", "marvel", "lucasfilm"
        ).any { it in lower }
    }

    private fun titleTokens(title: String?): List<String> {
        if (title.isNullOrBlank()) return emptyList()
        return title.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 && it !in STOP_WORDS }
    }
}

internal data class YouTubeTrailerSearchHit(
    val videoId: String,
    val title: String,
    val channel: String = "",
    val durationSeconds: Int? = null
)
