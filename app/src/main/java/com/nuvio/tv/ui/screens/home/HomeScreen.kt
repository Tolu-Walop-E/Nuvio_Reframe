package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.ui.components.ErrorState
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.screens.home.netflix.NetflixContentTab
import com.nuvio.tv.ui.screens.home.netflix.NetflixHomeContent
import com.nuvio.tv.ui.screens.home.netflix.NetflixHomeFeature
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.LOCAL_LIBRARY_LIST_KEY
import com.nuvio.tv.core.tracking.supportsMembershipFor
import com.nuvio.tv.data.local.StartupAuthNotice
import com.nuvio.tv.ui.components.posteroptions.TrackingRemovalConfirmationDialog
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class HomePosterOptionsTarget(
    val item: MetaPreview,
    val addonBaseUrl: String
)

/**
 * First paint of Netflix home on Shield is the hitch: LazyColumn, rails, Coil and
 * the hero all compose in the same frames the user can see. Keep the spinner up
 * until real rails exist, then let that first composition happen *under* the
 * loader so enter is a fade of the spinner, not a stuttering layout.
 */
private const val HOME_FIRST_PAINT_SETTLE_MS = 1_500L
/** Don't trap the user on the spinner if addons are slow or missing. */
private const val HOME_STABLE_GATE_TIMEOUT_MS = 8_000L
private const val HOME_PLACEHOLDER_ITEM_PREFIX = "__placeholder_"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit = { item ->
        onNavigateToDetail(
            when (item) {
                is ContinueWatchingItem.InProgress -> item.progress.contentId
                is ContinueWatchingItem.NextUp -> item.info.contentId
            },
            when (item) {
                is ContinueWatchingItem.InProgress -> item.progress.contentType
                is ContinueWatchingItem.NextUp -> item.info.contentType
            },
            ""
        )
    },
    onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit = onContinueWatchingClick,
    onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit = onContinueWatchingClick,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit = { _, _, _ -> },
    onNavigateToGenre: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onNavigateToFolderDetail: (String, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val initialCwResolved by viewModel.initialCwResolved.collectAsStateWithLifecycle()
    val scrollToTopTrigger by viewModel.scrollToTopTrigger.collectAsStateWithLifecycle()
    val effectiveAutoplayEnabled by viewModel.effectiveAutoplayEnabled.collectAsStateWithLifecycle(
        initialValue = false
    )
    val hasCatalogContent = uiState.catalogRows.any { it.items.isNotEmpty() }
    val hasCollectionContent = uiState.homeRows.any { it is HomeRow.CollectionRow }
    val hasHeroContent = uiState.heroItems.isNotEmpty()
    val hasRealHomeRails = uiState.hasRealHomeRails()
    // Netflix / view-pack Netflix presentation renders from homeRows/catalogs directly —
    // don't block first paint on the classic modern presentation builder.
    val modernPresentationReady =
        uiState.homeLayout != HomeLayout.MODERN ||
            uiState.homeLayout == HomeLayout.NETFLIX ||
            !uiState.activeViewPackName.isNullOrBlank() ||
            uiState.modernHomePresentation.rows.list.isNotEmpty() ||
            (uiState.heroSectionEnabled && hasHeroContent && !hasCatalogContent && !hasCollectionContent)
    var showHomeContentWithAnimation by rememberSaveable { mutableStateOf(false) }
    var hasShownInitialHomeContent by rememberSaveable { mutableStateOf(false) }
    // Home is composed under the spinner; this flag only dismisses the overlay.
    var homeStableGateReleased by rememberSaveable { mutableStateOf(false) }
    // Mount the real home (still covered) once rails exist so first composition
    // is not the frame the user walks into.
    var homeContentMounted by rememberSaveable { mutableStateOf(false) }
    // Track that catalog loading has started at least once (isLoading went true→false).
    var catalogLoadingStarted by rememberSaveable { mutableStateOf(false) }
    var posterOptionsTarget by remember { mutableStateOf<HomePosterOptionsTarget?>(null) }

    LaunchedEffect(uiState.homeLayout) {
        if (uiState.homeLayout != HomeLayout.MODERN) {
            HeroBackdropState.update(null)
        }
    }

    // Notify ViewModel of locale changes after activity recreation
    LaunchedEffect(Unit) {
        viewModel.notifyLocaleChanged()
    }

    val movieWatchedStatus = uiState.movieWatchedStatus
    // Use a stable lambda whose identity NEVER changes. The lambda captures
    // movieWatchedStatus via rememberUpdatedState so it always reads the latest
    // value without forcing downstream recomposition from lambda identity change.
    val latestMovieWatchedStatus = androidx.compose.runtime.rememberUpdatedState(movieWatchedStatus)
    val isCatalogItemWatched: (MetaPreview) -> Boolean = remember {
        { item: MetaPreview -> latestMovieWatchedStatus.value[homeItemStatusKey(item.id, item.apiType)] == true }
    }
    val onCatalogItemLongPress: (MetaPreview, String) -> Unit = remember {
        { item, addonBaseUrl ->
            posterOptionsTarget = HomePosterOptionsTarget(item, addonBaseUrl)
            viewModel.refreshPosterLibraryStatus(item)
        }
    }

    val onNavigateToDetailStable = remember(onNavigateToDetail) { onNavigateToDetail }
    val onContinueWatchingClickStable = remember(onContinueWatchingClick) { onContinueWatchingClick }
    val onContinueWatchingStartFromBeginningStable = remember(onContinueWatchingStartFromBeginning) { onContinueWatchingStartFromBeginning }
    val onContinueWatchingPlayManuallyStable = remember(onContinueWatchingPlayManually) { onContinueWatchingPlayManually }
    val onNavigateToCatalogSeeAllStable = remember(onNavigateToCatalogSeeAll) { onNavigateToCatalogSeeAll }
    val onNavigateToGenreStable = remember(onNavigateToGenre) { onNavigateToGenre }
    val onNavigateToFolderDetailStable = remember(onNavigateToFolderDetail) { onNavigateToFolderDetail }
    val onRemoveContinueWatchingStable = remember(viewModel) {
        { contentId: String, season: Int?, episode: Int?, isNextUp: Boolean ->
            viewModel.onEvent(HomeEvent.OnRemoveContinueWatching(contentId, season, episode, isNextUp))
        }
    }

    LaunchedEffect(
        uiState.isLoading,
        hasCatalogContent,
        hasCollectionContent,
        hasHeroContent,
        hasRealHomeRails,
        initialCwResolved,
        modernPresentationReady,
        uiState.continueWatchingItems.size,
        uiState.installedAddonsCount
    ) {
        // Track that addons/content are known (even if isLoading flipped too fast to catch).
        if (uiState.installedAddonsCount > 0 || hasCatalogContent || hasCollectionContent || hasHeroContent) {
            catalogLoadingStarted = true
        }
        // Continue Watching and placeholder shimmer cards used to open the gate
        // immediately. The real catalog rails then mounted on-screen and hitch.
        // Wait for at least one real rail (or a genuine empty-addon state).
        val canMountHome =
            hasRealHomeRails ||
                hasCollectionContent ||
                (uiState.installedAddonsCount == 0 && initialCwResolved)
        if (!homeContentMounted && catalogLoadingStarted && canMountHome && modernPresentationReady) {
            Log.d(
                "HomeGate",
                "MOUNT: realRails=$hasRealHomeRails collections=$hasCollectionContent " +
                    "hero=$hasHeroContent addons=${uiState.installedAddonsCount}"
            )
            homeContentMounted = true
        }
    }

    LaunchedEffect(homeContentMounted) {
        if (!homeContentMounted || homeStableGateReleased) return@LaunchedEffect
        delay(HOME_FIRST_PAINT_SETTLE_MS)
        Log.d("HomeGate", "RELEASE after ${HOME_FIRST_PAINT_SETTLE_MS}ms settle")
        homeStableGateReleased = true
    }

    LaunchedEffect(Unit) {
        // Safety timeout — if catalogs haven't arrived, show whatever is
        // available rather than spinning forever (cleared cache, no addons).
        delay(HOME_STABLE_GATE_TIMEOUT_MS)
        if (!homeStableGateReleased) {
            Log.d(
                "HomeGate",
                "RELEASE timeout: isLoading=${uiState.isLoading} cwResolved=$initialCwResolved " +
                    "realRails=$hasRealHomeRails catalogs=$hasCatalogContent " +
                    "cwItems=${uiState.continueWatchingItems.size}"
            )
            homeContentMounted = true
            homeStableGateReleased = true
        }
    }

    val posterCardStyle = remember(
        uiState.posterCardWidthDp,
        uiState.posterCardCornerRadiusDp
    ) {
        val computedHeightDp = (uiState.posterCardWidthDp * 1.5f).roundToInt()
        PosterCardStyle(
            width = uiState.posterCardWidthDp.dp,
            height = computedHeightDp.dp,
            cornerRadius = uiState.posterCardCornerRadiusDp.dp,
            focusedBorderWidth = PosterCardDefaults.Style.focusedBorderWidth,
            focusedScale = PosterCardDefaults.Style.focusedScale
        )
    }

    val noAddonsError = stringResource(R.string.home_error_no_addons)
    val noCatalogAddonsError = stringResource(R.string.home_error_no_catalog_addons)
    val hasAnyContent = uiState.catalogRows.isNotEmpty() ||
        uiState.continueWatchingItems.isNotEmpty() ||
        uiState.heroItems.isNotEmpty() ||
        hasCollectionContent
    val showStartupLoader = when {
        !uiState.layoutPreferencesReady -> true
        uiState.isLoading && !hasAnyContent -> true
        uiState.error == noAddonsError && uiState.catalogRows.isEmpty() -> !homeStableGateReleased
        uiState.error == noCatalogAddonsError && uiState.catalogRows.isEmpty() && !hasCollectionContent && !hasHeroContent -> !homeStableGateReleased
        uiState.error != null && uiState.catalogRows.isEmpty() -> false
        !uiState.isLoading && !hasAnyContent -> !homeStableGateReleased
        else -> !homeStableGateReleased || !modernPresentationReady || !showHomeContentWithAnimation
    }

    // Reports the home screen as fully drawn once it leaves the loading state so startup timing is measurable and post-launch work can be deferred.
    ReportDrawnWhen { !showStartupLoader }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            !uiState.layoutPreferencesReady -> {
                Unit
            }

            uiState.isLoading && !hasAnyContent -> {
                Unit
            }

            uiState.error == noAddonsError && uiState.catalogRows.isEmpty() -> {
                if (!homeStableGateReleased) {
                    Unit
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_addons),
                            style = MaterialTheme.typography.bodyLarge,
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }
                }
            }

            uiState.error == noCatalogAddonsError && uiState.catalogRows.isEmpty() && !hasCollectionContent && !hasHeroContent -> {
                if (!homeStableGateReleased) {
                    Unit
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_catalog_addons),
                            style = MaterialTheme.typography.bodyLarge,
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }
                }
            }

            uiState.error != null && uiState.catalogRows.isEmpty() -> {
                ErrorState(
                    message = uiState.error ?: stringResource(R.string.error_generic),
                    onRetry = { viewModel.onEvent(HomeEvent.OnRetry) }
                )
            }

            !uiState.isLoading && !hasAnyContent -> {
                // Don't show "no catalogs" until the stable gate has released —
                // addons may still be loading from remote after a cache clear.
                if (!homeStableGateReleased) {
                    Unit
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.web_no_catalogs),
                            style = MaterialTheme.typography.bodyLarge,
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }
                }
            }

            else -> {
                // Mount under the spinner once rails exist so first composition
                // is covered. The overlay stays until [homeStableGateReleased].
                if (!homeContentMounted) {
                    Unit
                } else if (!modernPresentationReady) {
                    Unit
                } else {
                    // First composition is covered by the spinner — skip the
                    // slide-in so those frames aren't spent on an animation the
                    // user cannot see.
                    LaunchedEffect(Unit) {
                        if (!showHomeContentWithAnimation) {
                            hasShownInitialHomeContent = true
                            showHomeContentWithAnimation = true
                        }
                    }
                    // Keep loading visible during the single-frame gap before animation starts.
                    if (!showHomeContentWithAnimation) {
                        Unit
                    }
                    AnimatedVisibility(
                        visible = showHomeContentWithAnimation,
                        enter = if (hasShownInitialHomeContent) {
                            EnterTransition.None
                        } else {
                            fadeIn(animationSpec = tween(180)) +
                                slideInVertically(
                                    initialOffsetY = { it / 24 },
                                    animationSpec = tween(180)
                                )
                        }
                    ) {
                        when (uiState.homeLayout) {
                            HomeLayout.CLASSIC -> ClassicHomeRoute(
                                viewModel = viewModel,
                                uiState = uiState,
                                posterCardStyle = posterCardStyle,
                                onNavigateToDetail = onNavigateToDetailStable,
                                onContinueWatchingClick = onContinueWatchingClickStable,
                                onContinueWatchingStartFromBeginning = onContinueWatchingStartFromBeginningStable,
                                onContinueWatchingPlayManually = onContinueWatchingPlayManuallyStable,
                                showContinueWatchingManualPlayOption = effectiveAutoplayEnabled,
                                onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAllStable,
                                onNavigateToFolderDetail = onNavigateToFolderDetailStable,
                                isCatalogItemWatched = isCatalogItemWatched,
                                onCatalogItemLongPress = onCatalogItemLongPress
                            )

                            HomeLayout.GRID -> GridHomeRoute(
                                viewModel = viewModel,
                                uiState = uiState,
                                posterCardStyle = posterCardStyle,
                                onNavigateToDetail = onNavigateToDetailStable,
                                onContinueWatchingClick = onContinueWatchingClickStable,
                                onContinueWatchingStartFromBeginning = onContinueWatchingStartFromBeginningStable,
                                onContinueWatchingPlayManually = onContinueWatchingPlayManuallyStable,
                                showContinueWatchingManualPlayOption = effectiveAutoplayEnabled,
                                onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAllStable,
                                onNavigateToFolderDetail = onNavigateToFolderDetailStable,
                                isCatalogItemWatched = isCatalogItemWatched,
                                onCatalogItemLongPress = onCatalogItemLongPress
                            )

                            HomeLayout.NETFLIX,
                            HomeLayout.MODERN -> ModernHomeRoute(
                                viewModel = viewModel,
                                uiState = uiState,
                                onNavigateToDetail = onNavigateToDetailStable,
                                onContinueWatchingClick = onContinueWatchingClickStable,
                                onContinueWatchingStartFromBeginning = onContinueWatchingStartFromBeginningStable,
                                onContinueWatchingPlayManually = onContinueWatchingPlayManuallyStable,
                                showContinueWatchingManualPlayOption = effectiveAutoplayEnabled,
                                onNavigateToFolderDetail = onNavigateToFolderDetailStable,
                                onNavigateToGenre = onNavigateToGenreStable,
                                isCatalogItemWatched = isCatalogItemWatched,
                                onCatalogItemLongPress = onCatalogItemLongPress
                            )
                        }
                    }
                }
            }
        }

        if (showStartupLoader) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NuvioTheme.colors.Background),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }

        val startupAuthNotice = uiState.startupAuthNotice
        if (startupAuthNotice != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = NuvioTheme.spacing.xl)
                    .background(
                        color = Color(0xFF5A1C1C),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = when (startupAuthNotice) {
                        StartupAuthNotice.NUVIO -> stringResource(R.string.auth_notice_nuvio_logged_out)
                        StartupAuthNotice.TRAKT -> stringResource(R.string.auth_notice_trakt_logged_out)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextPrimary
                )
            }
        }
    }

    val selectedPoster = posterOptionsTarget
    if (selectedPoster != null) {
        val item = selectedPoster.item
        val statusKey = homeItemStatusKey(item.id, item.apiType)
        val isMovie = item.apiType.equals("movie", ignoreCase = true)
        val isSeries = item.apiType.equals("series", ignoreCase = true) ||
            item.apiType.equals("tv", ignoreCase = true) ||
            item.apiType.equals("anime", ignoreCase = true)
        HomePosterOptionsDialog(
            title = item.name,
            isInLibrary = uiState.posterLibraryMembership[statusKey] == true,
            isLibraryPending = statusKey in uiState.posterLibraryPending,
            showManageLists = uiState.librarySourceMode != LibrarySourceMode.LOCAL,
            isMovie = isMovie,
            isSeries = isSeries,
            isWatched = movieWatchedStatus[statusKey] == true,
            isWatchedPending = statusKey in uiState.movieWatchedPending,
            onDismiss = { posterOptionsTarget = null },
            onDetails = {
                onNavigateToDetail(item.id, item.apiType, selectedPoster.addonBaseUrl)
                posterOptionsTarget = null
            },
            onToggleLibrary = {
                if (uiState.librarySourceMode != LibrarySourceMode.LOCAL) {
                    viewModel.openPosterListPicker(item, selectedPoster.addonBaseUrl)
                } else {
                    viewModel.togglePosterLibrary(item, selectedPoster.addonBaseUrl)
                }
                posterOptionsTarget = null
            },
            onToggleWatched = {
                if (isMovie) {
                    viewModel.togglePosterMovieWatched(item)
                } else {
                    viewModel.togglePosterSeriesWatched(item)
                }
                posterOptionsTarget = null
            }
        )
    }

    if (uiState.showPosterListPicker) {
        val localTab = LibraryListTab(
            key = LOCAL_LIBRARY_LIST_KEY,
            title = stringResource(R.string.trakt_library_source_nuvio),
            type = LibraryListTab.Type.WATCHLIST
        )
        val contentType = uiState.posterListPickerContentType.orEmpty()
        val destinationTabs = uiState.libraryListTabs.filter { tab ->
            tab.supportsMembershipFor(contentType)
        }
        HomeLibraryListPickerDialog(
            title = uiState.posterListPickerTitle ?: stringResource(R.string.detail_lists_fallback),
            tabs = listOf(localTab) + destinationTabs,
            membership = uiState.posterListPickerMembership,
            isPending = uiState.posterListPickerPending,
            error = uiState.posterListPickerError,
            onToggle = { key -> viewModel.togglePosterListPickerMembership(key) },
            onSave = { viewModel.savePosterListPickerMembership() },
            onDismiss = { viewModel.dismissPosterListPicker() }
        )
    }

    if (uiState.posterListPickerRemovalConfirmations.isNotEmpty()) {
        TrackingRemovalConfirmationDialog(
            itemTitle = uiState.posterListPickerTitle.orEmpty(),
            confirmations = uiState.posterListPickerRemovalConfirmations,
            isPending = uiState.posterListPickerPending,
            onConfirm = viewModel::confirmPosterListPickerRemoval,
            onDismiss = viewModel::cancelPosterListPickerRemoval
        )
    }
}

