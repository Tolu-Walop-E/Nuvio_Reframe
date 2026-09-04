package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem

internal object NetflixHomeTokens {
    val Background = Color(0xFF050505)
    val Surface = Color(0xFF121212)
    val SurfaceRaised = Color(0xFF181818)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.70f)
    val TextMuted = Color.White.copy(alpha = 0.50f)
    val Accent = Color(0xFFE50914)
    val Focus = Color.White
    /** Match score. Muted so it reads as a fact, not a second accent. */
    val RatingGreen = Color(0xFF3FA45F)
    val Scrim = Color.Black.copy(alpha = 0.68f)

    val PageHorizontalPadding = 40.dp
    /** Absolute / measured top nav row height. */
    val TopNavHeight = 48.dp
    /**
     * Leanback safe-area inset. Kept small: the earlier clipping was vertical
     * overflow (nav + gap + fixed hero exceeded the screen), not real overscan.
     */
    val TvOverscanTop = 12.dp
    val TvOverscanBottom = 8.dp
    /**
     * Netflix keeps the nav on its own dark strip with clear air under it — the
     * rails start below and never scroll behind it. The nav therefore stays in
     * normal layout flow and this gap is real, reserved space.
     */
    val NavContentGap = 8.dp
    /**
     * Fade at the scroll container's top edge so rows scrolled past dissolve
     * instead of cutting mid-line. A focused rail reserves this much headroom in
     * its bring-into-view rect, otherwise the fade eats its own title.
     */
    val ScrollTopFade = 40.dp
    /** Fallback for callers without screen constraints; prefer [heroHeightFor]. */
    val HeroHeight = 320.dp

    /**
     * Netflix's billboard is full-bleed and owns roughly the top three quarters of
     * the viewport, with the next row's title and a poster sliver below it.
     */
    fun heroHeightFor(screenHeight: Dp): Dp =
        (screenHeight * 0.72f).coerceIn(300.dp, 460.dp)

    /** Vertical space the home chrome takes before the first rail can draw. */
    fun homeChromeHeight(): Dp =
        TvOverscanTop + TopNavHeight + NavContentGap + TvOverscanBottom
    val HeroCornerRadius = 14.dp
    val RailSpacing = 10.dp
    val CardCornerRadius = 6.dp
    val FocusBorder = 3.dp
    /**
     * Resume bar. Insets must stay clear of [FocusBorder]; the focus ring is drawn
     * over the card bounds and would otherwise cover the bar while focused.
     */
    val ProgressBarHeight = 4.dp
    val ProgressBarBottomInset = 7.dp
    val ProgressBarHorizontalInset = 8.dp
    val ProgressScrimHeight = 32.dp
    /**
     * Focus dwell before a trailer is armed, used when no caller supplies the
     * user's "Trailer Start Delay" setting.
     */
    const val TrailerStartDelayMs = 250
    val PortraitCardWidth = 122.dp
    val PortraitCardHeight = 184.dp
    val FocusedPortraitCardWidth = 258.dp
    val FocusedPortraitCardHeight = 146.dp
    val LandscapeCardWidth = 218.dp
    val LandscapeCardHeight = 122.dp
    val FocusedLandscapeCardWidth = 292.dp
    val FocusedLandscapeCardHeight = 164.dp
    /** Uniform landscape CW cards; focus expands to [FocusedContinueCard*] like Netflix. */
    val ContinueCardWidth = 260.dp
    val ContinueCardHeight = 146.dp
    val FocusedContinueCardWidth = 340.dp
    val FocusedContinueCardHeight = 192.dp
    /**
     * Text-mode rail tiles. Any rail can be switched to these instead of posters;
     * the proportions come from Netflix's "Browse by category" tiles.
     */
    val TextTileHeight = 48.dp
    /** Rounded-rect tiles (Netflix categories), not capsules. */
    val TextTileCorner = 10.dp
    val TextTileMinWidth = 128.dp
    val TextTileHorizontalPadding = 22.dp
    const val ShowCataloguePosterLabels = false

    /**
     * Netflix gives the eye one resting place: the lead catalogue row carries full
     * height and later rows sit slightly back, so the next row's title always peeks.
     * An explicit per-row scale (ViewPack or Customize) wins over this.
     */
    const val SecondaryRailScale = 0.86f
    const val SecondaryRailTitleScale = 0.93f

    /**
     * Hierarchy scale for a catalogue row. Returns 1 for the featured row and for
     * any row the user/pack sized by hand.
     */
    fun railHierarchyScale(
        isFeatured: Boolean,
        hasExplicitScale: Boolean,
        secondary: Float
    ): Float = if (isFeatured || hasExplicitScale) 1f else secondary
}

