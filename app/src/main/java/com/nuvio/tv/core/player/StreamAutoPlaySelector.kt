package com.nuvio.tv.core.player

import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlaySource
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState

object StreamAutoPlaySelector {
    fun orderAddonStreams(
        streams: List<AddonStreams>,
        installedOrder: List<String>
    ): List<AddonStreams> {
        if (streams.isEmpty()) return streams

        val addonRankByName = HashMap<String, Int>(installedOrder.size)
        installedOrder.forEachIndexed { index, addonName ->
            if (addonName !in addonRankByName) {
                addonRankByName[addonName] = index
            }
        }

        val (directDebridEntries, remainingEntries) = streams.partition {
            it.streams.any { stream -> stream.isDirectDebrid() }
        }
        if (installedOrder.isEmpty()) {
            return directDebridEntries + remainingEntries.sortedWith(preferredAddonComparator { it.addonName })
        }
        val (addonEntries, pluginEntries) = remainingEntries.partition { it.addonName in addonRankByName }
        val orderedAddons = addonEntries.sortedWith(
            compareBy<AddonStreams> { preferredAddonRank(it.addonName) }
                .thenBy { addonRankByName.getValue(it.addonName) }
        )
        return directDebridEntries + orderedAddons +
            pluginEntries.sortedWith(preferredAddonComparator { it.addonName })
    }

    private fun isPlayable(stream: Stream): Boolean {
        // External URL streams (e.g. error pages, web links) are not playable.
        if (stream.isExternal()) return false
        when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CHECKING,
            StreamDebridCacheState.NOT_CACHED,
            StreamDebridCacheState.UNKNOWN -> return false
            StreamDebridCacheState.CACHED,
            null -> Unit
        }
        return stream.getStreamUrl() != null || stream.isTorrent() || stream.isDirectDebrid()
    }

    internal fun preferredAddonRank(addonName: String): Int {
        val normalized = addonName.lowercase().replace(" ", "")
        return when {
            "duckstream" in normalized -> 0
            "penguplay" in normalized || normalized == "pengu" -> 1
            else -> 2
        }
    }

    private fun <T> preferredAddonComparator(name: (T) -> String): Comparator<T> {
        return compareBy { preferredAddonRank(name(it)) }
    }

    private fun shouldWaitForDuckstreams(
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        arrivedAddonNames: Set<String>
    ): Boolean {
        val expected = if (selectedAddons.isEmpty()) installedAddonNames else selectedAddons
        val duckstreams = expected.filter { preferredAddonRank(it) == 0 }
        if (duckstreams.isEmpty()) return false
        return duckstreams.none { name ->
            arrivedAddonNames.any { arrived -> arrived.equals(name, ignoreCase = true) }
        }
    }



    fun selectAutoPlayStream(
        streams: List<Stream>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
        bingeGroupOnly: Boolean = false,
        arrivedAddonNames: Set<String> = emptySet(),
        waitForPreferredAddons: Boolean = false
    ): Stream? {
        if (streams.isEmpty()) return null

        val effectiveSource = if (!AppFeaturePolicy.pluginsEnabled && source == StreamAutoPlaySource.ENABLED_PLUGINS_ONLY) {
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY
        } else {
            source
        }

        val sourceScopedStreams = when (effectiveSource) {
            StreamAutoPlaySource.ALL_SOURCES -> streams
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY -> streams.filter { it.addonName in installedAddonNames }
            StreamAutoPlaySource.ENABLED_PLUGINS_ONLY -> streams.filter { it.addonName !in installedAddonNames }
        }
        val candidateStreams = sourceScopedStreams.filter { stream ->
            val isAddonStream = stream.addonName in installedAddonNames
            if (isAddonStream) {
                selectedAddons.isEmpty() || stream.addonName in selectedAddons
            } else {
                selectedPlugins.isEmpty() || stream.addonName in selectedPlugins
            }
        }
        if (candidateStreams.isEmpty()) return null

        if (waitForPreferredAddons &&
            shouldWaitForDuckstreams(
                installedAddonNames = installedAddonNames,
                selectedAddons = selectedAddons,
                arrivedAddonNames = arrivedAddonNames
            )
        ) {
            return null
        }

        val rankedCandidates = candidateStreams.withIndex()
            .sortedWith(
                compareBy<IndexedValue<Stream>> { preferredAddonRank(it.value.addonName) }
                    .thenBy { it.index }
            )
            .map { it.value }

        // Binge group matching takes priority over mode — even in MANUAL mode,
        // a persisted binge group should auto-play without showing the picker.
        val targetBingeGroup = preferredBingeGroup?.trim().orEmpty()
        if (preferBingeGroupInSelection && targetBingeGroup.isNotEmpty()) {
            val bingeGroupMatch = rankedCandidates.firstOrNull { stream ->
                stream.behaviorHints?.bingeGroup == targetBingeGroup && isPlayable(stream)
            }
            if (bingeGroupMatch != null) return bingeGroupMatch
            // When bingeGroupOnly is set (MANUAL mode with only binge-group
            // preference enabled), don't fall back to a non-matching stream —
            // return null so the caller shows the stream picker instead.
            if (bingeGroupOnly) return null
        }

        if (mode == StreamAutoPlayMode.MANUAL) return null

        return when (mode) {
            StreamAutoPlayMode.MANUAL -> null
            StreamAutoPlayMode.FIRST_STREAM -> rankedCandidates.firstOrNull { isPlayable(it) }
            StreamAutoPlayMode.REGEX_MATCH -> {
                val pattern = regexPattern.trim()
 
                // Try to compile the user regex
                val userRegex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
                if (userRegex == null) return null

                // Auto-extract exclusion patterns from negative lookaheads
                val exclusionMatches = Regex("\\(\\?![^)]*?\\(([^)]+)\\)").findAll(pattern)

                val exclusionWords = exclusionMatches
                    .flatMap { match -> match.groupValues[1].split("|") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                val excludeRegex = if (exclusionWords.isNotEmpty()) {
                    Regex("\\b(${exclusionWords.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
                } else null

                // 1. Build list of ALL regex‑matching streams
                val matchingStreams = rankedCandidates.filter { stream ->
                    if (!isPlayable(stream)) return@filter false

                    val searchableText = buildString {
                        append(stream.addonName).append(' ')
                        append(stream.name.orEmpty()).append(' ')
                        append(stream.title.orEmpty()).append(' ')
                        append(stream.description.orEmpty()).append(' ')
                        append(stream.getStreamUrl().orEmpty())
                        if (stream.isTorrent()) append(' ').append(stream.infoHash.orEmpty())
                    }

                    // Must match include pattern
                    if (!userRegex.containsMatchIn(searchableText)) return@filter false

                    // Must NOT match exclusion pattern
                    if (excludeRegex != null && excludeRegex.containsMatchIn(searchableText)) {
                        return@filter false
                    }

                    true
                }

                if (matchingStreams.isEmpty()) return null
                matchingStreams.firstOrNull { isPlayable(it) }
            }

        }
    }
}