@Composable
private fun ClassicHomeRoute(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    posterCardStyle: PosterCardStyle,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit,
    onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit,
    showContinueWatchingManualPlayOption: Boolean,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit = { _, _ -> },
    isCatalogItemWatched: (MetaPreview) -> Boolean,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit
) {
    val focusState by viewModel.focusState.collectAsStateWithLifecycle()
    val scrollToTopTrigger by viewModel.scrollToTopTrigger.collectAsStateWithLifecycle()
    ClassicHomeContent(
        uiState = uiState,
        posterCardStyle = posterCardStyle,
        focusState = focusState,
        scrollToTopTrigger = scrollToTopTrigger,
        trailerPreviewUrls = viewModel.trailerPreviewUrls,
        trailerPreviewAudioUrls = viewModel.trailerPreviewAudioUrls,
        onNavigateToDetail = onNavigateToDetail,
        onContinueWatchingClick = onContinueWatchingClick,
        onContinueWatchingStartFromBeginning = onContinueWatchingStartFromBeginning,
        onContinueWatchingPlayManually = onContinueWatchingPlayManually,
        showContinueWatchingManualPlayOption = showContinueWatchingManualPlayOption,
        onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAll,
        onNavigateToFolderDetail = onNavigateToFolderDetail,
        onRemoveContinueWatching = { contentId, season, episode, isNextUp ->
            viewModel.onEvent(HomeEvent.OnRemoveContinueWatching(contentId, season, episode, isNextUp))
        },
        isCatalogItemWatched = isCatalogItemWatched,
        onCatalogItemLongPress = onCatalogItemLongPress,
        onRequestTrailerPreview = { item ->
            viewModel.requestTrailerPreview(item)
        },
        onItemFocus = { item ->
            viewModel.onItemFocus(item)
        },
        onSaveFocusState = { vi, vo, rk, ikm, m, ri, ii ->
            viewModel.saveFocusState(vi, vo, rk, ikm, m, ri, ii)
        },
        onRequestLazyCatalogLoad = remember(viewModel) {
            { catalogKey: String -> viewModel.requestLazyCatalogLoad(catalogKey) }
        }
    )
}