internal object NetflixHomeTypography {
    val RowTitle = TextStyle(
        fontSize = 23.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold
    )
    val RowSubtitle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    )
    val Metadata = TextStyle(
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium
    )
    val Synopsis = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    )
    val ContinueTitle = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    )
    val ContinueSecondary = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    )
}

internal object NetflixHomeDimensions {
    private const val PORTRAIT_ASPECT_WIDTH = 2f / 3f
    private const val LANDSCAPE_ASPECT_WIDTH = 16f / 9f
    private const val RAIL_HEIGHT_VIEWPORT_FRACTION = 0.285f

    const val CatalogueRailMinHeightPx = 480f
    const val CatalogueRailMaxHeightPx = 520f
    const val CatalogueFocusedMinWidthPx = 850f
    const val CatalogueFocusedMaxWidthPx = 920f
    const val CataloguePortraitMinWidthPx = 320f
    const val CataloguePortraitMaxWidthPx = 348f

    /**
     * @param maxAbsoluteRailHeight when set (focused-info footer on), posters cannot grow past
     * this absolute height so facts + ≥2 synopsis lines stay on-screen under the rail.
     * @param landscapeFocusGrow when true, focused width uses 16:9 (editorial / Top-10 mode).
     * When false, focused width stays portrait aspect × [NetflixHomeMotion.FocusScale].
     */
    fun catalogueRailGeometry(
        usableWidth: Dp,
        density: Density,
        scale: Float = 1f,
        maxAbsoluteRailHeight: Dp? = null,
        landscapeFocusGrow: Boolean = false
    ): NetflixRailGeometry {
        val railMinHeight = with(density) { CatalogueRailMinHeightPx.toDp() }
        val railMaxHeight = with(density) { CatalogueRailMaxHeightPx.toDp() }
        val focusedMinWidth = with(density) { CatalogueFocusedMinWidthPx.toDp() }
        val focusedMaxWidth = with(density) { CatalogueFocusedMaxWidthPx.toDp() }
        val portraitMinWidth = with(density) { CataloguePortraitMinWidthPx.toDp() }
        val portraitMaxWidth = with(density) { CataloguePortraitMaxWidthPx.toDp() }
        val clampedScale = scale.coerceIn(0.55f, 2.5f)
        val uncappedMax = railMaxHeight * clampedScale
        val heightCeiling = maxAbsoluteRailHeight?.let { cap ->
            minOf(uncappedMax, cap.coerceAtLeast(railMinHeight * 0.85f))
        } ?: uncappedMax
        val heightFloor = minOf(railMinHeight * clampedScale.coerceAtMost(1.15f), heightCeiling)
        val railHeight = (usableWidth * RAIL_HEIGHT_VIEWPORT_FRACTION * clampedScale)
            .coerceIn(heightFloor, heightCeiling)
        // Widths follow the (possibly capped) poster height so cards stay proportional.
        val widthScale = if (railMaxHeight > 0.dp) {
            (railHeight / railMaxHeight).coerceIn(0.55f, 2.5f)
        } else {
            clampedScale
        }
        val portraitWidth = (railHeight * PORTRAIT_ASPECT_WIDTH)
            .coerceIn(portraitMinWidth * widthScale.coerceAtMost(clampedScale), portraitMaxWidth * widthScale)
        val focusedWidth = if (landscapeFocusGrow) {
            (railHeight * LANDSCAPE_ASPECT_WIDTH)
                .coerceIn(focusedMinWidth * widthScale.coerceAtMost(clampedScale), focusedMaxWidth * widthScale)
        } else {
            portraitWidth * NetflixHomeMotion.FocusScale
        }
        return NetflixRailGeometry(
            railHeight = railHeight,
            portraitWidth = portraitWidth,
            focusedWidth = focusedWidth
        )
    }
}

internal object NetflixHomeSpacing {
    private const val RAIL_HORIZONTAL_GAP_PX = 16f
    /** Room for focus ring / modest scale without clipping the pivot selector. */
    val RailFocusPadding = 12.dp
    val RailTopPadding = 12.dp
    /** Measured height of a rail heading at the default title scale. */
    val RailTitleHeight = 36.dp
    /**
     * Slice of the following row that stays on screen when a rail is focused.
     * Must cover the gap between rails, the next rail's own top padding, its
     * whole title and a sliver of poster — a half-cut title reads as a bug, not
     * as a peek. The rail asks bring-into-view for exactly this, so the scroll
     * anchor and the poster budget below cannot drift apart.
     */
    val NextRowPeek =
        NetflixHomeTokens.RailSpacing + RailTopPadding + RailTitleHeight + 24.dp

