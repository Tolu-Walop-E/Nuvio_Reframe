package com.nuvio.tv.core.viewpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewPackScreenPacksTest {

    @Test
    fun legacyHomePackHasNoScreenPacks() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "home",
              "name": "My Nuvio home",
              "blocks": [
                {"id": "nav", "type": "topNav", "dataSource": "none"},
                {"id": "hero", "type": "hero", "dataSource": "featured"},
                {"id": "movies", "type": "mediaRail", "dataSource": "catalog:a:movie:popular"}
              ]
            }
        """.trimIndent()
        val pack = parseViewPackJson(json)
        assertNull(pack.moviesScreen)
        assertNull(pack.showsScreen)
        assertEquals("catalog:a:movie:popular", pack.blocks.last().dataSource)
    }

    @Test
    fun nestedMoviesAndShowsRoundTrip() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "home",
              "name": "My Nuvio home",
              "blocks": [
                {"id": "nav", "type": "topNav", "dataSource": "none"},
                {"id": "series", "type": "mediaRail", "dataSource": "catalog:a:series:trending"}
              ],
              "screens": {
                "movies": {
                  "schemaVersion": 1,
                  "id": "movies",
                  "name": "My Nuvio movies",
                  "blocks": [
                    {"id": "nav", "type": "topNav", "dataSource": "none"},
                    {"id": "hero", "type": "hero", "dataSource": "catalog:a:movie:popular"},
                    {"id": "cw", "type": "mediaRail", "dataSource": "continueWatching"},
                    {"id": "rail", "type": "mediaRail", "dataSource": "catalog:a:movie:popular"}
                  ]
                },
                "shows": {
                  "schemaVersion": 1,
                  "id": "shows",
                  "name": "My Nuvio TV shows",
                  "blocks": [
                    {"id": "nav", "type": "topNav", "dataSource": "none"},
                    {"id": "rail", "type": "mediaRail", "dataSource": "catalog:a:series:trending"}
                  ]
                }
              }
            }
        """.trimIndent()
        val pack = parseViewPackJson(json)
        val movies = requireNotNull(pack.moviesScreen)
        val shows = requireNotNull(pack.showsScreen)
        assertEquals("My Nuvio movies", movies.name)
        assertTrue(packHasHero(movies))
        assertTrue(packHasContinueWatching(movies))
        assertEquals(listOf("a_movie_popular"), homeOrderKeysFromPack(movies))
        assertEquals(listOf("a_series_trending"), homeOrderKeysFromPack(shows))

        val again = parseViewPackJson(serializeViewPackJson(pack))
        assertNotNull(again.moviesScreen)
        assertNotNull(again.showsScreen)
        assertEquals("catalog:a:movie:popular", again.moviesScreen?.blocks?.last()?.dataSource)
        assertEquals("catalog:a:series:trending", again.showsScreen?.blocks?.last()?.dataSource)
    }
}
