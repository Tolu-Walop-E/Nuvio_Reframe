package com.nuvio.tv.ui.screens.player

import androidx.media3.common.Player
import com.nuvio.tv.core.tracking.TrackingScrobbleAction

internal fun trackingActionForNonPlayingState(
    playbackState: Int,
    playWhenReady: Boolean = false,
    userPausedManually: Boolean = false
): TrackingScrobbleAction? {
    // Skip-intro / live seek reports isPlaying=false while playWhenReady stays true.
    // Treat that as a stall, not a pause — pause+start scrobble during the jump
    // also GC-stalls the Shield audio thread.
    if (
        playWhenReady &&
        !userPausedManually &&
        playbackState != Player.STATE_ENDED &&
        playbackState != Player.STATE_IDLE
    ) {
        return null
    }
    return when (playbackState) {
        Player.STATE_BUFFERING -> null
        Player.STATE_ENDED, Player.STATE_IDLE -> TrackingScrobbleAction.STOP
        else -> TrackingScrobbleAction.PAUSE
    }
}

internal fun shouldSendPauseScrobble(
    hasActiveScrobble: Boolean,
    progressPercent: Float
): Boolean = hasActiveScrobble && progressPercent in 0f..100f

internal fun shouldSendStopScrobble(
    hasActiveScrobble: Boolean,
    progressPercent: Float
): Boolean = hasActiveScrobble || progressPercent >= 80f
