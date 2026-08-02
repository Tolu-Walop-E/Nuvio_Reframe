package com.nuvio.tv.ui.screens.home.netflix

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    val HeroHeight = 318.dp
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
    val ContinueCardWidth = 236.dp
    val ContinueCardHeight = 132.dp
    val FocusedContinueCardWidth = 292.dp
    val FocusedContinueCardHeight = 164.dp
    val GenreCardWidth = 150.dp
    val GenreCardHeight = 54.dp
}

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
