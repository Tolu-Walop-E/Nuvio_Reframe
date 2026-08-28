package com.nuvio.tv.ui.screens.player

import android.util.Log
import com.nuvio.tv.core.tracking.TrackingMediaReference
import com.nuvio.tv.core.tracking.buildTrackingMediaReference
import com.nuvio.tv.data.simkl.enrichMediaReference
import com.nuvio.tv.domain.model.WatchProgress
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * After a movie or season/series finale ends (or the user exits at ≥90%),
 * optionally show a rate prompt. Mid-season episodes with a next episode never enter.
 *
 * Important: the prompt must be armed in the **same** UI state update as
 * [PlayerUiState.playbackEnded], otherwise PlayerScreen exits before the card appears.
 */
internal fun PlayerRuntimeController.buildRatePromptModeOrNull(): PostPlayMode.RatePrompt? {
    if (!rateAfterWatchingEnabledSetting) return null
    val state = _uiState.value
    if (state.nextEpisode != null || nextEpisodeVideo != null) return null
    (state.postPlayMode as? PostPlayMode.RatePrompt)?.let { return it }

    val media = buildRatingMediaReference() ?: return null
    val title = (contentName ?: title).orEmpty().ifBlank { media.title.orEmpty() }
    if (title.isBlank()) return null

    return PostPlayMode.RatePrompt(
        title = title,
        artworkUrl = backdrop ?: poster,
        subtitle = ratePromptSubtitle(
            contentType = contentType,
            season = currentSeason,
            episode = currentEpisode,
        ),
        selectedRating = 8,
    )
}

/**
 * Synchronously arms the rate prompt when eligible. Returns true if the prompt
 * is (or was already) showing so callers can block exit.
 */
internal fun PlayerRuntimeController.armRatePromptSyncIfEligible(
    markPlaybackEnded: Boolean = false,
): Boolean {
    val existing = _uiState.value.postPlayMode as? PostPlayMode.RatePrompt
    if (existing != null) {
        if (markPlaybackEnded && !_uiState.value.playbackEnded) {
            _uiState.update { it.copy(playbackEnded = true, showControls = false) }
        }
        return true
    }
    val prompt = buildRatePromptModeOrNull() ?: return false
    _uiState.update {
        it.copy(
            postPlayMode = prompt,
            playbackEnded = if (markPlaybackEnded) true else it.playbackEnded,
            showControls = false,
            suppressPostPlayRecommendations = false,
        )
    }
    pauseForRatePrompt()
    scheduleRatePromptSkipIfAlreadyHandled()
    return true
}

/**
 * Used when [playbackEnded] flips true: attach a rate prompt in the same update
 * so PlayerScreen cannot exit before the card is visible.
 */
internal fun PlayerRuntimeController.ratePromptForNaturalEndOrNull(
    naturalEnded: Boolean,
    wasEnded: Boolean,
): PostPlayMode.RatePrompt? {
    if (!naturalEnded || wasEnded) return null
    return buildRatePromptModeOrNull()
}

internal fun PlayerRuntimeController.onRatePromptArmedFromNaturalEnd() {
    pauseForRatePrompt()
    scheduleRatePromptSkipIfAlreadyHandled()
}

/**
 * Back / exit while progress is at the completion threshold (≥90%).
 * Returns true when the rate card is shown and exit should be deferred.
 */
internal fun PlayerRuntimeController.requestRatePromptBeforeExit(
    positionMs: Long,
    durationMs: Long,
): Boolean {
    if (durationMs <= 0L) return false
    val ratio = positionMs.toFloat() / durationMs.toFloat()
    if (ratio < WatchProgress.COMPLETED_THRESHOLD) return false
    if (!armRatePromptSyncIfEligible(markPlaybackEnded = false)) return false
    // Keep player paused under the card; progress is already saved periodically.
    return true
}

