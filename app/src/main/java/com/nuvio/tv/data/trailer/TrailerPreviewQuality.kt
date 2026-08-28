package com.nuvio.tv.data.trailer

/**
 * Card trailers stay at 720p.
 *
 * Shield (and similar boxes) will spin up a 4K HDMI mode when ExoPlayer hands it a
 * 1080p+ YouTube stream, then fail the surface handoff: audio keeps going over a
 * frozen poster. 720p is the highest ladder that stays in 1080p output mode.
 *
 * Within that cap, never pick 360/480 when a 720p rendition exists.
 */
internal object TrailerPreviewQuality {
    const val MAX_HEIGHT = 720
    const val MIN_HEIGHT = 720

    fun isPreferred(height: Int): Boolean = height in MIN_HEIGHT..MAX_HEIGHT

    fun isBelowFloor(height: Int): Boolean = height in 1 until MIN_HEIGHT

    fun isAboveCap(height: Int): Boolean = height > MAX_HEIGHT

    fun heightScore(height: Int, fps: Int, bitrate: Double): Double {
        val heightScore = when {
            height <= 0 -> 0.0
            isPreferred(height) -> height * 1_000_000_000.0
            isBelowFloor(height) -> height * 1_000_000.0
            else -> 1_000.0 - (height - MAX_HEIGHT).toDouble()
        }
        return heightScore + fps * 1_000_000.0 + bitrate
    }

    fun isBetterHeight(candidate: Int, best: Int): Boolean {
        val c = candidate.coerceAtLeast(0)
        val b = best.coerceAtLeast(0)
        if (c == 0) return false
        if (b == 0) return true
        val cRank = rank(c)
        val bRank = rank(b)
        if (cRank != bRank) return cRank < bRank
        return when (cRank) {
            1 -> c > b
            2 -> c < b
            else -> c > b
        }
    }

    /** 0 = 720p, 1 = below 720p, 2 = above 720p. */
    private fun rank(height: Int): Int = when {
        isPreferred(height) -> 0
        isBelowFloor(height) -> 1
        else -> 2
    }
}
