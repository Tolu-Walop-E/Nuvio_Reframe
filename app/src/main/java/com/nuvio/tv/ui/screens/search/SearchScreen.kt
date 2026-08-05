package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.screens.home.HeroBackdropState

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.focusGroup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.ui.util.recompositionHighlighter
import com.nuvio.tv.ui.util.dpadRepeatThrottle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.nuvio.tv.ui.util.RtlKeyUtils
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.CatalogRowSection
import com.nuvio.tv.ui.components.SearchSkeletonRow
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.ErrorState
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.stableKey
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.nuvio.tv.R

/** Skeleton rows shown while a search is pending, matching the two mobile renders. */
private const val SEARCH_SKELETON_ROW_COUNT = 2

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToSeeAll: (catalogId: String, addonId: String, type: String) -> Unit = { _, _, _ -> },
    onOpenDiscover: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val watchedMovieIds by viewModel.watchedMovieIds.collectAsState()
    val watchedSeriesIds by viewModel.watchedSeriesIds.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val strVoiceNoSpeech = stringResource(R.string.search_voice_no_speech)
    val strVoiceMicPermission = stringResource(R.string.search_voice_mic_permission)
    val strVoiceFailed = stringResource(R.string.search_voice_failed)
    val strVoiceUnavailable = stringResource(R.string.search_voice_unavailable)
    val voiceFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val discoverFirstItemFocusRequester = remember { FocusRequester() }
    val recentClearHistoryFocusRequester = remember { FocusRequester() }
    var isSearchFieldFocused by remember { mutableStateOf(false) }
    var isRecentSearchSectionFocused by remember { mutableStateOf(false) }
    var focusResults by remember { mutableStateOf(false) }
    var pendingFocusMoveToResultsQuery by remember { mutableStateOf<String?>(null) }
    var pendingFocusMoveSawSearching by remember { mutableStateOf(false) }
    var pendingFocusMoveHadExistingSearchRows by remember { mutableStateOf(false) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceRmsLevel by remember { mutableStateOf(0f) }
    var discoverFocusedItemIndex by rememberSaveable { mutableStateOf(0) }
    var restoreDiscoverFocus by rememberSaveable { mutableStateOf(false) }
    var pendingDiscoverRestoreOnResume by rememberSaveable { mutableStateOf(false) }
    val restoringSearchFocus = remember { mutableStateOf(viewModel.hasSavedSearchFocus) }
    val didRestoreSearchFocus = remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val onVoiceQueryResultState = rememberUpdatedState<(String) -> Unit> { recognized ->
        if (recognized.isNotBlank()) {
            viewModel.onEvent(SearchEvent.QueryChanged(recognized))
            viewModel.onEvent(SearchEvent.SubmitSearch)
            focusResults = false
            pendingFocusMoveToResultsQuery = recognized
            pendingFocusMoveSawSearching = false
            pendingFocusMoveHadExistingSearchRows =
                uiState.submittedQuery.trim().length >= MIN_SEARCH_QUERY_LENGTH &&
                    uiState.catalogRows.any { it.items.isNotEmpty() }
        } else {
            Toast.makeText(context, strVoiceNoSpeech, Toast.LENGTH_SHORT).show()
        }
    }
    val isVoiceSearchAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    val speechRecognizer = remember(context, isVoiceSearchAvailable) {
        if (isVoiceSearchAvailable) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }
    val buildRecognizeIntent: () -> Intent = {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }
    val hasRecordAudioPermission by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var recordAudioPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestAudioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        recordAudioPermissionGranted = granted
        if (granted) {
            isVoiceListening = true
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.startListening(buildRecognizeIntent())
            }.onFailure {
                isVoiceListening = false
                Toast.makeText(context, strVoiceUnavailable, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, strVoiceMicPermission, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(hasRecordAudioPermission) {
        recordAudioPermissionGranted = hasRecordAudioPermission
    }
    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                // Normalize RMS dB to 0..1 range. Typical values: -2 (silence) to 10 (loud).
                voiceRmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                isVoiceListening = false
                voiceRmsLevel = 0f
                Log.w("SearchScreen", "Voice recognition error: $error")
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        Toast.makeText(context, strVoiceNoSpeech, Toast.LENGTH_SHORT).show()
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> Unit
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        Toast.makeText(context, strVoiceMicPermission, Toast.LENGTH_SHORT).show()
                    else ->
                        Toast.makeText(context, strVoiceFailed, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResults(results: Bundle?) {
                isVoiceListening = false
                voiceRmsLevel = 0f
                val recognized = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                onVoiceQueryResultState.value(recognized)
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
        }

        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.destroy()
        }
    }
    val topInputFocusRequester = remember(isVoiceSearchAvailable) {
        if (isVoiceSearchAvailable) voiceFocusRequester else searchFocusRequester
    }
    val launchVoiceSearch: () -> Unit = {
        if (!isVoiceSearchAvailable || speechRecognizer == null) {
            Toast.makeText(context, strVoiceUnavailable, Toast.LENGTH_SHORT).show()
        } else if (!recordAudioPermissionGranted) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            isVoiceListening = true
            runCatching {
                speechRecognizer.cancel()
                speechRecognizer.startListening(buildRecognizeIntent())
            }.onFailure {
                isVoiceListening = false
                Toast.makeText(context, strVoiceUnavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val posterCardStyle = remember(uiState.posterCardWidthDp, uiState.posterCardCornerRadiusDp) {
        val computedHeightDp = (uiState.posterCardWidthDp * 1.5f).roundToInt()
        PosterCardStyle(
            width = uiState.posterCardWidthDp.dp,
            height = computedHeightDp.dp,
            cornerRadius = uiState.posterCardCornerRadiusDp.dp,
            focusedBorderWidth = PosterCardDefaults.Style.focusedBorderWidth,
            focusedScale = PosterCardDefaults.Style.focusedScale
        )
    }

    val trimmedQuery = remember(uiState.query) { uiState.query.trim() }
    val trimmedSubmittedQuery = remember(uiState.submittedQuery) { uiState.submittedQuery.trim() }

    // Stable per-row state maps — mirrors ClassicHomeContent pattern so
    // CatalogRowSection keeps focus when placeholder→real data transitions.
    val searchRowStates = remember { mutableMapOf<String, LazyListState>() }
    val searchRowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val searchRowEntryFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val searchRowFocusedItemIndex = remember { mutableMapOf<String, Int>() }
    var lastFocusedRowKey by remember { mutableStateOf(viewModel.savedFocusRowKey) }

    // Clean up stale keys when the catalog rows change.
    val visibleRowKeys = remember(uiState.catalogRows) {
        uiState.catalogRows.mapTo(mutableSetOf()) {
            it.stableKey()
        }
    }
    // Stable list of non-empty catalog rows — mirrors ClassicHomeContent's
    // visibleHomeRows pattern so the LazyColumn receives a remember'd list.
    var resultTypeFilter by rememberSaveable { mutableStateOf(SEARCH_FILTER_ALL) }
    val movieCatalogRows = remember(uiState.catalogRows) {
        uiState.catalogRows.mapNotNull { it.filteredByMediaKind(SearchMediaKind.MOVIE) }
    }
    val seriesCatalogRows = remember(uiState.catalogRows) {
        uiState.catalogRows.mapNotNull { it.filteredByMediaKind(SearchMediaKind.SERIES) }
    }
    val visibleCatalogRows = remember(resultTypeFilter, movieCatalogRows, seriesCatalogRows) {
        when (resultTypeFilter) {
            SEARCH_FILTER_MOVIES -> movieCatalogRows
            SEARCH_FILTER_SERIES -> seriesCatalogRows
            else -> movieCatalogRows + seriesCatalogRows
        }
    }
    val netflixChrome = com.nuvio.tv.ui.screens.home.netflix.NetflixHomeFeature.ENABLED
    val searchContentInset = if (netflixChrome) {
        com.nuvio.tv.ui.screens.home.netflix.NetflixHomeTokens.PageHorizontalPadding
    } else {
        52.dp
    }
    LaunchedEffect(visibleRowKeys) {
        searchRowStates.keys.retainAll(visibleRowKeys)
        searchRowFocusRequesters.keys.retainAll(visibleRowKeys)
        searchRowEntryFocusRequesters.keys.retainAll(visibleRowKeys)
        searchRowFocusedItemIndex.keys.retainAll(visibleRowKeys)
    }

    val isDiscoverMode = remember(uiState.discoverLocation, trimmedQuery, trimmedSubmittedQuery) {
        shouldShowDiscoverInSearch(
            discoverLocation = uiState.discoverLocation,
            query = trimmedQuery,
            submittedQuery = trimmedSubmittedQuery
        )
    }
    LaunchedEffect(isDiscoverMode) {
        if (isDiscoverMode) viewModel.ensureDiscoverLoaded()
    }
    val hasPendingUnsubmittedQuery = remember(isDiscoverMode, trimmedQuery, trimmedSubmittedQuery) {
        !isDiscoverMode &&
            trimmedQuery.length >= MIN_SEARCH_QUERY_LENGTH &&
            trimmedQuery != trimmedSubmittedQuery
    }
    val showRecentSearches = remember(
        trimmedQuery,
        uiState.recentSearches
    ) {
        trimmedQuery.isEmpty() &&
            uiState.recentSearches.isNotEmpty()
    }
    val canMoveToResults = remember(
        isDiscoverMode,
        uiState.discoverResults,
        trimmedSubmittedQuery,
        uiState.catalogRows
    ) {
        if (isDiscoverMode) {
            false
        } else {
            trimmedSubmittedQuery.length >= MIN_SEARCH_QUERY_LENGTH &&
                uiState.catalogRows.any { it.items.isNotEmpty() }
        }
    }
    val submitCurrentQuery: (String) -> Unit = { submittedQuery ->
        viewModel.onEvent(SearchEvent.SubmitSearch)
        focusResults = false
        if (submittedQuery.length >= MIN_SEARCH_QUERY_LENGTH) {
            pendingFocusMoveToResultsQuery = submittedQuery
            pendingFocusMoveSawSearching = false
            pendingFocusMoveHadExistingSearchRows =
                trimmedSubmittedQuery.length >= MIN_SEARCH_QUERY_LENGTH &&
                    uiState.catalogRows.any { row -> row.items.isNotEmpty() }
        } else {
            pendingFocusMoveToResultsQuery = null
            pendingFocusMoveSawSearching = false
            pendingFocusMoveHadExistingSearchRows = false
        }
    }
    val handleQueryChanged: (String) -> Unit = { nextQuery ->
        val previousQuery = uiState.query.trim()
        val trimmedNextQuery = nextQuery.trim()
        val selectedSuggestion = trimmedNextQuery.length >= MIN_SEARCH_QUERY_LENGTH &&
            trimmedNextQuery != trimmedSubmittedQuery &&
            uiState.suggestions.any { it.equals(trimmedNextQuery, ignoreCase = true) } &&
            trimmedNextQuery.startsWith(previousQuery, ignoreCase = true) &&
            trimmedNextQuery.length - previousQuery.length > 1

        focusResults = false
        pendingFocusMoveToResultsQuery = null
        pendingFocusMoveSawSearching = false
        pendingFocusMoveHadExistingSearchRows = false
        viewModel.onEvent(SearchEvent.QueryChanged(nextQuery))
        if (selectedSuggestion) {
            submitCurrentQuery(trimmedNextQuery)
        }
    }
    val submitRecentSearch: (String) -> Unit = { recentQuery ->
        val trimmedRecentQuery = recentQuery.trim()
        if (trimmedRecentQuery.isNotEmpty()) {
            viewModel.onEvent(SearchEvent.QueryChanged(trimmedRecentQuery))
            submitCurrentQuery(trimmedRecentQuery)
        }
    }

    LaunchedEffect(focusResults, isDiscoverMode, uiState.discoverResults.size) {
        if (focusResults && isDiscoverMode && uiState.discoverResults.isNotEmpty()) {
            delay(100)
            runCatching { discoverFirstItemFocusRequester.requestFocus() }
            focusResults = false
            pendingFocusMoveToResultsQuery = null
            pendingFocusMoveSawSearching = false
            pendingFocusMoveHadExistingSearchRows = false
        }
    }

    LaunchedEffect(
        pendingFocusMoveToResultsQuery,
        pendingFocusMoveSawSearching,
        pendingFocusMoveHadExistingSearchRows,
        uiState.isSearching,
        uiState.submittedQuery,
        canMoveToResults,
        isDiscoverMode
    ) {
        val pendingQuery = pendingFocusMoveToResultsQuery ?: return@LaunchedEffect
        val currentSubmittedQuery = uiState.submittedQuery.trim()
        if (currentSubmittedQuery != pendingQuery) return@LaunchedEffect

        if (uiState.isSearching) {
            pendingFocusMoveSawSearching = true
            return@LaunchedEffect
        }

        val shouldRequireSeenSearching = pendingFocusMoveHadExistingSearchRows
        if ((shouldRequireSeenSearching && !pendingFocusMoveSawSearching) || !canMoveToResults) {
            return@LaunchedEffect
        }

        if (isDiscoverMode) {
            focusResults = true
        } else {
            // Use explicit first-item focus for deterministic landing on row 1 / column 1.
            delay(80)
            focusResults = true
        }
        pendingFocusMoveToResultsQuery = null
        pendingFocusMoveSawSearching = false
        pendingFocusMoveHadExistingSearchRows = false
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasSavedSearchFocus) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        runCatching { topInputFocusRequester.requestFocus() }
    }

    // Push search suggestions to the native keyboard suggestion bar
    LaunchedEffect(uiState.suggestions) {
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return@LaunchedEffect
        val completions = uiState.suggestions.mapIndexed { index, name ->
            CompletionInfo(index.toLong(), index, name)
        }.toTypedArray()
        imm.displayCompletions(view, completions)
    }

    var isScreenActive by remember { mutableStateOf(true) }
    val latestPendingDiscoverRestore by rememberUpdatedState(pendingDiscoverRestoreOnResume)
    val latestShouldKeepSearchFocus by rememberUpdatedState(
        focusResults || uiState.isSearching || isVoiceListening
    )
    val latestVoiceSearchAvailable by rememberUpdatedState(isVoiceSearchAvailable)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isScreenActive = true
                if (latestPendingDiscoverRestore) {
                    restoreDiscoverFocus = true
                    pendingDiscoverRestoreOnResume = false
                } else if (viewModel.hasSavedSearchFocus || didRestoreSearchFocus.value) {
                    // Returning from details — don't steal focus, CatalogRowSection
                    // already restored it or will restore it via focusedItemIndex.
                    didRestoreSearchFocus.value = false
                } else if (!latestShouldKeepSearchFocus) {
                    coroutineScope.launch {
                        repeat(2) { withFrameNanos { } }
                        runCatching {
                            if (latestVoiceSearchAvailable) {
                                voiceFocusRequester.requestFocus()
                            } else {
                                searchFocusRequester.requestFocus()
                            }
                        }
                    }
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isScreenActive = false
                keyboardController?.hide()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (netflixChrome) Color.Transparent else Color(0xFF031018)),
        contentAlignment = Alignment.TopCenter
    ) {
        if (!netflixChrome) {
            AsyncImage(
                model = HeroBackdropState.lastDisplayedUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.18f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xEE062638),
                                Color(0xF4051018),
                                Color(0xFA020407)
                            )
                        )
                    )
            )
        } else {
            // Netflix shell already provides poster-blur chrome + top nav.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f))
            )
        }
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .recompositionHighlighter()
                .dpadRepeatThrottle(),
            state = listState,
            contentPadding = PaddingValues(
                top = if (isDiscoverMode) 18.dp else 22.dp,
                bottom = NuvioTheme.spacing.lg
            ),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
        ) {
            item(key = "search_input") {
                SearchInputField(
                    query = uiState.query,
                    canMoveToResults = canMoveToResults,
                    voiceFocusRequester = if (isVoiceSearchAvailable) voiceFocusRequester else null,
                    searchFocusRequester = searchFocusRequester,
                    onSearchFieldFocusChanged = { focused -> isSearchFieldFocused = focused },
                    onQueryChanged = handleQueryChanged,
                    onSubmit = {
                        submitCurrentQuery(uiState.query.trim())
                    },
                    showVoiceSearch = isVoiceSearchAvailable,
                    isVoiceListening = isVoiceListening,
                    voiceRmsLevel = voiceRmsLevel,
                    onVoiceSearch = launchVoiceSearch,
                    onMoveToResults = { focusResults = true },
                    onOpenDiscover = onOpenDiscover,
                    showDiscoverButton = uiState.discoverLocation == DiscoverLocation.IN_SEARCH,
                    keyboardController = keyboardController,
                    clearHistoryFocusRequester = if (showRecentSearches) recentClearHistoryFocusRequester else null,
                    isScreenActive = isScreenActive
                )
            }

            if (isDiscoverMode) {
                if (showRecentSearches) {
                    item(key = "recent_searches") {
                        RecentSearchesSection(
                            recentSearches = uiState.recentSearches,
                            onSearchSelected = submitRecentSearch,
                            onClearHistory = {
                                viewModel.onEvent(SearchEvent.ClearRecentSearches)
                            },
                            onSectionFocusChanged = { focused -> isRecentSearchSectionFocused = focused },
                            clearHistoryFocusRequester = recentClearHistoryFocusRequester,
                            modifier = Modifier.padding(horizontal = 52.dp)
                        )
                    }
                } else {
                    item(key = "search_start") {
                        EmptyScreenState(
                            title = stringResource(R.string.search_start_title),
                            subtitle = stringResource(R.string.search_start_subtitle),
                            icon = Icons.Default.Search
                        )
                    }
                }
            } else {
                // The "press Done to search" hint is gone: search now runs as you type, so the
                // instruction is wrong, and it was re-appearing on every keystroke. Neither the
                // mobile nor the desktop client shows an equivalent message.

                when {
                    trimmedSubmittedQuery.length < MIN_SEARCH_QUERY_LENGTH && !hasPendingUnsubmittedQuery -> {
                        item {
                            if (showRecentSearches) {
                                RecentSearchesSection(
                                    recentSearches = uiState.recentSearches,
                                    onSearchSelected = submitRecentSearch,
                                    onClearHistory = {
                                        viewModel.onEvent(SearchEvent.ClearRecentSearches)
                                    },
                                    onSectionFocusChanged = { focused ->
                                        isRecentSearchSectionFocused = focused
                                    },
                                    clearHistoryFocusRequester = recentClearHistoryFocusRequester,
                                    modifier = Modifier.padding(horizontal = 52.dp)
                                )
                            } else {
                                EmptyScreenState(
                                    title = stringResource(R.string.search_start_title),
                                    subtitle = if (uiState.discoverLocation == DiscoverLocation.OFF) {
                                        stringResource(R.string.search_start_subtitle_no_discover)
                                    } else {
                                        stringResource(R.string.search_start_subtitle)
                                    },
                                    icon = Icons.Default.Search
                                )
                            }
                        }
                    }

                    // Nothing to show yet, either still waiting on the debounce or on the first
                    // responses. Mobile renders skeleton rows for both, so this does too. Unlike
                    // mobile it only applies with an empty screen: mobile swaps results out for
                    // skeletons on every keystroke, which on a remote reads as flicker because each
                    // letter outlasts the debounce, so existing results are kept instead.
                    (hasPendingUnsubmittedQuery || uiState.isSearching) && visibleCatalogRows.isEmpty() -> {
                        items(SEARCH_SKELETON_ROW_COUNT) {
                            SearchSkeletonRow(
                                posterCardStyle = posterCardStyle,
                                showAddonName = uiState.catalogAddonNameEnabled,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                        }
                    }

                    uiState.error != null && uiState.catalogRows.isEmpty() -> {
                        item {
                            ErrorState(
                                message = uiState.error ?: stringResource(R.string.search_error_failed),
                                onRetry = { viewModel.onEvent(SearchEvent.Retry) }
                            )
                        }
                    }

                    !uiState.isSearching && movieCatalogRows.isEmpty() && seriesCatalogRows.isEmpty() -> {
                        item {
                            EmptyScreenState(
                                title = stringResource(R.string.search_no_results_title),
                                subtitle = stringResource(R.string.search_no_results_subtitle),
                                icon = Icons.Default.Search
                            )
                        }
                    }

                    else -> {
                        item(key = "search_type_filters") {
                            SearchTypeFilterRow(
                                selected = resultTypeFilter,
                                movieCount = movieCatalogRows.sumOf { it.items.size },
                                seriesCount = seriesCatalogRows.sumOf { it.items.size },
                                onSelected = { resultTypeFilter = it },
                                modifier = Modifier.padding(horizontal = searchContentInset)
                            )
                        }

                        if (visibleCatalogRows.isEmpty()) {
                            item(key = "search_filter_empty") {
                                EmptyScreenState(
                                    title = stringResource(R.string.search_no_results_title),
                                    subtitle = when (resultTypeFilter) {
                                        SEARCH_FILTER_MOVIES -> stringResource(R.string.search_filter_movies)
                                        SEARCH_FILTER_SERIES -> stringResource(R.string.search_filter_series)
                                        else -> stringResource(R.string.search_no_results_subtitle)
                                    },
                                    icon = Icons.Default.Search
                                )
                            }
                        } else {
                            if (resultTypeFilter == SEARCH_FILTER_ALL || resultTypeFilter == SEARCH_FILTER_MOVIES) {
                                if (movieCatalogRows.isNotEmpty() && resultTypeFilter == SEARCH_FILTER_ALL) {
                                    item(key = "search_section_movies") {
                                        SearchSectionHeader(
                                            title = stringResource(R.string.search_section_movies),
                                            modifier = Modifier.padding(horizontal = searchContentInset)
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = if (resultTypeFilter == SEARCH_FILTER_ALL) {
                                        movieCatalogRows
                                    } else {
                                        visibleCatalogRows
                                    },
                                    key = { index, item -> "movie_${item.stableKey()}_$index" },
                                    contentType = { _, _ -> "catalog_row" }
                                ) { index, catalogRow ->
                                    SearchCatalogResultRow(
                                        catalogRow = catalogRow,
                                        index = index,
                                        uiState = uiState,
                                        watchedMovieIds = watchedMovieIds,
                                        watchedSeriesIds = watchedSeriesIds,
                                        restoringSearchFocus = restoringSearchFocus,
                                        didRestoreSearchFocus = didRestoreSearchFocus,
                                        focusResults = focusResults,
                                        onFocusResultsConsumed = { focusResults = false },
                                        pendingFocusMoveToResultsQuery = pendingFocusMoveToResultsQuery,
                                        onClearPendingFocusMove = { pendingFocusMoveToResultsQuery = null },
                                        searchRowStates = searchRowStates,
                                        searchRowFocusRequesters = searchRowFocusRequesters,
                                        searchRowEntryFocusRequesters = searchRowEntryFocusRequesters,
                                        searchRowFocusedItemIndex = searchRowFocusedItemIndex,
                                        viewModel = viewModel,
                                        onNavigateToDetail = onNavigateToDetail,
                                        onNavigateToSeeAll = onNavigateToSeeAll,
                                        onLastFocusedRowKey = { lastFocusedRowKey = it }
                                    )
                                }
                            }

                            if (resultTypeFilter == SEARCH_FILTER_ALL || resultTypeFilter == SEARCH_FILTER_SERIES) {
                                if (seriesCatalogRows.isNotEmpty() && resultTypeFilter == SEARCH_FILTER_ALL) {
                                    item(key = "search_section_series") {
                                        SearchSectionHeader(
                                            title = stringResource(R.string.search_section_series),
                                            modifier = Modifier.padding(horizontal = searchContentInset)
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = if (resultTypeFilter == SEARCH_FILTER_ALL) {
                                        seriesCatalogRows
                                    } else {
                                        visibleCatalogRows
                                    },
                                    key = { index, item -> "series_${item.stableKey()}_$index" },
                                    contentType = { _, _ -> "catalog_row" }
                                ) { index, catalogRow ->
                                    SearchCatalogResultRow(
                                        catalogRow = catalogRow,
                                        index = index,
                                        uiState = uiState,
                                        watchedMovieIds = watchedMovieIds,
                                        watchedSeriesIds = watchedSeriesIds,
                                        restoringSearchFocus = restoringSearchFocus,
                                        didRestoreSearchFocus = didRestoreSearchFocus,
                                        focusResults = focusResults,
                                        onFocusResultsConsumed = { focusResults = false },
                                        pendingFocusMoveToResultsQuery = pendingFocusMoveToResultsQuery,
                                        onClearPendingFocusMove = { pendingFocusMoveToResultsQuery = null },
                                        searchRowStates = searchRowStates,
                                        searchRowFocusRequesters = searchRowFocusRequesters,
                                        searchRowEntryFocusRequesters = searchRowEntryFocusRequesters,
                                        searchRowFocusedItemIndex = searchRowFocusedItemIndex,
                                        viewModel = viewModel,
                                        onNavigateToDetail = onNavigateToDetail,
                                        onNavigateToSeeAll = onNavigateToSeeAll,
                                        onLastFocusedRowKey = { lastFocusedRowKey = it }
                                    )
                                }
                            }
                        }

                        // Results are up but more catalogs are still answering, as on mobile.
                        if (uiState.isSearching || hasPendingUnsubmittedQuery) {
                            item(key = "search_loading_more") {
                                SearchSkeletonRow(
                                    posterCardStyle = posterCardStyle,
                                    showAddonName = uiState.catalogAddonNameEnabled,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val posterOptionsState by viewModel.posterOptions.state.collectAsState()
    com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost(
        state = posterOptionsState,
        controller = viewModel.posterOptions,
        onNavigateToDetail = { id, type, addonBaseUrl ->
            val clickedItem = uiState.catalogRows
                .flatMap { it.items }
                .firstOrNull { it.id == id }
                ?: uiState.discoverResults.firstOrNull { it.id == id }
            HeroBackdropState.update(clickedItem?.backdropUrl)
            onNavigateToDetail(id, type, addonBaseUrl)
        }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    onSearchSelected: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSectionFocusChanged: (Boolean) -> Unit,
    clearHistoryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .onFocusChanged { state ->
                onSectionFocusChanged(state.hasFocus || state.isFocused)
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.search_recent_title),
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary
            )
            Button(
                onClick = onClearHistory,
                modifier = Modifier.focusRequester(clearHistoryFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground,
                    focusedContentColor = NuvioTheme.colors.Primary
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
            ) {
                Text(text = stringResource(R.string.search_recent_clear))
            }
        }

        recentSearches.forEach { recentQuery ->
            Button(
                onClick = { onSearchSelected(recentQuery) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { keyEvent ->
                        val clearHistoryKey = RtlKeyUtils.getClearHistoryDpadKey(isRtl)
                        if (keyEvent.nativeKeyEvent.keyCode == clearHistoryKey) {
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                runCatching { clearHistoryFocusRequester.requestFocus() }
                            }
                            true
                        } else {
                            false
                        }
                    },
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground,
                    focusedContentColor = NuvioTheme.colors.Primary
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md))
            ) {
                Text(
                    text = recentQuery,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    canMoveToResults: Boolean,
    voiceFocusRequester: FocusRequester?,
    searchFocusRequester: FocusRequester,
    onSearchFieldFocusChanged: (Boolean) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    showVoiceSearch: Boolean,
    isVoiceListening: Boolean,
    voiceRmsLevel: Float,
    onVoiceSearch: () -> Unit,
    onMoveToResults: () -> Unit,
    onOpenDiscover: () -> Unit,
    showDiscoverButton: Boolean,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    clearHistoryFocusRequester: FocusRequester?,
    isScreenActive: Boolean = true
) {
    var isDiscoverButtonFocused by remember { mutableStateOf(false) }
    var isVoiceButtonFocused by remember { mutableStateOf(false) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDiscoverButton) {
            IconButton(
                onClick = onOpenDiscover,
                modifier = Modifier
                    .onFocusChanged { isDiscoverButtonFocused = it.isFocused }
                        .size(NuvioTheme.spacing.huge)
                        .border(
                            width = if (isDiscoverButtonFocused) NuvioTheme.spacing.xxs else NuvioTheme.spacing.hairline,
                            color = if (isDiscoverButtonFocused) Color.White else Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(50)
                        )
                        .background(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        )
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = stringResource(R.string.cd_open_discover),
                    tint = NuvioTheme.colors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
        }

        if (showVoiceSearch) {
            val themeAccent = NuvioTheme.colors.Secondary

            // Pulsating animation (constant rhythm while listening)
            val pulseTransition = rememberInfiniteTransition(label = "voicePulse")
            val pulseScale by pulseTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulseScale"
            )
            val pulseAlpha by pulseTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulseAlpha"
            )

            // RMS-based ring — smoothly follows mic input level
            val animatedRms by animateFloatAsState(
                targetValue = if (isVoiceListening) voiceRmsLevel else 0f,
                animationSpec = tween(100),
                label = "rmsRing"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp) // extra room for rings
            ) {
                // Layer 1: Pulsating ring (constant rhythm)
                if (isVoiceListening) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val radius = (size.minDimension / 2f) * pulseScale
                        drawCircle(
                            color = themeAccent.copy(alpha = pulseAlpha * 0.4f),
                            radius = radius
                        )
                    }
                }

                // Layer 2: RMS level ring (voice-reactive)
                if (isVoiceListening && animatedRms > 0.01f) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val rmsRadius = (size.minDimension / 2f) * (1f + animatedRms * 0.35f)
                        drawCircle(
                            color = themeAccent.copy(alpha = 0.25f + animatedRms * 0.25f),
                            radius = rmsRadius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 2.5f + animatedRms * 3f
                            )
                        )
                    }
                }

                // Layer 3: Actual button
                IconButton(
                    onClick = onVoiceSearch,
                    modifier = Modifier
                        .then(
                            if (voiceFocusRequester != null) {
                                Modifier.focusRequester(voiceFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .onFocusChanged { isVoiceButtonFocused = it.isFocused }
                        .size(NuvioTheme.spacing.huge)
                        .border(
                            width = if (isVoiceButtonFocused || isVoiceListening) NuvioTheme.spacing.xxs else NuvioTheme.spacing.hairline,
                            color = if (isVoiceListening) themeAccent else if (isVoiceButtonFocused) Color.White else Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(50)
                        )
                        .background(
                            color = if (isVoiceListening) themeAccent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.cd_voice_search),
                        tint = if (isVoiceListening) themeAccent else NuvioTheme.colors.TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .weight(1f)
                .focusRequester(searchFocusRequester)
                .focusProperties {
                    canFocus = isScreenActive
                }
                .onFocusChanged { focusState ->
                    onSearchFieldFocusChanged(focusState.isFocused)
                }
                .onPreviewKeyEvent { keyEvent ->
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                onSubmit()
                            }
                            return@onPreviewKeyEvent true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (canMoveToResults) {
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    onMoveToResults()
                                }
                                return@onPreviewKeyEvent true
                            }
                        }

                        else -> {
                            val clearHistoryKey = RtlKeyUtils.getClearHistoryDpadKey(isRtl)
                            if (keyEvent.nativeKeyEvent.keyCode == clearHistoryKey) {
                                if (clearHistoryFocusRequester != null) {
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        keyboardController?.hide()
                                        runCatching { clearHistoryFocusRequester.requestFocus() }
                                    }
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                    }
                    false
                },
            keyboardOptions = KeyboardOptions.Default.copy(
                 imeAction = ImeAction.Done,
                 autoCorrectEnabled = false
             ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onSubmit()
                    keyboardController?.hide()
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    color = Color.White.copy(alpha = 0.68f)
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                focusedIndicatorColor = Color.White.copy(alpha = 0.86f),
                unfocusedIndicatorColor = Color.White.copy(alpha = 0.30f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
            )
        )

        // Clear button, requested in review. Placed beside the field rather than as a trailing
        // icon so it is reachable with the D-pad, matching the voice button's treatment.
        if (query.isNotEmpty()) {
            var isClearButtonFocused by remember { mutableStateOf(false) }
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
            IconButton(
                onClick = { onQueryChanged("") },
                modifier = Modifier
                    .onFocusChanged { isClearButtonFocused = it.isFocused }
                    .size(NuvioTheme.spacing.huge)
                    .border(
                        width = if (isClearButtonFocused) NuvioTheme.spacing.xxs else NuvioTheme.spacing.hairline,
                        color = if (isClearButtonFocused) Color.White else Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50)
                    )
                    .background(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_clear_search),
                    tint = NuvioTheme.colors.TextPrimary
                )
            }
        }
    }
}

private const val SEARCH_FILTER_ALL = "all"
private const val SEARCH_FILTER_MOVIES = "movie"
private const val SEARCH_FILTER_SERIES = "series"

private enum class SearchMediaKind { MOVIE, SERIES }

private fun isSeriesType(type: String?): Boolean {
    val value = type.orEmpty()
    return value.equals("series", ignoreCase = true) || value.equals("tv", ignoreCase = true)
}

private fun CatalogRow.filteredByMediaKind(kind: SearchMediaKind): CatalogRow? {
    val filteredItems = items.filter { item ->
        val effectiveType = item.apiType.ifBlank { apiType }
        when (kind) {
            SearchMediaKind.MOVIE -> !isSeriesType(effectiveType)
            SearchMediaKind.SERIES -> isSeriesType(effectiveType)
        }
    }
    return copy(items = filteredItems).takeIf { it.items.isNotEmpty() }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        color = Color.White,
        style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchTypeFilterRow(
    selected: String,
    movieCount: Int,
    seriesCount: Int,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SearchTypeChip(
            label = stringResource(R.string.search_filter_all),
            selected = selected == SEARCH_FILTER_ALL,
            onClick = { onSelected(SEARCH_FILTER_ALL) }
        )
        SearchTypeChip(
            label = "${stringResource(R.string.search_filter_movies)} ($movieCount)",
            selected = selected == SEARCH_FILTER_MOVIES,
            onClick = { onSelected(SEARCH_FILTER_MOVIES) }
        )
        SearchTypeChip(
            label = "${stringResource(R.string.search_filter_series)} ($seriesCount)",
            selected = selected == SEARCH_FILTER_SERIES,
            onClick = { onSelected(SEARCH_FILTER_SERIES) }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        colors = ButtonDefaults.colors(
            containerColor = when {
                selected -> Color.White
                focused -> Color.White.copy(alpha = 0.22f)
                else -> Color.White.copy(alpha = 0.10f)
            },
            contentColor = if (selected) Color.Black else Color.White,
            focusedContainerColor = if (selected) Color.White else Color.White.copy(alpha = 0.28f),
            focusedContentColor = if (selected) Color.Black else Color.White
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(22.dp))
    ) {
        Text(text = label)
    }
}

@Composable
private fun SearchCatalogResultRow(
    catalogRow: CatalogRow,
    index: Int,
    uiState: SearchUiState,
    watchedMovieIds: Set<String>,
    watchedSeriesIds: Set<String>,
    restoringSearchFocus: androidx.compose.runtime.MutableState<Boolean>,
    didRestoreSearchFocus: androidx.compose.runtime.MutableState<Boolean>,
    focusResults: Boolean,
    onFocusResultsConsumed: () -> Unit,
    pendingFocusMoveToResultsQuery: String?,
    onClearPendingFocusMove: () -> Unit,
    searchRowStates: MutableMap<String, LazyListState>,
    searchRowFocusRequesters: MutableMap<String, FocusRequester>,
    searchRowEntryFocusRequesters: MutableMap<String, FocusRequester>,
    searchRowFocusedItemIndex: MutableMap<String, Int>,
    viewModel: SearchViewModel,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToSeeAll: (String, String, String) -> Unit,
    onLastFocusedRowKey: (String) -> Unit
) {
    val catalogKey = catalogRow.stableKey()
    val isPlaceholder = catalogRow.isLoading &&
        catalogRow.items.firstOrNull()?.id?.startsWith("__placeholder_") == true
    val hasEnoughForSeeAll = !isPlaceholder && catalogRow.items.size >= 15

    val listState = searchRowStates.getOrPut(catalogKey) {
        val saved = viewModel.savedRowScrollPositions[catalogKey]
        LazyListState(
            firstVisibleItemIndex = saved?.first ?: 0,
            firstVisibleItemScrollOffset = saved?.second ?: 0
        )
    }
    val rowFocusRequester = searchRowFocusRequesters.getOrPut(catalogKey) { FocusRequester() }
    val entryFocusRequester = searchRowEntryFocusRequesters.getOrPut(catalogKey) { FocusRequester() }

    CatalogRowSection(
        catalogRow = catalogRow,
        showSeeAll = hasEnoughForSeeAll,
        showPosterLabels = uiState.posterLabelsEnabled,
        showAddonName = uiState.catalogAddonNameEnabled,
        showCatalogTypeSuffix = true,
        enableRowFocusRestorer = true,
        rowFocusRequester = rowFocusRequester,
        entryFocusRequester = entryFocusRequester,
        listState = listState,
        restorerFocusedIndex = if (restoringSearchFocus.value && catalogKey == viewModel.savedFocusRowKey) {
            viewModel.savedFocusItemIndex
        } else {
            searchRowFocusedItemIndex[catalogKey] ?: -1
        },
        isItemWatched = { item ->
            val isSeries = isSeriesType(item.apiType)
            if (isSeries) item.id in watchedSeriesIds else item.id in watchedMovieIds
        },
        focusedItemIndex = when {
            restoringSearchFocus.value && catalogKey == viewModel.savedFocusRowKey ->
                viewModel.savedFocusItemIndex
            focusResults && index == 0 -> 0
            else -> -1
        },
        onItemFocused = { itemIndex ->
            if (focusResults) onFocusResultsConsumed()
            if (restoringSearchFocus.value) {
                restoringSearchFocus.value = false
                didRestoreSearchFocus.value = true
                viewModel.hasSavedSearchFocus = false
            }
            onClearPendingFocusMove()
            searchRowFocusedItemIndex[catalogKey] = itemIndex
            onLastFocusedRowKey(catalogKey)
        },
        onItemClick = { id, type, addonBaseUrl ->
            onLastFocusedRowKey(catalogKey)
            viewModel.savedFocusRowKey = catalogKey
            viewModel.savedFocusItemIndex = searchRowFocusedItemIndex[catalogKey] ?: 0
            viewModel.savedRowScrollPositions = searchRowStates.mapValues {
                it.value.firstVisibleItemIndex to it.value.firstVisibleItemScrollOffset
            }
            viewModel.hasSavedSearchFocus = true
            val clickedItem = catalogRow.items.firstOrNull { it.id == id }
            HeroBackdropState.update(clickedItem?.backdropUrl)
            onNavigateToDetail(id, type, addonBaseUrl)
        },
        onItemLongPress = { item, addonBaseUrl ->
            viewModel.posterOptions.show(item, addonBaseUrl)
        },
        onSeeAll = {
            onNavigateToSeeAll(
                catalogRow.catalogId,
                catalogRow.addonId,
                catalogRow.apiType
            )
        }
    )
}
