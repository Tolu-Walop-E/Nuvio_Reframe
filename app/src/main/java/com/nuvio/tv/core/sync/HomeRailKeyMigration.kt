package com.nuvio.tv.core.sync

/**
 * Rebinding saved home-rail settings after an addon is reinstalled.
 *
 * Every per-rail setting the user can change — order, hidden, custom title, scale,
 * trailer, poster grow, text mode — is stored against the rail's order key, which is
 * `{addonManifestId}_{catalogType}_{catalogId}` (see [homeCatalogKey]). The addon's
 * transport URL is deliberately absent, so swapping the URL of an addon that keeps its
 * manifest id already preserves everything.
 *
 * What does break is an addon that publishes a *new manifest id* in a new version. The
 * catalogs are the same catalogs, but every saved key now points at an addon id that no
 * longer exists, so the user's whole home layout silently reverts to defaults. Packs
 * already survive this via `remapPackOrderKey`; user settings did not.
 *
 * This planner closes that gap. It identifies a rail by `(catalogType, catalogId)` and
 * moves saved settings onto whichever installed addon now serves that pair.
 */
internal object HomeRailKeyMigration {

    /** One catalog offered by a currently installed, enabled addon. */
    internal data class InstalledCatalog(
        val addonId: String,
        val type: String,
        val catalogId: String
    ) {
        /** The trailing part of an order key that identifies the catalog, not the addon. */
        val identitySuffix: String get() = "_${type}_${catalogId}"

        val orderKey: String get() = homeCatalogKey(addonId, type, catalogId)
    }

    /**
     * Saved keys that should move, as `old key -> new key`.
     *
     * Deliberately conservative — a wrong remap would apply someone's settings, or worse
     * their "hidden" flag, to an unrelated rail. A key only moves when:
     *
     * - it does not already match an installed catalog (nothing to fix),
     * - exactly one installed addon serves that `(type, catalogId)` pair, so the new
     *   owner is unambiguous,
     * - the addon-id part of the key is non-empty, which keeps synthetic keys such as
     *   `collection_…` and `continue_watching` out of it,
     * - and the destination is not already spoken for by another saved key, so an
     *   existing setting is never overwritten by a stale one.
     */
    fun plan(
        savedKeys: Collection<String>,
        installed: Collection<InstalledCatalog>
    ): Map<String, String> {
        if (savedKeys.isEmpty() || installed.isEmpty()) return emptyMap()

        // Ambiguous pairs are dropped outright: if two addons both serve
        // `movie/top`, there is no way to tell which one the settings belonged to.
        val ownerBySuffix = HashMap<String, String?>()
        for (catalog in installed) {
            if (catalog.type.isBlank() || catalog.catalogId.isBlank() || catalog.addonId.isBlank()) {
                continue
            }
            val suffix = catalog.identitySuffix
            if (ownerBySuffix.containsKey(suffix)) {
                if (ownerBySuffix[suffix] != catalog.addonId) ownerBySuffix[suffix] = null
            } else {
                ownerBySuffix[suffix] = catalog.addonId
            }
        }
        val unambiguous = ownerBySuffix.filterValues { it != null }.mapValues { it.value!! }
        if (unambiguous.isEmpty()) return emptyMap()

        // Longest suffix first, so `_movie_top_movies` is preferred over `_movie_movies`
        // for a key that happens to end with both.
        val suffixesByLength = unambiguous.keys.sortedByDescending { it.length }
        val savedKeySet = savedKeys.toSet()
        val installedKeys = installed.mapTo(HashSet()) { it.orderKey }

        val moves = LinkedHashMap<String, String>()
        val claimed = HashSet<String>()
        // Stable iteration so the "first key wins" tie-break is reproducible.
        for (savedKey in savedKeys.distinct().sorted()) {
            if (savedKey.isBlank() || savedKey in installedKeys) continue
            val suffix = suffixesByLength.firstOrNull { savedKey.endsWith(it) } ?: continue
            // Guard against a bare suffix with no addon id in front of it.
            if (savedKey.length <= suffix.length) continue
            val target = unambiguous.getValue(suffix) + suffix
            if (target == savedKey) continue
            if (target in savedKeySet || target in claimed) continue
            claimed.add(target)
            moves[savedKey] = target
        }
        return moves
    }

    /** Rewrites an order list in place, preserving position and dropping duplicates. */
    fun applyToList(keys: List<String>, moves: Map<String, String>): List<String> {
        if (moves.isEmpty() || keys.isEmpty()) return keys
        val seen = LinkedHashSet<String>(keys.size)
        for (key in keys) seen.add(moves[key] ?: key)
        return seen.toList()
    }

    /** Existing entries at a destination win; a stale key never clobbers a live one. */
    fun <T> applyToMap(values: Map<String, T>, moves: Map<String, String>): Map<String, T> {
        if (moves.isEmpty() || values.isEmpty()) return values
        val out = LinkedHashMap<String, T>(values.size)
        for ((key, value) in values) {
            val target = moves[key] ?: key
            if (target !in out) out[target] = value
        }
        return out
    }
}
