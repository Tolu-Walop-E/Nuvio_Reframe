package com.nuvio.tv.core.sync

import android.util.Log
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.viewpack.parseViewPackJson
import com.nuvio.tv.core.viewpack.rotateUnlockedBlocks
import com.nuvio.tv.core.viewpack.serializeViewPackJson
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.remote.supabase.SupabaseViewPackBlob
import com.nuvio.tv.domain.model.AuthState
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "ViewPackSyncService"
private const val POLL_INTERVAL_MS = 12_000L

data class PendingViewPackOffer(
    val packName: String,
    val serializedJson: String,
    val updatedAt: String?,
    val profileId: Int
)

/**
 * Pulls Studio-authored view packs from Supabase (`view_pack_blobs`).
 * Background poll surfaces an accept dialog instead of silently overwriting while watching.
 */
@Singleton
class ViewPackSyncService @Inject constructor(
    private val postgrest: Postgrest,
    private val authManager: AuthManager,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val profileManager: ProfileManager,
    private val syncClientIdentity: SyncClientIdentity,
    private val homeCatalogSettingsSyncService: HomeCatalogSettingsSyncService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    private val _pendingOffer = MutableStateFlow<PendingViewPackOffer?>(null)
    val pendingOffer: StateFlow<PendingViewPackOffer?> = _pendingOffer.asStateFlow()

    /** UpdatedAt values the user dismissed with Later (until a newer pack arrives). */
    private var dismissedUpdatedAt: String? = null

    init {
        scope.launch {
            authManager.authState.collect { state ->
                when (state) {
                    is AuthState.FullAccount -> startPolling()
                    is AuthState.SignedOut -> {
                        stopPolling()
                        _pendingOffer.value = null
                        dismissedUpdatedAt = null
                    }
                    is AuthState.Loading -> Unit
                }
            }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                try {
                    checkForRemoteOffer(promptUser = true)
                } catch (e: Exception) {
                    Log.w(TAG, "View pack poll failed", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * Re-check rail rotation when the app returns to the foreground.
     *
     * The home pipeline only evaluates rotation when it first collects the pack,
     * which ties a time-based feature to ViewModel construction: someone who always
     * leaves via the Home button rather than Back keeps one arrangement until the
     * process happens to be reclaimed. onStart only fires after the app has been
     * away, so this cannot reorder rails while they are being browsed.
     */
    fun requestForegroundRotation() {
        scope.launch { rotateActivePackIfDue() }
    }

    private suspend fun rotateActivePackIfDue() {
        try {
            val json = layoutPreferenceDataStore.activeViewPackJson.first()
            if (json.isNullOrBlank()) return
            val pack = parseViewPackJson(json)
            if (!pack.rotateUnlocked) return
            val state = layoutPreferenceDataStore.getViewPackRotationState()
            val rotation = rotateUnlockedBlocks(pack, state)
            if (!rotation.didShuffle) return
            layoutPreferenceDataStore.setViewPackRotationState(rotation.state)
            Log.i(TAG, "Rotated unlocked rails for “${pack.name}”")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to rotate view pack on foreground", e)
        }
    }

    private suspend fun <T> withJwtRefreshRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (!authManager.refreshSessionIfJwtExpired(e)) throw e
            block()
        }
    }

    /**
     * Startup / force sync: apply remote pack immediately when different.
     * Returns true if local state was updated.
     */
    suspend fun pullFromRemote(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!authManager.isAuthenticated) return@withContext Result.success(false)
            val offer = fetchRemoteOffer()
            val local = layoutPreferenceDataStore.activeViewPackJson.first()
            if (offer == null) {
                if (!local.isNullOrBlank()) {
                    layoutPreferenceDataStore.clearActiveViewPack()
                    _pendingOffer.value = null
                    dismissedUpdatedAt = null
                    Log.i(TAG, "Remote view pack absent — cleared local pack")
                    return@withContext Result.success(true)
                }
                return@withContext Result.success(false)
            }
            if (local == offer.serializedJson) {
                _pendingOffer.value = null
                return@withContext Result.success(false)
            }
            layoutPreferenceDataStore.setActiveViewPackJson(offer.serializedJson)
            _pendingOffer.value = null
            dismissedUpdatedAt = offer.updatedAt
            Log.i(TAG, "Applied remote view pack “${offer.packName}” for profile ${offer.profileId}")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull view pack from remote", e)
            Result.failure(e)
        }
    }

    /**
     * Poll path: if remote differs from local, queue an accept dialog (do not apply yet).
     */
    suspend fun checkForRemoteOffer(promptUser: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!authManager.isAuthenticated) return@withContext Result.success(false)
            val offer = fetchRemoteOffer()
            val local = layoutPreferenceDataStore.activeViewPackJson.first()
            if (offer == null) {
                if (!local.isNullOrBlank()) {
                    // Cloud pack was cleared (Studio/TV) — drop local so it cannot reappear.
                    layoutPreferenceDataStore.clearActiveViewPack()
                    _pendingOffer.value = null
                    dismissedUpdatedAt = null
                    Log.i(TAG, "Remote view pack cleared — removed local pack")
                    return@withContext Result.success(true)
                }
                if (_pendingOffer.value != null) _pendingOffer.value = null
                return@withContext Result.success(false)
            }
            if (local == offer.serializedJson) {
                if (_pendingOffer.value?.serializedJson == offer.serializedJson) {
                    _pendingOffer.value = null
                }
                return@withContext Result.success(false)
            }
            if (!promptUser) {
                layoutPreferenceDataStore.setActiveViewPackJson(offer.serializedJson)
                _pendingOffer.value = null
                return@withContext Result.success(true)
            }
            if (offer.updatedAt != null && offer.updatedAt == dismissedUpdatedAt) {
                return@withContext Result.success(false)
            }
            if (_pendingOffer.value?.serializedJson == offer.serializedJson) {
                return@withContext Result.success(false)
            }
            _pendingOffer.value = offer
            Log.i(TAG, "Pending view pack offer “${offer.packName}” for profile ${offer.profileId}")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check remote view pack", e)
            Result.failure(e)
        }
    }

    suspend fun acceptPendingOffer(): Result<String> = withContext(Dispatchers.IO) {
        val offer = _pendingOffer.value
            ?: return@withContext Result.failure(IllegalStateException("No pending view pack"))
        try {
            layoutPreferenceDataStore.setActiveViewPackJson(offer.serializedJson)
            dismissedUpdatedAt = offer.updatedAt
            _pendingOffer.value = null
            // Studio Send to TV also pushes genre targets / home catalog — pull them now.
            homeCatalogSettingsSyncService.pullFromRemote()
                .onFailure { e -> Log.w(TAG, "Home catalog pull after pack accept failed", e) }
            Log.i(TAG, "User accepted view pack “${offer.packName}”")
            Result.success(offer.packName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept view pack", e)
            Result.failure(e)
        }
    }

    fun dismissPendingOffer() {
        val offer = _pendingOffer.value
        dismissedUpdatedAt = offer?.updatedAt
        _pendingOffer.value = null
        Log.i(TAG, "User dismissed view pack offer “${offer?.packName}”")
    }

    /**
     * Clear the active pack locally and delete the account blob so poll cannot re-offer it.
     * Local clear always succeeds; remote clear is best-effort when signed in.
     */
    suspend fun clearActiveAndRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        layoutPreferenceDataStore.clearActiveViewPack()
        _pendingOffer.value = null
        dismissedUpdatedAt = null
        if (!authManager.isAuthenticated) {
            return@withContext Result.success(Unit)
        }
        try {
            val profileId = profileManager.activeProfileId.value
            val params = buildJsonObject {
                put("p_profile_id", profileId)
                putSyncOriginClientId(syncClientIdentity)
            }
            withJwtRefreshRetry {
                postgrest.rpc("sync_clear_view_pack", params)
            }
            Log.i(TAG, "Cleared remote view pack for profile $profileId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear remote view pack (local already cleared)", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchRemoteOffer(): PendingViewPackOffer? {
        if (!authManager.isAuthenticated) return null
        val profileId = profileManager.activeProfileId.value
        val params = buildJsonObject {
            put("p_profile_id", profileId)
        }
        val response = withJwtRefreshRetry {
            postgrest.rpc("sync_pull_view_pack", params)
        }
        val blob = response.decodeList<SupabaseViewPackBlob>().firstOrNull() ?: return null
        val remoteObject = blob.packJson
        if (remoteObject.isEmpty()) return null
        val remoteText = remoteObject.toString()
        val pack = parseViewPackJson(remoteText)
        // Re-serialize (not rotate) so the comparison against the local copy is a
        // stable, normalized string rather than whatever key order Supabase returned.
        val serialized = serializeViewPackJson(pack)
        return PendingViewPackOffer(
            packName = pack.name,
            serializedJson = serialized,
            updatedAt = blob.updatedAt,
            profileId = profileId
        )
    }
}