    /**
     * Tallest poster a focused rail may use while keeping the top fade clear of
     * its title and the next row's title-plus-sliver on screen.
     */
    fun focusedPosterCap(screenHeight: Dp): Dp =
        (
            screenHeight -
                NetflixHomeTokens.homeChromeHeight() -
                NetflixHomeTokens.ScrollTopFade -
                RailTopPadding -
                RailTitleHeight -
                RailFocusPadding -
                FocusedMetadataHeight -
                NextRowPeek
            ).coerceAtLeast(180.dp)
    /**
     * Reserved footer under catalogue rails: one middot facts line + 2 synopsis
     * lines. Netflix never reserves a tall slab here, so this stays tight and the
     * reclaimed height goes back to the posters.
     */
    val FocusedMetadataHeight = 78.dp
    /** Title + episode line + short description under CW rail. */
    val ContinueMetadataHeight = 82.dp
    val BottomFocusClearance = 400.dp

    fun railHorizontalGap(density: Density): Dp {
        return with(density) { RAIL_HORIZONTAL_GAP_PX.toDp() }
    }
}

internal object NetflixHomeMotion {
    const val FocusWidthDurationMs = 120
    /**
     * Modest grow when a rail opts out of landscape focus expand (`posterGrow: false`).
     * Default Netflix browse expands portrait → landscape billboard instead.
     */
    const val FocusScale = 1.08f
    /** Short / zero when landscape is already memory-cached to avoid poster flicker under trailers. */
    const val ArtworkCrossfadeDurationMs = 80
    val FocusWidthAnimation: TweenSpec<Dp> = tween(durationMillis = FocusWidthDurationMs)
}

@Immutable
internal data class NetflixRailGeometry(
    val railHeight: Dp,
    val portraitWidth: Dp,
    val focusedWidth: Dp
)

internal sealed class NetflixHomeTarget {
    data class Catalog(
        val item: MetaPreview,
        val addonBaseUrl: String
    ) : NetflixHomeTarget()

    data class ContinueWatching(
        val item: ContinueWatchingItem
    ) : NetflixHomeTarget()
}

internal data class NetflixHeroItem(
    val key: String,
    val title: String,
    val logo: String?,
    val backdrop: String?,
    val poster: String?,
    val description: String?,
    val rating: String?,
    val year: String?,
    val certification: String?,
    val genres: List<String>,
    val runtime: String?,
    val target: NetflixHomeTarget
)

/**
 * Some addons report runtime as a bare minute count ("23"), which reads as a
 * stray number in the middot facts line. Anything already carrying a unit is
 * passed through untouched.
 */
internal fun netflixRuntimeLabel(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (value == "0" || value == "0 min") return null
    return if (value.all { it.isDigit() }) "$value min" else value
}

internal fun MetaPreview.netflixAmbientArtUrl(): String? =
    background ?: landscapePoster ?: poster

internal fun ContinueWatchingItem.netflixAmbientArtUrl(): String? = when (this) {
    is ContinueWatchingItem.InProgress ->
        progress.backdrop ?: progress.poster
    is ContinueWatchingItem.NextUp ->
        info.backdrop ?: info.thumbnail ?: info.poster
}

internal fun MetaPreview.toNetflixHeroItem(addonBaseUrl: String): NetflixHeroItem {
    return NetflixHeroItem(
        key = "catalog|$apiType|$id",
        title = name,
        logo = logo,
        backdrop = background ?: landscapePoster,
        poster = poster,
        description = description,
        rating = imdbRating?.let { String.format("%.1f", it) },
        year = releaseInfo?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value ?: it },
        certification = ageRating,
        genres = genres.take(3),
        runtime = runtime,
        target = NetflixHomeTarget.Catalog(this, addonBaseUrl)
    )
}

internal fun ContinueWatchingItem.toNetflixHeroItem(): NetflixHeroItem {
    return when (this) {
        is ContinueWatchingItem.InProgress -> NetflixHeroItem(
            key = "cw|${progress.contentId}|${progress.videoId}",
            title = progress.name,
            logo = progress.logo,
            backdrop = progress.backdrop,
            poster = progress.poster,
            description = episodeDescription ?: progress.episodeTitle,
            rating = episodeImdbRating?.let { String.format("%.1f", it) },
            year = releaseInfo?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value ?: it },
            certification = null,
            genres = genres.take(3),
            runtime = null,
            target = NetflixHomeTarget.ContinueWatching(this)
        )

        is ContinueWatchingItem.NextUp -> NetflixHeroItem(
            key = "cw|${info.contentId}|${info.videoId}",
            title = info.name,
            logo = info.logo,
            backdrop = info.backdrop ?: info.thumbnail,
            poster = info.poster,
            description = info.episodeDescription ?: info.episodeTitle,
            rating = info.imdbRating?.let { String.format("%.1f", it) },
            year = info.releaseInfo?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value ?: it },
            certification = null,
            genres = info.genres.take(3),
            runtime = null,
            target = NetflixHomeTarget.ContinueWatching(this)
        )
    }
}
