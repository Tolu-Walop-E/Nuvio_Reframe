package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState

/**
 * Picks a replacement stream when the current cache/debrid URL is dead.
 * Prefers the same torrent identity (re-resolve) or binge group before any other source.
 */
internal object PlayerFreshStreamLinkPolicy {

    data class Input(
        val streams: List<Stream>,
        val deadUrl: String,
        val preferredInfoHash: String?,
        val preferredFileIdx: Int?,
        val preferredBingeGroup: String?,
        val preferredAddonName: String?,
    )

    fun select(input: Input): Stream? {
        val candidates = input.streams.filter(::isRecoverableCandidate)
        if (candidates.isEmpty()) return null

        val deadUrl = input.deadUrl.trim()
        val preferredHash = input.preferredInfoHash?.trim()?.takeIf { it.isNotBlank() }
        val preferredBinge = input.preferredBingeGroup?.trim()?.takeIf { it.isNotBlank() }
        val preferredAddon = input.preferredAddonName?.trim()?.takeIf { it.isNotBlank() }

        if (preferredHash != null) {
            val hashMatches = candidates.filter { stream ->
                stream.getEffectiveInfoHash()?.equals(preferredHash, ignoreCase = true) == true &&
                    fileIdxCompatible(stream, input.preferredFileIdx)
            }
            hashMatches.firstOrNull { canReResolveOrDiffers(it, deadUrl) }
                ?.let { return stripStalePlayableUrl(it) }
        }

        if (preferredBinge != null) {
            candidates.firstOrNull { stream ->
                stream.behaviorHints?.bingeGroup == preferredBinge && differsFromDeadUrl(stream, deadUrl)
            }?.let { return stripStalePlayableUrl(it) }
        }

        if (preferredAddon != null) {
            candidates.firstOrNull { stream ->
                stream.addonName.equals(preferredAddon, ignoreCase = true) &&
                    differsFromDeadUrl(stream, deadUrl)
            }?.let { return stripStalePlayableUrl(it) }
        }

        return candidates.firstOrNull { differsFromDeadUrl(it, deadUrl) }?.let(::stripStalePlayableUrl)
    }

    private fun isRecoverableCandidate(stream: Stream): Boolean {
        if (stream.isExternal()) return false
        // During recovery, accept streams whose debrid cache status is still unknown /
        // checking — Continue Watching often fails before badges finish.
        if (stream.debridCacheStatus?.state == StreamDebridCacheState.NOT_CACHED) return false
        return stream.getStreamUrl() != null ||
            stream.isTorrent() ||
            stream.isDirectDebrid() ||
            !stream.getEffectiveInfoHash().isNullOrBlank() ||
            stream.clientResolve != null
    }

    private fun fileIdxCompatible(stream: Stream, preferredFileIdx: Int?): Boolean {
        if (preferredFileIdx == null) return true
        val streamIdx = stream.getEffectiveFileIdx() ?: return true
        return streamIdx == preferredFileIdx
    }

    private fun differsFromDeadUrl(stream: Stream, deadUrl: String): Boolean {
        if (deadUrl.isBlank()) return true
        val url = stream.getStreamUrl()
        return url.isNullOrBlank() || !url.equals(deadUrl, ignoreCase = true)
    }

    private fun canReResolveOrDiffers(stream: Stream, deadUrl: String): Boolean {
        if (stream.clientResolve != null || stream.isDirectDebrid() || stream.isTorrent()) return true
        if (!stream.getEffectiveInfoHash().isNullOrBlank()) return true
        return differsFromDeadUrl(stream, deadUrl)
    }

    private fun stripStalePlayableUrl(stream: Stream): Stream {
        // Force debrid/torrent identities through resolve again instead of reusing a dead HTTP URL.
        if (
            stream.clientResolve != null ||
            stream.isDirectDebrid() ||
            stream.isTorrent() ||
            !stream.getEffectiveInfoHash().isNullOrBlank()
        ) {
            return stream.copy(url = null, externalUrl = null)
        }
        return stream
    }
}
