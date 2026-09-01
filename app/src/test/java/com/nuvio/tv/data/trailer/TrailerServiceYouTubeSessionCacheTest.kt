package com.nuvio.tv.data.trailer

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TrailerApi
import com.nuvio.tv.data.remote.api.TrailerResponse
import com.nuvio.tv.domain.model.TmdbSettings
import io.mockk.*
import java.time.Clock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class TrailerServiceYouTubeSessionCacheTest {

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
    fun `reuses successful playback source for same youtube key within session`() = runTest {
        val trailerApi = mockk<TrailerApi>()
        val tmdbApi = mockk<TmdbApi>()
        val extractor = mockk<InAppYouTubeExtractor>()
        val tmdbSettingsDataStore = mockk<TmdbSettingsDataStore>()
        val tmdbService = mockk<TmdbService>()
        every { tmdbSettingsDataStore.settings } returns flowOf(TmdbSettings(language = "en"))
        every { tmdbService.apiKey() } returns "tmdb-key"
        val service = TrailerService(
            trailerApi = trailerApi,
            tmdbApi = tmdbApi,
            inAppYouTubeExtractor = extractor,
            tmdbSettingsDataStore = tmdbSettingsDataStore,
            tmdbService = tmdbService,
            clock = Clock.systemUTC(),
            remoteTrailerResolverEnabled = false
        )

        val cached = TrailerPlaybackSource(
            videoUrl = "https://cdn.example/video.mp4",
            audioUrl = "https://cdn.example/audio.m4a"
        )
        coEvery { extractor.extractPlaybackSource(any()) } returnsMany listOf(
            cached,
            TrailerPlaybackSource(videoUrl = "https://cdn.example/should-not-be-used.mp4")
        )

        val first = service.getTrailerPlaybackSourceFromYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val second = service.getTrailerPlaybackSourceFromYouTubeUrl("https://youtu.be/dQw4w9WgXcQ")

        assertEquals("https://cdn.example/video.mp4", first?.videoUrl)
        assertEquals("https://cdn.example/video.mp4", second?.videoUrl)
        assertEquals("https://cdn.example/audio.m4a", second?.audioUrl)
        coVerify(exactly = 1) { extractor.extractPlaybackSource(any()) }
        coVerify(exactly = 0) { trailerApi.getTrailer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `failed youtube extraction is not cached for same key`() = runTest {
        val trailerApi = mockk<TrailerApi>()
        val tmdbApi = mockk<TmdbApi>()
        val extractor = mockk<InAppYouTubeExtractor>()
        val tmdbSettingsDataStore = mockk<TmdbSettingsDataStore>()
        val tmdbService = mockk<TmdbService>()
        every { tmdbSettingsDataStore.settings } returns flowOf(TmdbSettings(language = "en"))
        every { tmdbService.apiKey() } returns "tmdb-key"
        val service = TrailerService(
            trailerApi = trailerApi,
            tmdbApi = tmdbApi,
            inAppYouTubeExtractor = extractor,
            tmdbSettingsDataStore = tmdbSettingsDataStore,
            tmdbService = tmdbService,
            clock = Clock.systemUTC(),
            remoteTrailerResolverEnabled = false
        )

        coEvery { extractor.extractPlaybackSource("https://www.youtube.com/watch?v=dQw4w9WgXcQ") } returnsMany listOf(
            null,
            TrailerPlaybackSource(videoUrl = "https://cdn.example/video-after-retry.mp4")
        )

        val first = service.getTrailerPlaybackSourceFromYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val second = service.getTrailerPlaybackSourceFromYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertNull(first)
        assertEquals("https://cdn.example/video-after-retry.mp4", second?.videoUrl)
        coVerify(exactly = 2) { extractor.extractPlaybackSource("https://www.youtube.com/watch?v=dQw4w9WgXcQ") }
        coVerify(exactly = 0) { trailerApi.getTrailer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `uses remote resolver source before local extractor when enabled`() = runTest {
        val trailerApi = mockk<TrailerApi>()
        val tmdbApi = mockk<TmdbApi>()
        val extractor = mockk<InAppYouTubeExtractor>()
        val tmdbSettingsDataStore = mockk<TmdbSettingsDataStore>()
        val tmdbService = mockk<TmdbService>()
        every { tmdbSettingsDataStore.settings } returns flowOf(TmdbSettings(language = "en"))
        every { tmdbService.apiKey() } returns "tmdb-key"
        val service = TrailerService(
            trailerApi = trailerApi,
            tmdbApi = tmdbApi,
            inAppYouTubeExtractor = extractor,
            tmdbSettingsDataStore = tmdbSettingsDataStore,
            tmdbService = tmdbService,
            clock = Clock.systemUTC(),
            remoteTrailerResolverEnabled = true
        )

        coEvery {
            trailerApi.getTrailer(any(), any(), any(), any(), any())
        } returns Response.success(
            TrailerResponse(
                videoUrl = "https://resolver.example/video-720.mp4",
                audioUrl = "https://resolver.example/audio.m4a",
                verified = true,
                height = 720,
                format = "adaptive"
            )
        )

        val source = service.getTrailerPlaybackSourceFromYouTubeUrl(
            youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            title = "Example",
            year = "2026"
        )

        assertEquals("https://resolver.example/video-720.mp4", source?.videoUrl)
        assertEquals("https://resolver.example/audio.m4a", source?.audioUrl)
        coVerify(exactly = 1) {
            trailerApi.getTrailer(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "Example",
                "2026",
                720,
                "muxed,hls,verified_adaptive"
            )
        }
        coVerify(exactly = 0) { extractor.extractPlaybackSource(any()) }
    }

    @Test
    fun `rejects unverified remote adaptive audio and falls back to local extractor`() = runTest {
        val trailerApi = mockk<TrailerApi>()
        val tmdbApi = mockk<TmdbApi>()
        val extractor = mockk<InAppYouTubeExtractor>()
        val tmdbSettingsDataStore = mockk<TmdbSettingsDataStore>()
        val tmdbService = mockk<TmdbService>()
        every { tmdbSettingsDataStore.settings } returns flowOf(TmdbSettings(language = "en"))
        every { tmdbService.apiKey() } returns "tmdb-key"
        val service = TrailerService(
            trailerApi = trailerApi,
            tmdbApi = tmdbApi,
            inAppYouTubeExtractor = extractor,
            tmdbSettingsDataStore = tmdbSettingsDataStore,
            tmdbService = tmdbService,
            clock = Clock.systemUTC(),
            remoteTrailerResolverEnabled = true
        )

        coEvery {
            trailerApi.getTrailer(any(), any(), any(), any(), any())
        } returns Response.success(
            TrailerResponse(
                url = "https://resolver.example/video-720.mp4",
                audioUrl = "https://resolver.example/audio.m4a",
                verified = false
            )
        )
        coEvery {
            extractor.extractPlaybackSource("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        } returns TrailerPlaybackSource(videoUrl = "https://local.example/muxed-720.mp4")

        val source = service.getTrailerPlaybackSourceFromYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertEquals("https://local.example/muxed-720.mp4", source?.videoUrl)
        assertNull(source?.audioUrl)
        coVerify(exactly = 1) { trailerApi.getTrailer(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            extractor.extractPlaybackSource("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        }
    }
}
