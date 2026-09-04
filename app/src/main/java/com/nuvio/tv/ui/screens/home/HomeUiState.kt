package com.nuvio.tv.ui.screens.home

import androidx.compose.runtime.Immutable
import com.nuvio.tv.core.tracking.TrackingMembershipRemovalConfirmation
import com.nuvio.tv.data.local.StartupAuthNotice
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.HomeRailCustomization
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.core.sync.SyncGenreRowTarget
import com.nuvio.tv.domain.model.WatchProgress

@Immutable
data class HomeUiState(
    val catalogRows: List<CatalogRow> = emptyList(),
    val continueWatchingItems: List<ContinueWatchingItem> = emptyList(),
    val upcomingItems: List<ContinueWatchingItem> = emptyList(),
    val isLoading: Boolean = true,
    val layoutPreferencesReady: Boolean = false,
    val error: String? = null,
    val selectedItemId: String? = null,
    val installedAddonsCount: Int = 0,
    val homeLayout: HomeLayout = HomeLayout.NETFLIX,
    val modernLandscapePostersEnabled: Boolean = false,
    val modernHeroFullScreenBackdropEnabled: Boolean = false,
    val heroItems: List<MetaPreview> = emptyList(),
    val heroCatalogKeys: List<String> = emptyList(),
    val heroSectionEnabled: Boolean = true,
    val modernHomePresentation: ModernHomePresentationState = ModernHomePresentationState(),
    val posterLabelsEnabled: Boolean = true,
    val catalogAddonNameEnabled: Boolean = true,
    val catalogTypeSuffixEnabled: Boolean = true,
    val classicFocusGradientEnabled: Boolean = false,
    val focusedPosterBackdropExpandEnabled: Boolean = false,
    val focusedPosterBackdropExpandDelaySeconds: Int = 3,
    val focusedPosterBackdropTrailerEnabled: Boolean = true,
    val focusedPosterBackdropTrailerMuted: Boolean = true,
    val focusedPosterBackdropTrailerPlaybackTarget: FocusedPosterTrailerPlaybackTarget =
        FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD,
    /** Focus dwell before a card or hero trailer starts. */
    val trailerStartDelayMs: Int = 250,
    val posterCardWidthDp: Int = 126,
    val posterCardHeightDp: Int = 189,
    val posterCardCornerRadiusDp: Int = 12,
    val librarySourceMode: LibrarySourceMode = LibrarySourceMode.LOCAL,
    val libraryListTabs: List<LibraryListTab> = emptyList(),
    val posterLibraryMembership: Map<String, Boolean> = emptyMap(),
    val movieWatchedStatus: Map<String, Boolean> = emptyMap(),
    val posterLibraryPending: Set<String> = emptySet(),
    val movieWatchedPending: Set<String> = emptySet(),
    val showPosterListPicker: Boolean = false,
    val posterListPickerTitle: String? = null,
    val posterListPickerContentType: String? = null,
    val posterListPickerMembership: Map<String, Boolean> = emptyMap(),
    val posterListPickerPending: Boolean = false,
    val posterListPickerError: String? = null,
    val posterListPickerRemovalConfirmations: List<TrackingMembershipRemovalConfirmation> = emptyList(),
    val gridItems: List<GridItem> = emptyList(),
    val hideUnreleasedContent: Boolean = false,
    val showFullReleaseDate: Boolean = true,
    val blurUnwatchedEpisodes: Boolean = false,
    val useEpisodeThumbnailsInCw: Boolean = true,
    val continueWatchingCardStyle: ContinueWatchingCardStyle = ContinueWatchingCardStyle.CARD,
    val heroEnrichmentEnabled: Boolean = false,
    val startupAuthNotice: StartupAuthNotice? = null,
    val homeRows: List<HomeRow> = emptyList(),
    val homeCatalogOrderKeys: List<String> = emptyList(),
    val disabledHomeCatalogKeys: Set<String> = emptySet(),
    val genreRowTargets: Map<String, SyncGenreRowTarget> = emptyMap(),
    /** All imported collections (not only those visible on the home rail). */
    val collections: List<Collection> = emptyList(),
    /** Active Studio view pack name when imported; null = default catalog order. */
    val activeViewPackName: String? = null,
    /** Enables first-card Left rail editing on Netflix home. */
    val viewPackCustomizationModeEnabled: Boolean = false,
    /** Live Nuvio-owned home overrides, layered over imported packs/default rails. */
    val homeRailCustomizations: Map<String, HomeRailCustomization> = emptyMap(),
    val homeRailShuffleEnabled: Boolean = false,
    val homeRailShuffleIntervalHours: Int = 24,
    val homeRailShuffleNonce: Long = 0L,
    val activeViewPackRotateEnabled: Boolean = false,
    /** Home-tab pack rail order (excludes nested Movies/Shows extras). */
    val viewPackOrderKeys: List<String> = emptyList(),
    /** Per-rail card size scales from the active view pack, keyed by home order key. */
    val viewPackRowScales: Map<String, Float> = emptyMap(),
    /** Per-rail poster title visibility from the active view pack. */
    val viewPackRowShowLabels: Map<String, Boolean> = emptyMap(),
    /** Per-rail in-card trailer opt-in from the active view pack. */
    val viewPackRowTrailers: Map<String, Boolean> = emptyMap(),
    /** Per-rail focus-grow (landscape expand) from the active view pack. */
    val viewPackRowPosterGrow: Map<String, Boolean> = emptyMap(),
    /** Per-rail text-tile mode (Studio `genreRail` blocks) from the active view pack. */
    val viewPackRowAsText: Map<String, Boolean> = emptyMap(),
    /** Pack-global catalog/media poster scale (1 = default). */
    val viewPackCatalogPosterScale: Float = 1f,
    /** Pack-global collection landscape tile scale (1 = default). */
    val viewPackCollectionLandscapeScale: Float = 1f,
    /** Pack-global rail heading text scale (1 = default). */
    val viewPackCollectionTitleScale: Float = 1f,
    /** Studio pack includes a hero block — show inset Featured banner. */
    val viewPackHeroEnabled: Boolean = false,
    /** Pack hero block `trailer` — when pack active, gates hero trailer autoplay. */
    val viewPackHeroTrailerEnabled: Boolean = false,
    /** Eyebrow label for the pack Featured banner (defaults to Featured). */
    val viewPackHeroLabel: String = "Featured",
    /** Pack hero dataSource (`featured` / `catalog:…`) — spotlight stays on this source. */
    val viewPackHeroDataSource: String? = null,
    /** Fixed Featured spotlight from the pack hero source (not focus-driven). */
    val viewPackFeaturedPreview: HeroPreview? = null,
    /** Meta for navigating Play / More Info on the fixed Featured banner. */
    val viewPackFeaturedMeta: MetaPreview? = null,
    val viewPackFeaturedAddonBaseUrl: String = "",
    /** Studio hero block height in canvas px (e.g. 520 of 1080). */
    val viewPackFeaturedHeightPx: Int? = null,
    /** Studio Movies tab pack; null = type-filter / discovery fallback. */
    val moviesScreenPack: NetflixScreenPackState? = null,
    /** Studio TV Shows tab pack; null = type-filter / discovery fallback. */
    val showsScreenPack: NetflixScreenPackState? = null
)

