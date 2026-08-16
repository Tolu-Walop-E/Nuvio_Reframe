package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import com.nuvio.tv.domain.model.StreamClientResolve
import com.nuvio.tv.domain.model.StreamDebridCacheState
import com.nuvio.tv.domain.model.StreamDebridCacheStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerFreshStreamLinkPolicyTest {

    @Test
    fun `prefers same infoHash and strips stale url for re-resolve`() {
        val selected = PlayerFreshStreamLinkPolicy.select(
            PlayerFreshStreamLinkPolicy.Input(
                streams = listOf(
                    httpStream(url = "https://cdn.example/dead.mp4", addon = "Other"),
                    debridStream(
                        infoHash = "abc123",
                        url = "https://cdn.example/dead.mp4",
                        addon = "TorBox"
                    )
                ),
                deadUrl = "https://cdn.example/dead.mp4",
                preferredInfoHash = "abc123",
                preferredFileIdx = null,
                preferredBingeGroup = null,
                preferredAddonName = null,
            )
        )
        assertNotNull(selected)
        assertEquals("TorBox", selected!!.addonName)
        assertNull(selected.url)
        assertEquals("abc123", selected.clientResolve?.infoHash)
    }

    @Test
    fun `falls back to binge group with a different url`() {
        val selected = PlayerFreshStreamLinkPolicy.select(
            PlayerFreshStreamLinkPolicy.Input(
                streams = listOf(
                    httpStream(
                        url = "https://cdn.example/dead.mp4",
                        addon = "A",
                        bingeGroup = "group-1"
                    ),
                    httpStream(
                        url = "https://cdn.example/fresh.mp4",
                        addon = "B",
                        bingeGroup = "group-1"
                    )
                ),
                deadUrl = "https://cdn.example/dead.mp4",
                preferredInfoHash = null,
                preferredFileIdx = null,
                preferredBingeGroup = "group-1",
                preferredAddonName = "A",
            )
        )
        assertNotNull(selected)
        assertEquals("https://cdn.example/fresh.mp4", selected!!.url)
    }

    @Test
    fun `accepts unknown debrid cache status during recovery`() {
        val selected = PlayerFreshStreamLinkPolicy.select(
            PlayerFreshStreamLinkPolicy.Input(
                streams = listOf(
                    Stream(
                        name = "Pending",
                        title = null,
                        description = null,
                        url = "https://cdn.example/fresh.mp4",
                        ytId = null,
                        infoHash = null,
                        fileIdx = null,
                        externalUrl = null,
                        behaviorHints = null,
                        addonName = "AIO",
                        addonLogo = null,
                        debridCacheStatus = StreamDebridCacheStatus(
                            providerId = "torbox",
                            providerName = "TorBox",
                            state = StreamDebridCacheState.UNKNOWN
                        )
                    )
                ),
                deadUrl = "https://cdn.example/dead.mp4",
                preferredInfoHash = null,
                preferredFileIdx = null,
                preferredBingeGroup = null,
                preferredAddonName = null,
            )
        )
        assertNotNull(selected)
        assertEquals("https://cdn.example/fresh.mp4", selected!!.url)
    }

    private fun httpStream(
        url: String,
        addon: String,
        bingeGroup: String? = null
    ): Stream = Stream(
        name = "HTTP",
        title = null,
        description = null,
        url = url,
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = bingeGroup?.let {
            StreamBehaviorHints(
                notWebReady = null,
                bingeGroup = it,
                countryWhitelist = null,
                proxyHeaders = null
            )
        },
        addonName = addon,
        addonLogo = null
    )

    private fun debridStream(
        infoHash: String,
        url: String?,
        addon: String
    ): Stream = Stream(
        name = "Debrid",
        title = null,
        description = null,
        url = url,
        ytId = null,
        infoHash = infoHash,
        fileIdx = 0,
        externalUrl = null,
        behaviorHints = null,
        addonName = addon,
        addonLogo = null,
        clientResolve = StreamClientResolve(
            type = "debrid",
            infoHash = infoHash,
            fileIdx = 0,
            magnetUri = null,
            sources = null,
            torrentName = null,
            filename = null,
            mediaType = null,
            mediaId = null,
            mediaOnlyId = null,
            title = null,
            season = null,
            episode = null,
            service = "torbox",
            serviceIndex = null,
            serviceExtension = null,
            isCached = true,
            stream = null
        )
    )
}
