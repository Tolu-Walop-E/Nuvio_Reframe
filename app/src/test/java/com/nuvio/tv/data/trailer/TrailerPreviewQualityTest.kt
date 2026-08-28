package com.nuvio.tv.data.trailer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPreviewQualityTest {
    @Test
    fun `720p beats 360p and 1080p`() {
        assertTrue(TrailerPreviewQuality.isBetterHeight(720, 360))
        assertTrue(TrailerPreviewQuality.isBetterHeight(720, 1080))
        assertTrue(TrailerPreviewQuality.isBetterHeight(720, 2160))
        assertFalse(TrailerPreviewQuality.isBetterHeight(360, 720))
        assertFalse(TrailerPreviewQuality.isBetterHeight(1080, 720))
    }

    @Test
    fun `when 720p is missing pick 480p over 360p and over 1080p`() {
        assertTrue(TrailerPreviewQuality.isBetterHeight(480, 360))
        assertTrue(TrailerPreviewQuality.isBetterHeight(480, 1080))
        assertFalse(TrailerPreviewQuality.isBetterHeight(1080, 480))
    }

    @Test
    fun `score ranks 720 then sub-720 then 1080`() {
        val p720 = TrailerPreviewQuality.heightScore(720, 30, 1.0)
        val p480 = TrailerPreviewQuality.heightScore(480, 30, 9_000_000.0)
        val p1080 = TrailerPreviewQuality.heightScore(1080, 60, 9_000_000.0)
        assertTrue(p720 > p480)
        assertTrue(p480 > p1080)
    }
}
