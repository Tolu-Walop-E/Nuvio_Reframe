package com.nuvio.tv.core.deeplink

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.viewpack.ViewPackRemoteImport
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DeepLinkHandler @Inject constructor(
    private val addonRepository: AddonRepository,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    @ApplicationContext private val context: Context
) {
    suspend fun installAddon(manifestUrl: String): DeepLinkInstallResult {
        return when (val result = addonRepository.fetchAddon(manifestUrl)) {
            is NetworkResult.Success -> {
                addonRepository.addAddon(manifestUrl)
                val addonName = result.data.displayName.ifBlank { result.data.baseUrl }
                DeepLinkInstallResult.Success(
                    context.getString(R.string.addon_install_success, addonName)
                )
            }
            is NetworkResult.Error -> DeepLinkInstallResult.Error(result.message)
            NetworkResult.Loading -> DeepLinkInstallResult.Error(context.getString(R.string.addon_error_invalid_url))
        }
    }

    suspend fun installViewPack(packUrl: String): DeepLinkInstallResult {
        return try {
            val (name, json) = ViewPackRemoteImport.loadAndSerialize(packUrl)
            layoutPreferenceDataStore.setActiveViewPackJson(json)
            DeepLinkInstallResult.Success(
                context.getString(R.string.layout_view_pack_imported, name)
            )
        } catch (e: Exception) {
            DeepLinkInstallResult.Error(
                context.getString(
                    R.string.layout_view_pack_import_failed,
                    e.message ?: context.getString(R.string.error_unknown)
                )
            )
        }
    }
}

sealed interface DeepLinkInstallResult {
    val message: String

    data class Success(
        override val message: String
    ) : DeepLinkInstallResult

    data class Error(
        override val message: String
    ) : DeepLinkInstallResult
}
