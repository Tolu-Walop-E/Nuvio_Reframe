package com.nuvio.tv.ui.screens.account

import android.content.Context
import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.R
import com.nuvio.tv.core.auth.AuthFailureException
import com.nuvio.tv.core.auth.AuthFailureReason
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.plugin.PluginManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.AddonSyncService
import com.nuvio.tv.core.sync.LibrarySyncService
import com.nuvio.tv.core.sync.PluginSyncService
import com.nuvio.tv.core.sync.ProfileSettingsSyncService
import com.nuvio.tv.core.sync.ProfileSyncService
import com.nuvio.tv.core.sync.WatchProgressSyncService
import com.nuvio.tv.core.sync.WatchedItemsSyncService
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.repository.AddonRepositoryImpl
import com.nuvio.tv.data.repository.AuthDiagnosticReportRepository
import com.nuvio.tv.data.repository.LibraryRepositoryImpl
import com.nuvio.tv.data.repository.WatchProgressRepositoryImpl
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.UserProfile
import com.nuvio.tv.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelEmailAuthTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authManager = mockk<AuthManager>()
    private val syncRepository = mockk<SyncRepository>(relaxed = true)
    private val pluginSyncService = mockk<PluginSyncService>()
    private val addonSyncService = mockk<AddonSyncService>()
    private val watchProgressSyncService = mockk<WatchProgressSyncService>(relaxed = true)
    private val librarySyncService = mockk<LibrarySyncService>(relaxed = true)
    private val watchedItemsSyncService = mockk<WatchedItemsSyncService>(relaxed = true)
    private val profileSettingsSyncService = mockk<ProfileSettingsSyncService>(relaxed = true)
    private val profileSyncService = mockk<ProfileSyncService>()
    private val pluginManager = mockk<PluginManager>(relaxed = true)
    private val addonRepository = mockk<AddonRepositoryImpl>(relaxed = true)
    private val watchProgressRepository = mockk<WatchProgressRepositoryImpl>(relaxed = true)
    private val libraryRepository = mockk<LibraryRepositoryImpl>(relaxed = true)
    private val watchProgressPreferences = mockk<WatchProgressPreferences>(relaxed = true)
    private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
    private val watchedItemsPreferences = mockk<WatchedItemsPreferences>(relaxed = true)
    private val traktAuthDataStore = mockk<TraktAuthDataStore>(relaxed = true)
    private val postgrest = mockk<Postgrest>(relaxed = true)
    private val profileManager = mockk<ProfileManager>()
    private val authDiagnosticReportRepository = mockk<AuthDiagnosticReportRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val primaryProfile = UserProfile(
        id = 1,
        name = "Primary",
        avatarColorHex = "#1E88E5"
    )

    private fun createViewModel(): AccountViewModel {
        every { authManager.authState } returns authState
        every { profileManager.profiles } returns MutableStateFlow(listOf(primaryProfile))
        every { profileManager.activeProfileId } returns MutableStateFlow(1)
        every { pluginManager.repositories } returns flowOf(emptyList())
        every { addonRepository.getInstalledAddons() } returns flowOf(emptyList())
        coEvery { profileSyncService.pushToRemote() } returns Result.success(Unit)
        coEvery { profileSyncService.pullFromRemote() } returns Result.success(listOf(primaryProfile))
        coEvery { pluginSyncService.pushToRemote() } returns Result.success(Unit)
        coEvery { pluginSyncService.getRemoteRepoUrls() } returns Result.success(emptyList())
        coEvery { addonSyncService.pushToRemote() } returns Result.success(Unit)
        coEvery { addonSyncService.getRemoteAddonUrls() } returns Result.success(emptyList())

        return AccountViewModel(
            authManager = authManager,
            syncRepository = syncRepository,
            pluginSyncService = pluginSyncService,
            addonSyncService = addonSyncService,
            watchProgressSyncService = watchProgressSyncService,
            librarySyncService = librarySyncService,
            watchedItemsSyncService = watchedItemsSyncService,
            profileSettingsSyncService = profileSettingsSyncService,
            profileSyncService = profileSyncService,
            pluginManager = pluginManager,
            addonRepository = addonRepository,
            watchProgressRepository = watchProgressRepository,
            libraryRepository = libraryRepository,
            watchProgressPreferences = watchProgressPreferences,
            libraryPreferences = libraryPreferences,
            watchedItemsPreferences = watchedItemsPreferences,
            traktAuthDataStore = traktAuthDataStore,
            postgrest = postgrest,
            profileManager = profileManager,
            authDiagnosticReportRepository = authDiagnosticReportRepository,
            context = context
        )
    }

    @Test
    fun `successful signup uses AuthManager email signup and phase one initialization`() = runTest {
        coEvery { authManager.signUpWithEmail("viewer@example.com", "password") } returns Result.success(Unit)
        val viewModel = createViewModel()

        viewModel.signUp("viewer@example.com", "password")
        advanceUntilIdle()

        coVerify(exactly = 1) { authManager.signUpWithEmail("viewer@example.com", "password") }
        coVerify(exactly = 1) { profileSyncService.pushToRemote() }
        coVerify(exactly = 1) { pluginSyncService.pushToRemote() }
        coVerify(exactly = 1) { addonSyncService.pushToRemote() }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `successful signin uses AuthManager email signin and phase one sync`() = runTest {
        coEvery { authManager.signInWithEmail("viewer@example.com", "password") } returns Result.success(Unit)
        val viewModel = createViewModel()

        viewModel.signIn("viewer@example.com", "password")
        advanceUntilIdle()

        coVerify(exactly = 1) { authManager.signInWithEmail("viewer@example.com", "password") }
        coVerify(exactly = 1) { profileSyncService.pullFromRemote() }
        coVerify(exactly = 1) { pluginSyncService.getRemoteRepoUrls() }
        coVerify(exactly = 1) { addonSyncService.getRemoteAddonUrls() }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `second signin submission is ignored while first is loading`() = runTest {
        val pendingResult = CompletableDeferred<Result<Unit>>()
        coEvery { authManager.signInWithEmail(any(), any()) } coAnswers { pendingResult.await() }
        val viewModel = createViewModel()

        viewModel.signIn("viewer@example.com", "password")
        viewModel.signIn("viewer@example.com", "password")
        runCurrent()

        coVerify(exactly = 1) { authManager.signInWithEmail("viewer@example.com", "password") }

        pendingResult.complete(Result.success(Unit))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `authentication failure becomes readable UI error state`() = runTest {
        every { context.getString(R.string.account_error_invalid_credentials) } returns "Invalid email or password."
        coEvery { authManager.signInWithEmail(any(), any()) } returns Result.failure(
            AuthFailureException(AuthFailureReason.InvalidCredentials)
        )
        val viewModel = createViewModel()

        viewModel.signIn("viewer@example.com", "wrong-password")
        advanceUntilIdle()

        assertEquals("Invalid email or password.", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
