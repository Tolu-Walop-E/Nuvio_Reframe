package com.nuvio.tv.core.player

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.TrailerSettingsDataStore
import com.nuvio.tv.domain.model.TrailerMinResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Application-scoped singleton that holds a single ExoPlayer instance dedicated to
 * trailer/preview playback on the home screen.
 *
 * Creating and tearing down ExoPlayer for every poster focus is extremely expensive
 * (codec init, hardware decoder allocation). This pool keeps one instance alive and
 * reuses it across focus changes. The player is stopped and cleared between uses but
 * never released until the process dies or [release] is explicitly called.
 *
 * When the full-screen player needs hardware decoders, call [yield] to free
 * codec resources without destroying the instance. Call [reclaim] when returning to
 * the home screen to lazily rebuild if needed.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class TrailerPlayerPool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerSettingsDataStore: PlayerSettingsDataStore,
    private val trailerSettingsDataStore: TrailerSettingsDataStore
) {
    companion object {
        private const val TAG = "TrailerPlayerPool"
        private val DEFAULT_OWNER = Any()
    }

    private var _player: ExoPlayer? = null
    private var activeOwner: Any? = null
    private val yielded = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    // Trailer previews default to the safer allocator; avoid blocking cold/focus
    // paths on DataStore. Full-screen playback reads the setting separately.
    private val forceNativeAllocation = AtomicBoolean(false)

    private val minResolutionHeight = AtomicInteger(TrailerMinResolution.P720.height)
    private val minResolutionWidth = AtomicInteger(TrailerMinResolution.P720.width)
    private val capResolutionHeight = AtomicInteger(TrailerMinResolution.P720.capHeight)
    private val capResolutionWidth = AtomicInteger(TrailerMinResolution.P720.capWidth)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                forceNativeAllocation.set(playerSettingsDataStore.nuvioPerformanceModeEnabled.first())
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            trailerSettingsDataStore.settings
                .map { it.minResolution }
                .distinctUntilChanged()
                .collect { resolution ->
                    minResolutionWidth.set(resolution.width)
                    minResolutionHeight.set(resolution.height)
                    capResolutionWidth.set(resolution.capWidth)
                    capResolutionHeight.set(resolution.capHeight)
                    rebuildPlayer()
                }
        }
    }

    /**
     * Returns the shared trailer ExoPlayer, creating it lazily if needed.
     * Returns null only if [release] was called (process shutdown).
     *
     * [owner] claims exclusive use so a previously focused TrailerPlayer cannot
     * stop/clear media after focus has already moved to another surface.
     */
    fun acquire(owner: Any = DEFAULT_OWNER): ExoPlayer? {
        if (released.get()) return null
        activeOwner = owner
        return obtain()
    }

    /**
     * Returns the shared player without claiming ownership.
     *
     * Composition must never claim ownership: a stale card recomposing (late
     * trailer URL, size change) would steal the player from the focused card,
     * leaving that card with audio and no picture.
     */
    fun obtain(): ExoPlayer? {
        if (released.get()) return null
        if (yielded.get()) {
            // Reclaim was not called yet but someone wants the player — rebuild.
            reclaim()
        }
        return _player ?: createPlayer().also { _player = it }
    }

    fun isOwnedBy(owner: Any): Boolean = activeOwner === owner

    /** True when nobody currently holds the shared player. */
    fun isUnowned(): Boolean = activeOwner == null

    /**
     * Stops playback and clears media but keeps the instance alive for reuse.
     * Call this when the trailer is no longer visible (poster lost focus, screen change).
     * No-ops if [owner] no longer owns the pool (another surface took over).
     */
    fun stop(owner: Any = DEFAULT_OWNER) {
        if (activeOwner !== owner && activeOwner != null) return
        if (activeOwner === owner) {
            activeOwner = null
        }
        _player?.let { player ->
            runCatching {
                player.playWhenReady = false
                player.stop()
                player.clearMediaItems()
            }
        }
    }

    /**
     * Releases codec resources so the detail-screen player can claim hardware decoders.
     * The ExoPlayer instance is released here; [reclaim] will create a fresh one.
     */
    fun yield() {
        if (yielded.compareAndSet(false, true)) {
            Log.d(TAG, "Yielding trailer player for detail playback")
            activeOwner = null
            _player?.let { player ->
                runCatching { player.stop() }
                runCatching { player.clearMediaItems() }
                runCatching { player.release() }
            }
            _player = null
        }
    }

    /**
     * Re-creates the player after a [yield]. Safe to call multiple times.
     */
    fun reclaim() {
        if (released.get()) return
        if (yielded.compareAndSet(true, false)) {
            Log.d(TAG, "Reclaiming trailer player")
            // Player will be lazily created on next acquire()
        }
    }

    /**
     * Permanently releases the player. Called on process death / Application.onTerminate.
     */
    fun release() {
        if (released.compareAndSet(false, true)) {
            activeOwner = null
            _player?.let { player ->
                runCatching { player.stop() }
                runCatching { player.clearMediaItems() }
                runCatching { player.release() }
            }
            _player = null
        }
    }

    private fun rebuildPlayer() {
        if (released.get() || yielded.get()) return
        _player?.let { player ->
            runCatching { player.stop() }
            runCatching { player.clearMediaItems() }
            runCatching { player.release() }
        }
        _player = null
    }

    private fun createPlayer(): ExoPlayer {
        val forceNative = forceNativeAllocation.get()
        Log.d(TAG, "Creating shared trailer ExoPlayer instance with forceNativeAllocation = $forceNative")
        // Preview trailers should start quickly; full-screen playback uses its own player.
        val loadControlBuilder = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_000,
                /* maxBufferMs = */ 15_000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 750
            )
        if (forceNative) {
            val allocator = DefaultAllocator(
                /* trimOnReset = */ true,
                /* individualAllocationSize = */ 65536,
                /* initialAllocationCount = */ 0,
                /* forceNativeAllocation = */ true
            )
            loadControlBuilder.setAllocator(allocator)
        }
        val loadControl = loadControlBuilder.build()
        val minWidth = minResolutionWidth.get()
        val minHeight = minResolutionHeight.get()
        val maxWidth = capResolutionWidth.get()
        val maxHeight = capResolutionHeight.get()
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(maxWidth, maxHeight)
                    .setMinVideoSize(minWidth, minHeight)
                    .setExceedVideoConstraintsIfNecessary(false)
                    .setForceHighestSupportedBitrate(false)
            )
        }
        val initialBitrate = if (minHeight >= 1080) 8_000_000L else 4_000_000L
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(
                DefaultBandwidthMeter.Builder(context)
                    .setInitialBitrateEstimate(initialBitrate)
                    .build()
            )
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
}