@Composable
private fun GridHomeRoute(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    posterCardStyle: PosterCardStyle,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit,
    onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit,
    showContinueWatchingManualPlayOption: Boolean,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit = { _, _ -> },
    isCatalogItemWatched: (MetaPreview) -> Boolean,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit
) {
    val gridFocusState by viewModel.gridFocusState.collectAsStateWithLifecycle()
    val scrollToTopTrigger by viewModel.scrollToTopTrigger.collectAsStateWithLifecycle()
    GridHomeContent(
        uiState = uiState,
        posterCardStyle = posterCardStyle,
        gridFocusState = gridFocusState,
        scrollToTopTrigger = scrollToTopTrigger,
        onNavigateToDetail = onNavigateToDetail,
        onContinueWatchingClick = onContinueWatchingClick,
        onContinueWatchingStartFromBeginning = onContinueWatchingStartFromBeginning,
        onContinueWatchingPlayManually = onContinueWatchingPlayManually,
        showContinueWatchingManualPlayOption = showContinueWatchingManualPlayOption,
        onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAll,
        onNavigateToFolderDetail = onNavigateToFolderDetail,
        onRemoveContinueWatching = remember(viewModel) {
            { contentId, season, episode, isNextUp ->
                viewModel.onEvent(HomeEvent.OnRemoveContinueWatching(contentId, season, episode, isNextUp))
            }
        },
        isCatalogItemWatched = isCatalogItemWatched,
        onCatalogItemLongPress = onCatalogItemLongPress,
        onItemFocus = remember(viewModel) {
            { item ->
                viewModel.onItemFocus(item)
            }
        },
        onSaveGridFocusState = remember(viewModel) {
            { vi, vo, key ->
                viewModel.saveGridFocusState(vi, vo, focusedItemKey = key)
            }
        }
    )
}

