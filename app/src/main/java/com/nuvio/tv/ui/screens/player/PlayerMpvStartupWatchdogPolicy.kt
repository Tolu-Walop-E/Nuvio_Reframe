package com.nuvio.tv.ui.screens.player

/**
 * Pure MPV startup decisions so a dead/idle libmpv load cannot leave the UI on
 * "Starting stream…" forever when time-pos/duration never become available.
 */
internal object PlayerMpvStartupWatchdogPolicy {

    const val NO_TIMELINE_TIMEOUT_MS = 20_000L
    const val HARD_TIMEOUT_MS = 45_000L
    const val POLL_INTERVAL_MS = 1_000L

    data class Input(
        val hasRenderedFirstFrame: Boolean,
        val hasFileLoaded: Boolean,
        val durationMs: Long,
        val positionMs: Long,
        val isPausedForCache: Boolean,
        val elapsedSinceStartMs: Long,
        val noTimelineTimeoutMs: Long = NO_TIMELINE_TIMEOUT_MS,
        val hardTimeoutMs: Long = HARD_TIMEOUT_MS,
    )

    sealed class Decision {
        data object KeepWaiting : Decision()
        data object FailStartup : Decision()
    }

    fun evaluate(input: Input): Decision {
        if (input.hasRenderedFirstFrame) {
            return Decision.KeepWaiting
        }
        if (input.elapsedSinceStartMs >= input.hardTimeoutMs) {
            return Decision.FailStartup
        }
        val hasTimeline =
            input.hasFileLoaded ||
                input.durationMs > 0L ||
                input.positionMs > 0L ||
                input.isPausedForCache
        if (!hasTimeline && input.elapsedSinceStartMs >= input.noTimelineTimeoutMs) {
            return Decision.FailStartup
        }
        return Decision.KeepWaiting
    }
}