internal fun PlayerRuntimeController.onRatePromptSelect(rating: Int) {
    val score = rating.coerceIn(1, 10)
    _uiState.update { state ->
        val mode = state.postPlayMode as? PostPlayMode.RatePrompt ?: return
        state.copy(postPlayMode = mode.copy(selectedRating = score))
    }
}

internal fun PlayerRuntimeController.onRatePromptSubmit() {
    val mode = _uiState.value.postPlayMode as? PostPlayMode.RatePrompt ?: return
    val rating = mode.selectedRating.coerceIn(1, 10)
    val media = buildRatingMediaReference()
    // Recs should follow the rate page immediately; Simkl can catch up in the background.
    finishRatePrompt(allowRecommendations = true)
    if (media == null) return
    scope.launch {
        val mediaKey = media.stableKey
        runCatching { userRatingsDataStore.saveRating(mediaKey, rating, syncedToSimkl = false) }
        val authenticated = runCatching {
            simklAuthRepository.state.value.isAuthenticated
        }.getOrDefault(false)
        if (authenticated) {
            val enriched = runCatching {
                simklSyncRepository.state.value.snapshot.enrichMediaReference(media)
            }.getOrDefault(media)
            val synced = runCatching {
                simklMutationService.rate(enriched, rating)
                true
            }.getOrElse { error ->
                Log.w(PlayerRuntimeController.TAG, "Simkl rating failed: ${error.message}")
                false
            }
            if (synced) {
                runCatching { userRatingsDataStore.markSynced(mediaKey) }
            }
        }
    }
}

internal fun PlayerRuntimeController.onRatePromptSkip() {
    finishRatePrompt(allowRecommendations = false)
    scope.launch {
        buildRatingMediaReference()?.stableKey?.let { key ->
            runCatching { userRatingsDataStore.markDismissed(key) }
        }
    }
}

private fun PlayerRuntimeController.finishRatePrompt(allowRecommendations: Boolean) {
    _uiState.update {
        it.copy(
            postPlayMode = null,
            playbackEnded = true,
            pendingExitReason = null,
            suppressPostPlayRecommendations = !allowRecommendations,
        )
    }
}

private fun PlayerRuntimeController.scheduleRatePromptSkipIfAlreadyHandled() {
    val mediaKey = buildRatingMediaReference()?.stableKey ?: return
    scope.launch {
        val entry = runCatching { userRatingsDataStore.get(mediaKey) }.getOrNull() ?: return@launch
        if (!entry.shouldSkipPrompt) return@launch
        if (_uiState.value.postPlayMode is PostPlayMode.RatePrompt) {
            finishRatePrompt(allowRecommendations = entry.rating != null)
        }
    }
}

private fun PlayerRuntimeController.pauseForRatePrompt() {
    runCatching {
        if (isUsingMpvEngine()) {
            mpvView?.setPaused(true)
        } else {
            _exoPlayer?.playWhenReady = false
            _exoPlayer?.pause()
        }
    }
    userPausedManually = true
    _uiState.update { it.copy(isPlaying = false) }
}

internal fun PlayerRuntimeController.buildRatingMediaReference(): TrackingMediaReference? {
    val rawContentId = contentId ?: return null
    val reference = buildTrackingMediaReference(
        contentType = contentType ?: "movie",
        parentMetaId = rawContentId,
        videoId = currentVideoId,
        title = contentName ?: title,
        releaseInfo = year,
        seasonNumber = currentSeason,
        episodeNumber = currentEpisode,
        episodeTitle = currentEpisodeTitle
    )
    // Finales rate the parent show/anime; movies rate the movie.
    val parent = reference.copy(episode = null, posterUrl = poster ?: backdrop)
    return parent.takeIf { it.hasResolvableIdentity }
}

internal fun ratePromptSubtitle(
    contentType: String?,
    season: Int?,
    episode: Int?,
): String? {
    val normalized = contentType?.lowercase()
    if (normalized !in listOf("series", "tv", "anime", "other")) return null
    return when {
        season != null && episode != null -> "Season $season · Episode $episode"
        season != null -> "Season $season finale"
        else -> "Series finale"
    }
}
