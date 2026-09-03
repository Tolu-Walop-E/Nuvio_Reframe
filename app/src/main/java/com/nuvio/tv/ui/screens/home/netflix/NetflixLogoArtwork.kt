package com.nuvio.tv.ui.screens.home.netflix

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.size.Precision

/**
 * Clearart / title logos must not decode from the card's live layout size.
 *
 * The Netflix rail expands portrait → landscape on focus. Coil is configured
 * globally with [Precision.INEXACT], so a logo that lands mid-animation (typical
 * for the 2nd+ cards once the CDN/disk is warm) gets cached at the small interim
 * size and then upscaled soft for the rest of the session. The first cold focus
 * often finishes the animation before the bytes arrive, which is why only the
 * first clearart looked sharp.
 *
 * Always decode at a fixed high-res size with [Precision.EXACT].
 */
internal object NetflixLogoArtwork {
    const val DecodeWidthPx = 960
    const val DecodeHeightPx = 360

    private val TmdbSizedPath = Regex(
        """(https?://image\.tmdb\.org/t/p/)w\d+(/.*)""",
        RegexOption.IGNORE_CASE
    )

    fun upgradeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        return TmdbSizedPath.replace(trimmed) { match ->
            "${match.groupValues[1]}original${match.groupValues[2]}"
        }
            .replace("/logo/medium/", "/logo/large/")
            .replace("/logo/small/", "/logo/large/")
    }

    fun request(context: Context, logoUrl: String): ImageRequest {
        val url = upgradeUrl(logoUrl)
        return ImageRequest.Builder(context)
            .data(url)
            .size(DecodeWidthPx, DecodeHeightPx)
            .precision(Precision.EXACT)
            // Transparent clearart looks muddy under RGB_565.
            .allowRgb565(false)
            .memoryCacheKey("netflix-logo:$url")
            .diskCacheKey(url)
            .build()
    }
}
