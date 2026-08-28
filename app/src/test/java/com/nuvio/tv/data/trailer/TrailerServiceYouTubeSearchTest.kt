package com.nuvio.tv.data.trailer

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TrailerApi
import com.nuvio.tv.domain.model.TmdbSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrailerServiceYouTubeSearchTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `searches youtube for official trailer when tmdb id is missing`() = runTest {
        val trailerApi = mockk<TrailerApi>()
        val tmdbApi = mockk<TmdbApi>()
        val extractor = mockk<InAppYouTubeExtractor>()
        val tmdbSettingsDataStore = mockk<TmdbSettingsDataStore>()
        val tmdbService = mockk<TmdbService>()
        every { tmdbSettingsDataStore.settings } returns flowOf(
            TmdbSettings(language = "en", useTrailers = true)
        )
        every { tmdbService.apiKey() } returns "tmdb-key"
        coEvery { extractor.searchTrailerVideos(any()) } returns listOf(
            YouTubeTrailerSearchHit(
                videoId = "reaction123",
                title = "Dune Ending Explained",
                channel = "Movie Recaps",
                durationSeconds = 720
            ),
            YouTubeTrailerSearchHit(
                videoId = "official123",
                title = "Dune | Official Trailer",
                channel = "Warner Bros. Pictures",
                durationSeconds = 150
            )
        )
        coEvery {
            extractor.extractPlaybackSource("https://www.youtube.com/watch?v=official123")
        } returns TrailerPlaybackSource(videoUrl = "https://cdn.example/dune.mp4")

        val service = TrailerService(trailerApi, tmdbApi, extractor, tmdbSettingsDataStore, tmdbService)
        val result = service.getTrailerPlaybackSource(
            title = "Dune",
            year = "2021",
            tmdbId = null,
            type = "movie"
        )

        assertEquals("https://cdn.example/dune.mp4", result?.videoUrl)
        coVerify(exactly = 1) {
            extractor.extractPlaybackSource("https://www.youtube.com/watch?v=official123")
        }
        coVerify(exactly = 0) {
            extractor.extractPlaybackSource("https://www.youtube.com/watch?v=reaction123")
        }
        coVerify(exactly = 0) { trailerApi.getTrailer(any(), any(), any()) }
    }
}
