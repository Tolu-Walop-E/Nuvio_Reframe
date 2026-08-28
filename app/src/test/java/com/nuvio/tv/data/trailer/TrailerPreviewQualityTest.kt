package com.nuvio.tv.data.trailer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPreviewQualityTest {
    @Test
    fun `720p policy is a floor that still allows 1080 and rejects 360 and 4K`() {
        val policy = TrailerPreviewQualityPolicy.P720
        assertTrue(TrailerPreviewQuality.isPreferred(720, policy))
        assertTrue(TrailerPreviewQuality.isPreferred(1080, policy))
        assertTrue(TrailerPreviewQuality.isBelowFloor(360, policy))
        assertTrue(TrailerPreviewQuality.isAboveCap(2160, policy))
        assertFalse(TrailerPreviewQuality.isAboveCap(1080, policy))
        assertTrue(TrailerPreviewQuality.isBetterHeight(720, 360, policy))
        assertTrue(TrailerPreviewQuality.isBetterHeight(720, 1080, policy))
        assertTrue(TrailerPreviewQuality.isBetterHeight(1080, 360, policy))
        assertFalse(TrailerPreviewQuality.isBetterHeight(360, 720, policy))
    }

    @Test
    fun `1080p policy prefers 1080 and rejects 720 and 4K`() {
        val policy = TrailerPreviewQualityPolicy.P1080
        assertTrue(TrailerPreviewQuality.isPreferred(1080, policy))
        assertTrue(TrailerPreviewQuality.isBelowFloor(720, policy))
        assertTrue(TrailerPreviewQuality.isAboveCap(2160, policy))
        assertTrue(TrailerPreviewQuality.isBetterHeight(1080, 720, policy))
        assertFalse(TrailerPreviewQuality.isBetterHeight(720, 1080, policy))
        val p1080 = TrailerPreviewQuality.heightScore(1080, 30, 1.0, policy)
        val p720 = TrailerPreviewQuality.heightScore(720, 60, 9_000_000.0, policy)
        assertTrue(p1080 > p720)
    }
}