@Immutable
data class NetflixScreenPackState(
    val orderKeys: List<String>,
    val rowScales: Map<String, Float> = emptyMap(),
    val rowShowLabels: Map<String, Boolean> = emptyMap(),
    val rowTrailers: Map<String, Boolean> = emptyMap(),
    val rowPosterGrow: Map<String, Boolean> = emptyMap(),
    /** Rails the pack authored as text tiles (Studio `genreRail` blocks). */
    val rowAsText: Map<String, Boolean> = emptyMap(),
    val catalogPosterScale: Float = 1f,
    val collectionLandscapeScale: Float = 1f,
    val collectionTitleScale: Float = 1f,
    val heroEnabled: Boolean = false,
    val heroTrailerEnabled: Boolean = false,
    val heroLabel: String = "Featured",
    val heroDataSource: String? = null,
    val featuredHeightPx: Int? = null,
    val hasContinueWatching: Boolean = false
)

@Immutable
sealed class ContinueWatchingItem {
    @Immutable
    data class InProgress(
        val progress: WatchProgress,
        val episodeDescription: String? = null,
        val episodeThumbnail: String? = null,
        val episodeImdbRating: Float? = null,
        val genres: List<String> = emptyList(),
        val releaseInfo: String? = null,
        val contentLanguage: String? = null
    ) : ContinueWatchingItem()

