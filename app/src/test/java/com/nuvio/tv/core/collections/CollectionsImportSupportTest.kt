package com.nuvio.tv.core.collections

import com.nuvio.tv.core.sync.CollectionSyncService
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsImportSupportTest {
    private val collectionsDataStore = mockk<CollectionsDataStore>(relaxed = true)
    private val layoutPreferenceDataStore = mockk<LayoutPreferenceDataStore>(relaxed = true)
    private val collectionSyncService = mockk<CollectionSyncService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)

    private val xperienceCollection = Collection(
        id = "aca6cfac-78e5-4c3c-ac71-7920514bc257",
        title = "For You & Trending",
        folders = listOf(
            CollectionFolder(
                id = "daf48a2b-d8a1-404f-a40b-07b2375498f8",
                title = "For You (Top 100 Today)",
                sources = listOf(
                    AddonCatalogCollectionSource(
                        addonId = "app.xperience.36a698c0-e472-4a44-9f59-b3b8d84e4b31",
                        type = "movie",
                        catalogId = "snoak_top100_movies"
                    )
                )
            )
        )
    )

    @Test
    fun `mergeAndPersist imports xperience style collections and updates home order`() = runTest {
        val support = CollectionsImportSupport(
            context = context,
            collectionsDataStore = collectionsDataStore,
            layoutPreferenceDataStore = layoutPreferenceDataStore,
            collectionSyncService = collectionSyncService
        )

        every { collectionsDataStore.importFromJson(any()) } returns listOf(xperienceCollection)
        coEvery { collectionsDataStore.getCurrentCollections() } returns emptyList()
        coEvery { layoutPreferenceDataStore.homeCatalogOrderKeys } returns flowOf(listOf("_special_genres"))

        val imported = support.mergeAndPersist("[]")

        assertTrue(imported)
        coVerify {
            collectionsDataStore.setCollections(listOf(xperienceCollection))
            layoutPreferenceDataStore.setHomeCatalogOrderKeys(
                listOf(
                    "_special_genres",
                    "collection_aca6cfac-78e5-4c3c-ac71-7920514bc257"
                )
            )
        }
        verify { collectionSyncService.triggerPush() }
    }

    @Test
    fun `mergeCollections replaces existing ids and appends new ones`() {
        val support = CollectionsImportSupport(
            context = context,
            collectionsDataStore = collectionsDataStore,
            layoutPreferenceDataStore = layoutPreferenceDataStore,
            collectionSyncService = collectionSyncService
        )
        val existing = listOf(
            Collection(id = "a", title = "Old A"),
            Collection(id = "b", title = "B")
        )
        val incoming = listOf(
            Collection(id = "a", title = "New A"),
            Collection(id = "c", title = "C")
        )

        val merged = support.mergeCollections(existing, incoming)

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("New A", merged.first { it.id == "a" }.title)
    }
}
