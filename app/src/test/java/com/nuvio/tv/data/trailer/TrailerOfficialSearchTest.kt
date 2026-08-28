package com.nuvio.tv.data.trailer

import com.nuvio.tv.data.remote.api.TmdbVideoResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerOfficialSearchTest {

    @Test
    fun `searchQueries include year and official trailer`() {
        val queries = TrailerOfficialSearch.searchQueries("Dune", "2021")
        assertEquals(
            listOf("Dune 2021 official trailer", "Dune official trailer", "Dune 2021 trailer"),
            queries
        )
    }

    @Test
    fun `ranks official theatrical trailer above teaser and clip junk`() {
        val teaser = tmdb(
            key = "teaserKey12",
            name = "Teaser",
            type = "Teaser",
            official = true,
            size = 1080
        )
        val bts = tmdb(
            key = "btsKey12345",
            name = "Behind the Scenes Featurette",
            type = "Trailer",
            official = true,
            size = 1080
        )
        val official = tmdb(
            key = "official123",
            name = "Dune Official Trailer",
            type = "Trailer",
            official = true,
            size = 1080
        )
        val ranked = TrailerOfficialSearch.rankTmdb(
            listOf(teaser, bts, official),
            title = "Dune",
            year = "2021"
        )
        assertEquals(listOf("official123", "teaserKey12"), ranked.map { it.key })
    }

    @Test
    fun `youtube search prefers official trailer over recap and reaction`() {
        val hits = listOf(
            YouTubeTrailerSearchHit(
                videoId = "reaction123",
                title = "Dune Ending Explained",
                channel = "Movie Recaps",
                durationSeconds = 720
            ),
            YouTubeTrailerSearchHit(
                videoId = "recap123456",
                title = "Dune 2021 Recap",
                channel = "WatchMojo",
                durationSeconds = 480
            ),
            YouTubeTrailerSearchHit(
                videoId = "official123",
                title = "Dune | Official Trailer",
                channel = "Warner Bros. Pictures",
                durationSeconds = 150
            ),
            YouTubeTrailerSearchHit(
                videoId = "fanmade1234",
                title = "Dune Official Trailer Fan Made",
                channel = "Some Fan",
                durationSeconds = 140
            )
        )
        val ranked = TrailerOfficialSearch.rankYouTubeHits(hits, "Dune", "2021")
        assertEquals(listOf("official123"), ranked.map { it.videoId })
    }

    @Test
    fun `rejects short bumpers and full movies`() {
        assertFalse(TrailerOfficialSearch.durationOk(8))
        assertFalse(TrailerOfficialSearch.durationOk(20 * 60))
        assertTrue(TrailerOfficialSearch.durationOk(150))
        assertEquals(150, TrailerOfficialSearch.parseDurationSeconds("2:30"))
    }

    @Test
    fun `junk titles are filtered`() {
        assertTrue(TrailerOfficialSearch.isJunkTitle("Dune Behind the Scenes"))
        assertTrue(TrailerOfficialSearch.isJunkTitle("Dune Reaction"))
        assertFalse(TrailerOfficialSearch.isJunkTitle("Dune Official Trailer"))
    }

    private fun tmdb(
        key: String,
        name: String,
        type: String,
        official: Boolean,
        size: Int
    ) = TmdbVideoResult(
        name = name,
        key = key,
        site = "YouTube",
        size = size,
        type = type,
        official = official
    )
}