@Composable
private fun ModernHomeRoute(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit,
    onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit,
    showContinueWatchingManualPlayOption: Boolean,
    onNavigateToFolderDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToGenre: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    isCatalogItemWatched: (MetaPreview) -> Boolean,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit
) {
    val focusState by viewModel.focusState.collectAsStateWithLifecycle()
    val scrollToTopTrigger by viewModel.scrollToTopTrigger.collectAsStateWithLifecycle()
    val pendingNetflixFocusRailKey by viewModel.pendingNetflixFocusRailKey.collectAsStateWithLifecycle()
    val netflixContentTab by viewModel.netflixContentTab.collectAsStateWithLifecycle()
    val netflixFolderRails by viewModel.netflixFolderRails.collectAsStateWithLifecycle()
    val enrichingItemId by viewModel.enrichingItemId.collectAsStateWithLifecycle()
    val lastEnrichedPreview by viewModel.lastEnrichedPreview.collectAsStateWithLifecycle()
    val enrichedPreviews by viewModel.enrichedPreviews.collectAsStateWithLifecycle()
    val failedEnrichmentIds by viewModel.failedEnrichmentIds.collectAsStateWithLifecycle()
    val requestTrailerPreview = remember(viewModel) {
        { itemId: String, title: String, releaseInfo: String?, apiType: String ->
            viewModel.requestTrailerPreview(itemId, title, releaseInfo, apiType)
        }
    }
    val loadMoreCatalog = remember(viewModel) {
        { catalogId: String, addonId: String, type: String ->
            viewModel.onEvent(HomeEvent.OnLoadMoreCatalog(catalogId, addonId, type))
        }
    }
    val removeContinueWatching = remember(viewModel) {
        { contentId: String, season: Int?, episode: Int?, isNextUp: Boolean ->
            viewModel.onEvent(HomeEvent.OnRemoveContinueWatching(contentId, season, episode, isNextUp))
        }
    }
    val saveModernFocusState = remember(viewModel) {
        { vi: Int, vo: Int, rk: String?, ikm: Map<String, String>, m: Map<String, Int>, ri: Int, ii: Int ->
            viewModel.saveFocusState(vi, vo, rk, ikm, m, ri, ii)
        }
    }
    val preloadAdjacentItem = remember(viewModel) {
        { item: MetaPreview ->
            viewModel.preloadAdjacentItem(item)
        }
    }
    val useNetflixPresentation =
        NetflixHomeFeature.AVAILABLE &&
            (uiState.homeLayout == HomeLayout.NETFLIX ||
                (uiState.homeLayout == HomeLayout.MODERN && !uiState.activeViewPackName.isNullOrBlank()))
    if (useNetflixPresentation) {
        NetflixHomeContent(
            uiState = uiState,
            focusState = focusState,
            onNavigateToDetail = onNavigateToDetail,
            onContinueWatchingClick = onContinueWatchingClick,
            onContinueWatchingStartFromBeginning = onContinueWatchingStartFromBeginning,
            onContinueWatchingPlayManually = onContinueWatchingPlayManually,
            showContinueWatchingManualPlayOption = showContinueWatchingManualPlayOption,
            onLoadMoreCatalog = loadMoreCatalog,
            onRemoveContinueWatching = removeContinueWatching,
            isCatalogItemWatched = isCatalogItemWatched,
            onCatalogItemLongPress = onCatalogItemLongPress,
            onNavigateToFolderDetail = onNavigateToFolderDetail,
            onItemFocus = remember(viewModel) {
                { item -> viewModel.onItemFocus(item) }
            },
            onPreloadAdjacentItem = preloadAdjacentItem,
            trailerPreviewUrls = viewModel.trailerPreviewUrls,
            trailerPreviewAudioUrls = viewModel.trailerPreviewAudioUrls,
            onRequestTrailerPreview = requestTrailerPreview,
            onSaveFocusState = saveModernFocusState,
            scrollToTopTrigger = scrollToTopTrigger,
            pendingFocusRailKeyFromHost = pendingNetflixFocusRailKey,
            onPendingFocusRailKeyConsumed = remember(viewModel) {
                { viewModel.consumePendingNetflixFocusRailKey() }
            },
            onRequestLazyCatalogLoad = remember(viewModel) {
                { catalogKey: String -> viewModel.requestLazyCatalogLoad(catalogKey) }
            },
            netflixFolderRails = netflixFolderRails,
            onEnsureFolderRails = remember(viewModel) {
                { requests -> viewModel.ensureNetflixFolderRails(requests) }
            },
            onRailScaleChange = remember(viewModel) {
                { orderKey, percent -> viewModel.updateActiveViewPackRailScale(orderKey, percent) }
            },
            onRailAsTextToggle = remember(viewModel) {
                { orderKey, enabled -> viewModel.updateHomeRailAsText(orderKey, enabled) }
            },
            onRailFocusedInfoToggle = remember(viewModel) {
                { orderKey, enabled -> viewModel.updateActiveViewPackRailPosterLabels(orderKey, enabled) }
            },
            onRailPosterGrowToggle = remember(viewModel) {
                { orderKey, enabled -> viewModel.updateActiveViewPackRailPosterGrow(orderKey, enabled) }
            },
            onRailTrailerToggle = remember(viewModel) {
                { orderKey, enabled -> viewModel.updateActiveViewPackRailTrailer(orderKey, enabled) }
            },
            onRailLockedToggle = remember(viewModel) {
                { orderKey, locked -> viewModel.updateHomeRailLocked(orderKey, locked) }
            },
            onRailMove = remember(viewModel) {
                { orderKey, orderKeys, direction ->
                    viewModel.moveHomeRail(orderKey, orderKeys, direction)
                }
            },
            onRailHide = remember(viewModel) {
                { railKey -> viewModel.hideHomeRail(railKey) }
            },
            onShowHiddenRails = remember(viewModel) {
                { viewModel.showHiddenHomeRails() }
            },
            onCollectionExpandedToggle = remember(viewModel) {
                { collectionId, expanded -> viewModel.setActiveViewPackCollectionExpanded(collectionId, expanded) }
            },
            selectedContentTab = remember(netflixContentTab) {
                runCatching { NetflixContentTab.valueOf(netflixContentTab) }
                    .getOrDefault(NetflixContentTab.HOME)
            },
            onContentTabChanged = remember(viewModel) {
                { tab: NetflixContentTab -> viewModel.setNetflixContentTab(tab.name) }
            }
        )
        return
    }
    ModernHomeContent(
        uiState = uiState,
        focusState = focusState,
        scrollToTopTrigger = scrollToTopTrigger,
        enrichingItemId = enrichingItemId,
        lastEnrichedPreview = lastEnrichedPreview,
        enrichedPreviews = enrichedPreviews,
        failedEnrichmentIds = failedEnrichmentIds,
        trailerPreviewUrls = viewModel.trailerPreviewUrls,
        trailerPreviewAudioUrls = viewModel.trailerPreviewAudioUrls,
        onNavigateToDetail = onNavigateToDetail,
        onContinueWatchingClick = onContinueWatchingClick,
        onContinueWatchingStartFromBeginning = onContinueWatchingStartFromBeginning,
        onContinueWatchingPlayManually = onContinueWatchingPlayManually,
        showContinueWatchingManualPlayOption = showContinueWatchingManualPlayOption,
        onRequestTrailerPreview = requestTrailerPreview,
        onLoadMoreCatalog = loadMoreCatalog,
        onRemoveContinueWatching = removeContinueWatching,
        isCatalogItemWatched = isCatalogItemWatched,
        onCatalogItemLongPress = onCatalogItemLongPress,
        onNavigateToFolderDetail = onNavigateToFolderDetail,
        onItemFocus = remember(viewModel) {
            { item -> viewModel.onItemFocus(item) }
        },
        onPreloadAdjacentItem = preloadAdjacentItem,
        onSaveFocusState = saveModernFocusState,
        onRequestLazyCatalogLoad = remember(viewModel) {
            { catalogKey: String -> viewModel.requestLazyCatalogLoad(catalogKey) }
        }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomePosterOptionsDialog(
    title: String,
    isInLibrary: Boolean,
    isLibraryPending: Boolean,
    showManageLists: Boolean,
    isMovie: Boolean,
    isSeries: Boolean = false,
    isWatched: Boolean,
    isWatchedPending: Boolean,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onToggleLibrary: () -> Unit,
    onToggleWatched: () -> Unit
) {
    val primaryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        primaryFocusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = stringResource(R.string.home_poster_dialog_subtitle)
    ) {
        Button(
            onClick = onDetails,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(primaryFocusRequester),
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                contentColor = NuvioTheme.colors.TextPrimary
            )
        ) {
            Text(stringResource(R.string.cw_action_go_to_details))
        }

        Button(
            onClick = onToggleLibrary,
            enabled = !isLibraryPending,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                contentColor = NuvioTheme.colors.TextPrimary
            )
        ) {
            Text(
                if (showManageLists) {
                    stringResource(R.string.library_manage_lists)
                } else {
                    if (isInLibrary) {
                        stringResource(R.string.hero_remove_from_library)
                    } else {
                        stringResource(R.string.hero_add_to_library)
                    }
                }
            )
        }

        if (isMovie || isSeries) {
            Button(
                onClick = onToggleWatched,
                enabled = !isWatchedPending,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(
                    if (isWatched) {
                        stringResource(R.string.hero_mark_unwatched)
                    } else {
                        stringResource(R.string.hero_mark_watched)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeLibraryListPickerDialog(
    title: String,
    tabs: List<LibraryListTab>,
    membership: Map<String, Boolean>,
    isPending: Boolean,
    error: String?,
    onToggle: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        primaryFocusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = stringResource(R.string.detail_lists_subtitle),
        width = 500.dp
    ) {
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFB6B6)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tabs, key = { it.key }) { tab ->
                val selected = membership[tab.key] == true
                val titleText = if (selected) "\u2713 ${tab.title}" else tab.title
                Button(
                    onClick = { onToggle(tab.key) },
                    enabled = !isPending,
                    modifier = if (tab.key == tabs.firstOrNull()?.key) {
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(primaryFocusRequester)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = if (selected) NuvioTheme.colors.FocusBackground else NuvioTheme.colors.BackgroundCard,
                        contentColor = NuvioTheme.colors.TextPrimary
                    )
                ) {
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Divider(color = NuvioTheme.colors.Border, thickness = NuvioTheme.spacing.hairline)

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Button(
                onClick = onSave,
                enabled = !isPending,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(if (isPending) stringResource(R.string.action_saving) else stringResource(R.string.action_save))
            }
        }
    }
}

/** True when a catalog row has real titles, not shimmer placeholders. */
internal fun CatalogRow.hasRealCatalogItems(): Boolean =
    items.isNotEmpty() && items.none { it.id.startsWith(HOME_PLACEHOLDER_ITEM_PREFIX) }

/**
 * Enough real home content that first composition is worth hiding under the spinner.
 * Placeholder rows and Continue Watching alone do not count — those used to open
 * the gate while catalog rails were still building.
 */
internal fun HomeUiState.hasRealHomeRails(): Boolean {
    if (catalogRows.any { it.hasRealCatalogItems() }) return true
    return homeRows.any { row ->
        when (row) {
            is HomeRow.Catalog -> row.row.hasRealCatalogItems()
            is HomeRow.CollectionRow -> true
            is HomeRow.PlaceholderCatalog -> false
        }
    }
}
