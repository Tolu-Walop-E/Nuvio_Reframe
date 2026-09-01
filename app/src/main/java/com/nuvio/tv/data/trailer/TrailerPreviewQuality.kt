package com.nuvio.tv.data.trailer

import com.nuvio.tv.domain.model.TrailerMinResolution

/**
 * Trailer quality ladder for in-app YouTube playback.
 *
 * [minHeight] is the preferred floor. If YouTube blocks every preferred
 * 720p+ stream, the extractor may fall back to a verified muxed rendition
 * below this floor so card trailers still play without adaptive-audio freezes.
 * [maxHeight] is a hard cap so Shield does not switch HDMI into a 4K decode
 * path (1080p+ used to leave audio running over a frozen poster).
 */
internal data class TrailerPreviewQualityPolicy(
    val minHeight: Int,
    val maxHeight: Int
) {
    companion object {
        /** Floor 720p, cap 1080p. Prefer 720 when both exist (safer on Shield). */
        val P720 = TrailerPreviewQualityPolicy(minHeight = 720, maxHeight = 1080)
        val P1080 = TrailerPreviewQualityPolicy(minHeight = 1080, maxHeight = 1080)

        fun from(resolution: TrailerMinResolution): TrailerPreviewQualityPolicy = when (resolution) {
            TrailerMinResolution.P720 -> P720
            TrailerMinResolution.P1080 -> P1080
        }
    }
}

internal object TrailerPreviewQuality {
    fun isPreferred(height: Int, policy: TrailerPreviewQualityPolicy): Boolean {
        return height in policy.minHeight..policy.maxHeight
    }

    fun isBelowFloor(height: Int, policy: TrailerPreviewQualityPolicy): Boolean {
        return height in 1 until policy.minHeight
    }

    fun isAboveCap(height: Int, policy: TrailerPreviewQualityPolicy): Boolean {
        return height > policy.maxHeight
    }

    fun heightScore(height: Int, fps: Int, bitrate: Double, policy: TrailerPreviewQualityPolicy): Double {
        val heightScore = when {
            height <= 0 -> 0.0
            isPreferred(height, policy) -> {
                val closeness = (policy.maxHeight - kotlin.math.abs(height - policy.minHeight))
                    .coerceAtLeast(0)
                1_000_000_000_000.0 + closeness * 1_000_000.0 + height
            }
            isBelowFloor(height, policy) -> height * 1_000_000.0
            else -> 1_000.0 - (height - policy.maxHeight).toDouble()
        }
        return heightScore + fps * 1_000_000.0 + bitrate
    }

    fun isBetterHeight(candidate: Int, best: Int, policy: TrailerPreviewQualityPolicy): Boolean {
        val c = candidate.coerceAtLeast(0)
        val b = best.coerceAtLeast(0)
        if (c == 0) return false
        if (b == 0) return true
        val cRank = rank(c, policy)
        val bRank = rank(b, policy)
        if (cRank != bRank) return cRank < bRank
        return when (cRank) {
            0 -> {
                val cDist = kotlin.math.abs(c - policy.minHeight)
                val bDist = kotlin.math.abs(b - policy.minHeight)
                if (cDist != bDist) cDist < bDist else c > b
            }
            1 -> c > b
            else -> c < b
        }
    }

    /** 0 = in range, 1 = below floor, 2 = above cap. */
    private fun rank(height: Int, policy: TrailerPreviewQualityPolicy): Int = when {
        isPreferred(height, policy) -> 0
        isBelowFloor(height, policy) -> 1
        else -> 2
    }
}
