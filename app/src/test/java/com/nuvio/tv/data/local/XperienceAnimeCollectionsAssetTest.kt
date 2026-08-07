package com.nuvio.tv.data.local

import org.junit.Assert.assertTrue
import org.junit.Test

class XperienceAnimeCollectionsAssetTest {
    private val store = CollectionsDataStore(
        appContext = io.mockk.mockk(relaxed = true),
        factory = io.mockk.mockk(relaxed = true),
        profileManager = io.mockk.mockk(relaxed = true)
    )

    @Test
    fun `bundled xperience anime collections json validates`() {
        val json = javaClass.classLoader
            ?.getResourceAsStream("xperience-anime-collections.json")
            ?.bufferedReader()
            ?.readText()
            .orEmpty()

        assertTrue(json.isNotBlank())

        val validation = store.validateCollectionsJson(json)
        assertTrue(validation.error ?: "valid", validation.valid)
        assertTrue(validation.collectionCount > 0)
        assertTrue(validation.folderCount > 0)

        val imported = store.importFromJson(json)
        assertTrue(imported.isNotEmpty())
        assertTrue(imported.all { it.folders.isNotEmpty() })
    }
}
