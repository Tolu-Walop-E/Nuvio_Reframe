package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local record of user ratings / skipped rate prompts so we don't re-prompt
 * after a movie or season/series finale. Simkl sync is best-effort on top.
 */
@Singleton
class UserRatingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "user_ratings"
        private const val PREFIX_RATING = "r:"
        private const val PREFIX_DISMISSED = "d:"
        private const val PREFIX_SYNCED = "s:"
    }

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private fun ratingKey(mediaKey: String) = stringPreferencesKey("rating_$mediaKey")

    data class Entry(
        val rating: Int? = null,
        val dismissed: Boolean = false,
        val syncedToSimkl: Boolean = false
    ) {
        val shouldSkipPrompt: Boolean
            get() = rating != null || dismissed
    }

    suspend fun get(mediaKey: String): Entry {
        val raw = store().data.first()[ratingKey(mediaKey)] ?: return Entry()
        return parse(raw)
    }

    suspend fun shouldSkipPrompt(mediaKey: String): Boolean = get(mediaKey).shouldSkipPrompt

    suspend fun saveRating(mediaKey: String, rating: Int, syncedToSimkl: Boolean = false) {
        val value = rating.coerceIn(1, 10)
        store().edit { prefs ->
            prefs[ratingKey(mediaKey)] = encode(
                rating = value,
                dismissed = false,
                syncedToSimkl = syncedToSimkl
            )
        }
    }

    suspend fun markDismissed(mediaKey: String) {
        val existing = get(mediaKey)
        if (existing.rating != null) return
        store().edit { prefs ->
            prefs[ratingKey(mediaKey)] = encode(
                rating = null,
                dismissed = true,
                syncedToSimkl = false
            )
        }
    }

    suspend fun markSynced(mediaKey: String) {
        val existing = get(mediaKey)
        val rating = existing.rating ?: return
        store().edit { prefs ->
            prefs[ratingKey(mediaKey)] = encode(
                rating = rating,
                dismissed = false,
                syncedToSimkl = true
            )
        }
    }

    private fun encode(rating: Int?, dismissed: Boolean, syncedToSimkl: Boolean): String {
        val parts = mutableListOf<String>()
        if (rating != null) parts += "$PREFIX_RATING$rating"
        if (dismissed) parts += PREFIX_DISMISSED
        if (syncedToSimkl) parts += PREFIX_SYNCED
        return parts.joinToString("|")
    }

    private fun parse(raw: String): Entry {
        var rating: Int? = null
        var dismissed = false
        var synced = false
        raw.split('|').forEach { part ->
            when {
                part.startsWith(PREFIX_RATING) ->
                    rating = part.removePrefix(PREFIX_RATING).toIntOrNull()?.coerceIn(1, 10)
                part == PREFIX_DISMISSED || part.startsWith(PREFIX_DISMISSED) -> dismissed = true
                part == PREFIX_SYNCED || part.startsWith(PREFIX_SYNCED) -> synced = true
            }
        }
        return Entry(rating = rating, dismissed = dismissed, syncedToSimkl = synced)
    }
}
