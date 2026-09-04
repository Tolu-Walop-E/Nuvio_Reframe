package com.nuvio.tv.core.sync

import com.nuvio.tv.core.sync.HomeRailKeyMigration.InstalledCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRailKeyMigrationTest {

    private fun catalog(addonId: String, type: String, catalogId: String) =
        InstalledCatalog(addonId = addonId, type = type, catalogId = catalogId)

    /** The xPerience case: same catalogs, new manifest id after an update. */
    @Test
    fun `remaps saved keys onto the reinstalled addon id`() {
        val installed = listOf(
            catalog("xperience.v2", "movie", "trending"),
            catalog("xperience.v2", "series", "airing")
        )
        val moves = HomeRailKeyMigration.plan(
            savedKeys = listOf("xperience.v1_movie_trending", "xperience.v1_series_airing"),
            installed = installed
        )
        assertEquals(
            mapOf(
                "xperience.v1_movie_trending" to "xperience.v2_movie_trending",
                "xperience.v1_series_airing" to "xperience.v2_series_airing"
            ),
            moves
        )
    }

    /** A URL-only change keeps the manifest id, so there is nothing to move. */
    @Test
    fun `leaves keys alone when the addon id is unchanged`() {
        val moves = HomeRailKeyMigration.plan(
            savedKeys = listOf("xperience_movie_trending"),
            installed = listOf(catalog("xperience", "movie", "trending"))
        )
        assertTrue("expected no moves but got $moves", moves.isEmpty())
    }

    /**
     * Two addons serving the same catalog pair is unresolvable, and guessing could
     * transplant a "hidden" flag onto someone else's rail.
     */
    @Test
    fun `refuses to guess when two addons serve the same catalog`() {
        val moves = HomeRailKeyMigration.plan(
            savedKeys = listOf("old_movie_trending"),
            installed = listOf(
                catalog("addon.a", "movie", "trending"),
                catalog("addon.b", "movie", "trending")
            )
        )
        assertTrue("expected no moves but got $moves", moves.isEmpty())
    }

    /** A live setting at the destination must not be replaced by a stale one. */
    @Test
    fun `never overwrites a key the user already has`() {
        val moves = HomeRailKeyMigration.plan(
            savedKeys = listOf("old_movie_trending", "xperience.v2_movie_trending"),
            installed = listOf(catalog("xperience.v2", "movie", "trending"))
        )
        assertTrue("expected no moves but got $moves", moves.isEmpty())
    }

    /** Only one stale key can claim a destination; the tie-break must be stable. */
    @Test
    fun `two stale keys cannot both claim the same destination`() {
        val moves = HomeRailKeyMigration.plan(
            savedKeys = listOf("v1_movie_trending", "v0_movie_trending"),
            installed = listOf(catalog("xperience.v2", "movie", "trending"))
        )
        assertEquals(mapOf("v0_movie_trending" to "xperience.v2_movie_trending"), moves)
    }

    /** Hidden / as-text / scale live in the customizations map even if the rail was never reordered. */
    @Test
    fun `remaps keys that only exist in saved settings maps`() {
        val moves = HomeRailKeyMigration.plan(
            savedKeys = listOf("xperience.v1_movie_genres"),
            installed = listOf(catalog("xperience.v2", "movie", "genres"))
        )
        assertEquals(
            mapOf("xperience.v1_movie_genres" to "xperience.v2_movie_genres"),
            moves
        )
    }

    /** Collection / Continue Watching / genre keys have no addon id to rewrite. */
    @Test
    fun `ignores synthetic rail keys`() {
        val moves = HomeRailKeyMigration.plan(
            savedKeys = listOf("collection_abc", "continue_watching", "_special_genres"),
            installed = listOf(
                catalog("addon", "movie", "trending"),
                catalog("addon", "special", "genres")
            )
        )
        assertTrue("expected no moves but got $moves", moves.isEmpty())
    }

    /** A catalog id that is a suffix of another must not steal the match. */
    @Test
    fun `prefers the most specific catalog identity`() {
        val installed = listOf(
            catalog("new", "movie", "movies"),
            catalog("new", "movie", "top_movies")
        )
        val moves = HomeRailKeyMigration.plan(
            savedKeys = listOf("old_movie_top_movies", "old_movie_movies"),
            installed = installed
        )
        assertEquals(
            mapOf(
                "old_movie_movies" to "new_movie_movies",
                "old_movie_top_movies" to "new_movie_top_movies"
            ),
            moves
        )
    }

    /** Type is part of the identity, so the same id under a different type stays put. */
    @Test
    fun `does not cross catalog types`() {
        val moves = HomeRailKeyMigration.plan(
            savedKeys = listOf("old_series_trending"),
            installed = listOf(catalog("new", "movie", "trending"))
        )
        assertTrue("expected no moves but got $moves", moves.isEmpty())
    }

    @Test
    fun `applying moves to an order list keeps position and drops duplicates`() {
        val moves = mapOf("old_movie_a" to "new_movie_a")
        assertEquals(
            listOf("continue_watching", "new_movie_a", "collection_x"),
            HomeRailKeyMigration.applyToList(
                listOf("continue_watching", "old_movie_a", "collection_x"),
                moves
            )
        )
        // Old and new both present: collapse to one entry at the earlier position.
        assertEquals(
            listOf("new_movie_a", "collection_x"),
            HomeRailKeyMigration.applyToList(
                listOf("old_movie_a", "new_movie_a", "collection_x"),
                moves
            )
        )
    }

    @Test
    fun `applying moves to a map prefers the existing destination value`() {
        val moves = mapOf("old_movie_a" to "new_movie_a")
        assertEquals(
            mapOf("new_movie_a" to "live"),
            HomeRailKeyMigration.applyToMap(
                linkedMapOf("new_movie_a" to "live", "old_movie_a" to "stale"),
                moves
            )
        )
    }

    @Test
    fun `no installed addons means no moves`() {
        assertTrue(
            HomeRailKeyMigration.plan(listOf("old_movie_a"), emptyList()).isEmpty()
        )
    }
}
