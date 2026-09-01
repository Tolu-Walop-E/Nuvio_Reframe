package com.nuvio.tv.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubeChunkedRangeTest {
    @Test
    fun `audio leftover shorter than 2MB is requested in one range`() {
        val remaining = 1_200_000L
        assertEquals(listOf(1_200_000L, 1_048_576L, 524_288L), youtubeChunkSizes(remaining, 0L))
    }

    @Test
    fun `short audio starts with a 1MB trailer-sized range`() {
        assertEquals(
            listOf(1024L * 1024),
            youtubeChunkSizes(1_956_089L, 0L, shortResource = true)
        )
    }

    @Test
    fun `video remaining uses 2MB then smaller fallbacks`() {
        val remaining = 40L * 1024 * 1024
        assertEquals(
            listOf(2L * 1024 * 1024, 1024L * 1024, 512L * 1024),
            youtubeChunkSizes(remaining, 0L)
        )
    }

    @Test
    fun `no bytes remaining means no ranges`() {
        assertEquals(emptyList<Long>(), youtubeChunkSizes(0L, 2L * 1024 * 1024))
    }

    @Test
    fun `googlevideo direct playback probe matches short track chunk requests`() {
        val ranges = googlevideoProbeRanges(
            "https://rr1---sn-a5mlrn6k.googlevideo.com/videoplayback?clen=1956089&itag=140",
            requireContinuation = true
        )

        assertEquals(
            listOf(0L to 1_048_575L, 1_048_576L to 1_956_088L),
            ranges
        )
    }

    @Test
    fun `googlevideo short stream probes only its playback range`() {
        val ranges = googlevideoProbeRanges(
            "https://rr1---sn-a5mlrn6k.googlevideo.com/videoplayback?clen=524288&itag=18",
            requireContinuation = true
        )

        assertEquals(listOf(0L to 524_287L), ranges)
    }

    @Test
    fun `complete adaptive audio probe covers every playback chunk`() {
        val ranges = googlevideoProbeRanges(
            "https://rr1---sn-a5mlrn6k.googlevideo.com/videoplayback?clen=3145729&itag=140",
            requireContinuation = true,
            requireComplete = true
        )

        assertEquals(
            listOf(
                0L to 1_048_575L,
                1_048_576L to 2_097_151L,
                2_097_152L to 3_145_727L,
                3_145_728L to 3_145_728L
            ),
            ranges
        )
    }

    @Test
    fun `complete adaptive audio probe rejects unknown content length`() {
        val ranges = googlevideoProbeRanges(
            "https://rr1---sn-a5mlrn6k.googlevideo.com/videoplayback?itag=140",
            requireContinuation = true,
            requireComplete = true
        )

        assertEquals(emptyList<Pair<Long, Long>>(), ranges)
    }

}
