package com.nuvio.tv.core.collections

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.sync.CollectionSyncService
import com.nuvio.tv.core.sync.homeCollectionKey
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.Collection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CollectionsImportSupport"

@Singleton
class CollectionsImportSupport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collectionsDataStore: CollectionsDataStore,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val collectionSyncService: CollectionSyncService
) {
    companion object {
        const val BUNDLED_XPERIENCE_ANIME_ASSET = "xperience-anime-collections.json"
        val KNOWN_DOWNLOAD_FILENAMES = listOf(
            "nuvio-collections.json",
            BUNDLED_XPERIENCE_ANIME_ASSET,
            "xperience-collections.json"
        )
    }

    suspend fun importBundledXperienceAnimeIfEmpty(): Boolean {
        if (collectionsDataStore.getCurrentCollections().isNotEmpty()) return false
        return importFromAsset(BUNDLED_XPERIENCE_ANIME_ASSET)
    }

    suspend fun importFromAsset(assetName: String): Boolean {
        return try {
            val json = context.assets.open(assetName).bufferedReader().use { it.readText() }
            mergeAndPersist(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import collections from asset $assetName", e)
            false
        }
    }

    suspend fun mergeAndPersist(json: String, mergeWithExisting: Boolean = true): Boolean {
        val imported = collectionsDataStore.importFromJson(json)
        if (imported.isEmpty()) {
            Log.w(TAG, "No collections parsed from import")
            return false
        }
        val merged = if (mergeWithExisting) {
            mergeCollections(collectionsDataStore.getCurrentCollections(), imported)
        } else {
            imported
        }
        collectionsDataStore.setCollections(merged)
        appendCollectionKeysToHomeOrder(merged)
        collectionSyncService.triggerPush()
        Log.i(TAG, "Imported ${imported.size} collection(s); ${merged.size} total after merge")
        return true
    }

    fun mergeCollections(existing: List<Collection>, imported: List<Collection>): List<Collection> {
        val result = existing.toMutableList()
        val existingIds = result.map { it.id }.toSet()
        for (collection in imported) {
            if (collection.id in existingIds) {
                val index = result.indexOfFirst { it.id == collection.id }
                if (index >= 0) result[index] = collection
            } else {
                result.add(collection)
            }
        }
        return result
    }

    private suspend fun appendCollectionKeysToHomeOrder(collections: List<Collection>) {
        val orderKeys = layoutPreferenceDataStore.homeCatalogOrderKeys.first()
        val orderSet = orderKeys.toSet()
        val missingKeys = collections.map { homeCollectionKey(it.id) }.filter { it !in orderSet }
        if (missingKeys.isEmpty()) return
        layoutPreferenceDataStore.setHomeCatalogOrderKeys(orderKeys + missingKeys)
    }

    fun readFromDownloadsDirectory(): String? {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        for (filename in KNOWN_DOWNLOAD_FILENAMES) {
            val file = java.io.File(downloadsDir, filename)
            if (file.exists()) return file.readText()
        }
        return null
    }

    fun readFromMediaStoreDownloads(resolverContext: Context): String? {
        val resolver = resolverContext.contentResolver
        val uri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(android.provider.MediaStore.Downloads._ID)
        val selection = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?"
        for (filename in KNOWN_DOWNLOAD_FILENAMES) {
            val content = resolver.query(uri, projection, selection, arrayOf(filename), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID))
                    val fileUri = android.content.ContentUris.withAppendedId(uri, id)
                    resolver.openInputStream(fileUri)?.bufferedReader()?.readText()
                } else {
                    null
                }
            }
            if (content != null) return content
        }
        return null
    }
}