    @Immutable
    data class NextUp(val info: NextUpInfo) : ContinueWatchingItem()
}

@Immutable
data class NextUpInfo(
    val contentId: String,
    val contentType: String,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    val videoId: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String?,
    val episodeDescription: String? = null,
    val thumbnail: String?,
    val released: String? = null,
    val hasAired: Boolean = true,
    val airDateLabel: String? = null,
    val lastWatched: Long,
    val imdbRating: Float? = null,
    val genres: List<String> = emptyList(),
    val releaseInfo: String? = null,
    val sortTimestamp: Long,
    val releaseTimestamp: Long? = null,
    val isReleaseAlert: Boolean = false,
    val isNewSeasonRelease: Boolean = false,
    val seedSeason: Int? = null,
    val seedEpisode: Int? = null,
    val contentLanguage: String? = null
)

@Immutable
sealed class HomeRow {
    @Immutable
    data class Catalog(val row: CatalogRow) : HomeRow()

    @Immutable
    data class CollectionRow(val collection: Collection) : HomeRow()

    /**
     * Placeholder for a catalog row whose data hasn't been fetched yet.
     * Rendered as a shimmer/skeleton row until the user scrolls near it
     * and the actual catalog data is loaded on demand.
     */
    @Immutable
    data class PlaceholderCatalog(
        val catalogKey: String,
        val stableCatalogKey: String,
        val addonId: String,
        val addonName: String,
        val addonBaseUrl: String,
        val catalogId: String,
        val catalogName: String,
        val apiType: String,
        val displayTitle: String
    ) : HomeRow()
}

@Immutable
sealed class GridItem {
    @Immutable
    data class Hero(val items: List<MetaPreview>) : GridItem()
    @Immutable
    data class SectionDivider(
        val catalogName: String,
        val catalogId: String,
        val addonBaseUrl: String,
        val addonId: String,
        val type: String
    ) : GridItem()
    @Immutable
    data class Content(
        val item: MetaPreview,
        val addonBaseUrl: String,
        val catalogId: String,
        val catalogName: String
    ) : GridItem()
    @Immutable
    data class SeeAll(
        val catalogId: String,
        val addonId: String,
        val addonBaseUrl: String,
        val type: String
    ) : GridItem()
    @Immutable
    data class CollectionHeader(
        val collectionId: String,
        val title: String
    ) : GridItem()
    @Immutable
    data class CollectionFolder(
        val collectionId: String,
        val collectionTitle: String,
        val focusGlowEnabled: Boolean,
        val folder: com.nuvio.tv.domain.model.CollectionFolder
    ) : GridItem()
}

sealed class HomeEvent {
    data class OnItemClick(val itemId: String, val itemType: String) : HomeEvent()
    data class OnLoadMoreCatalog(val catalogId: String, val addonId: String, val type: String) : HomeEvent()
    data class OnRemoveContinueWatching(
        val contentId: String,
        val season: Int? = null,
        val episode: Int? = null,
        val isNextUp: Boolean = false
    ) : HomeEvent()
    data object OnRetry : HomeEvent()
}

fun homeItemStatusKey(itemId: String, itemType: String): String {
    return "${itemType.lowercase()}|$itemId"
}
