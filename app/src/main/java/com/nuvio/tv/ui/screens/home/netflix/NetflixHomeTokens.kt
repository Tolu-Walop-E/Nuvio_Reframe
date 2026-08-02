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
    val Scrim = Color.Black.copy(alpha = 0.68f)

    val PageHorizontalPadding = 40.dp
    val TopNavHeight = 48.dp
    val HeroHeight = 420.dp
    val HeroCornerRadius = 26.dp
    val RailSpacing = 14.dp
    val CardCornerRadius = 10.dp
    val FocusBorder = 3.dp
    val PortraitCardWidth = 122.dp
    val PortraitCardHeight = 184.dp
    val FocusedPortraitCardWidth = 258.dp
    val FocusedPortraitCardHeight = 146.dp
    val LandscapeCardWidth = 218.dp
    val LandscapeCardHeight = 122.dp
    val FocusedLandscapeCardWidth = 292.dp
    val FocusedLandscapeCardHeight = 164.dp
    val ContinueCardWidth = 260.dp
    val ContinueCardHeight = 146.dp
    val FocusedContinueCardWidth = 440.dp
    val FocusedContinueCardHeight = 248.dp
    val GenreCardWidth = 150.dp
    val GenreCardHeight = 54.dp
    const val ShowCataloguePosterLabels = false
}

internal object NetflixHomeTypography {
    val RowTitle = TextStyle(
        fontSize = 26.sp,
        lineHeight = 30.sp,
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

    fun catalogueRailGeometry(usableWidth: Dp, density: Density): NetflixRailGeometry {
        val railMinHeight = with(density) { CatalogueRailMinHeightPx.toDp() }
        val railMaxHeight = with(density) { CatalogueRailMaxHeightPx.toDp() }
        val focusedMinWidth = with(density) { CatalogueFocusedMinWidthPx.toDp() }
        val focusedMaxWidth = with(density) { CatalogueFocusedMaxWidthPx.toDp() }
        val portraitMinWidth = with(density) { CataloguePortraitMinWidthPx.toDp() }
        val portraitMaxWidth = with(density) { CataloguePortraitMaxWidthPx.toDp() }
        val railHeight = (usableWidth * RAIL_HEIGHT_VIEWPORT_FRACTION)
            .coerceIn(railMinHeight, railMaxHeight)
        return NetflixRailGeometry(
            railHeight = railHeight,
            portraitWidth = (railHeight * PORTRAIT_ASPECT_WIDTH)
                .coerceIn(portraitMinWidth, portraitMaxWidth),
            focusedWidth = (railHeight * LANDSCAPE_ASPECT_WIDTH)
                .coerceIn(focusedMinWidth, focusedMaxWidth)
        )
    }
}

internal object NetflixHomeSpacing {
    private const val RAIL_HORIZONTAL_GAP_PX = 16f
    val RailFocusPadding = 10.dp
    val RailTopPadding = 24.dp
    val FocusedMetadataHeight = 104.dp
    val ContinueMetadataHeight = 58.dp
    val BottomFocusClearance = 400.dp

    fun railHorizontalGap(density: Density): Dp {
        return with(density) { RAIL_HORIZONTAL_GAP_PX.toDp() }
    }
}

internal object NetflixHomeMotion {
    const val FocusWidthDurationMs = 180
    const val ArtworkCrossfadeDurationMs = 190
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
