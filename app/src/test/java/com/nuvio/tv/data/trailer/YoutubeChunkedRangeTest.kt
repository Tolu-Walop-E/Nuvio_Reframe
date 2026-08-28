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
    fun `short audio starts with proven 512KB cadence`() {
        assertEquals(
            listOf(512L * 1024),
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
}
