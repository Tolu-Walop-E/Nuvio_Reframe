package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMpvStartupWatchdogPolicyTest {

    @Test
    fun `keeps waiting before no-timeline timeout`() {
        val decision = PlayerMpvStartupWatchdogPolicy.evaluate(
            PlayerMpvStartupWatchdogPolicy.Input(
                hasRenderedFirstFrame = false,
                hasFileLoaded = false,
                durationMs = 0L,
                positionMs = 0L,
                isPausedForCache = false,
                elapsedSinceStartMs = 5_000L,
            )
        )
        assertEquals(PlayerMpvStartupWatchdogPolicy.Decision.KeepWaiting, decision)
    }

    @Test
    fun `fails when timeline never appears`() {
        val decision = PlayerMpvStartupWatchdogPolicy.evaluate(
            PlayerMpvStartupWatchdogPolicy.Input(
                hasRenderedFirstFrame = false,
                hasFileLoaded = false,
                durationMs = 0L,
                positionMs = 0L,
                isPausedForCache = false,
                elapsedSinceStartMs = 20_000L,
            )
        )
        assertEquals(PlayerMpvStartupWatchdogPolicy.Decision.FailStartup, decision)
    }

    @Test
    fun `keeps waiting while torrent is paused for cache after load`() {
        val decision = PlayerMpvStartupWatchdogPolicy.evaluate(
            PlayerMpvStartupWatchdogPolicy.Input(
                hasRenderedFirstFrame = false,
                hasFileLoaded = true,
                durationMs = 0L,
                positionMs = 0L,
                isPausedForCache = true,
                elapsedSinceStartMs = 30_000L,
            )
        )
        assertEquals(PlayerMpvStartupWatchdogPolicy.Decision.KeepWaiting, decision)
    }

    @Test
    fun `hard timeout fails even after file loaded`() {
        val decision = PlayerMpvStartupWatchdogPolicy.evaluate(
            PlayerMpvStartupWatchdogPolicy.Input(
                hasRenderedFirstFrame = false,
                hasFileLoaded = true,
                durationMs = 1_000L,
                positionMs = 0L,
                isPausedForCache = false,
                elapsedSinceStartMs = 45_000L,
            )
        )
        assertEquals(PlayerMpvStartupWatchdogPolicy.Decision.FailStartup, decision)
    }

    @Test
    fun `keeps waiting after first frame`() {
        val decision = PlayerMpvStartupWatchdogPolicy.evaluate(
            PlayerMpvStartupWatchdogPolicy.Input(
                hasRenderedFirstFrame = true,
                hasFileLoaded = false,
                durationMs = 0L,
                positionMs = 0L,
                isPausedForCache = false,
                elapsedSinceStartMs = 60_000L,
            )
        )
        assertEquals(PlayerMpvStartupWatchdogPolicy.Decision.KeepWaiting, decision)
    }
}
