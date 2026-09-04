package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mozilla.tryfox.data.managers.FakeCacheManager
import org.mozilla.tryfox.data.managers.FakeUserDataRepository
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.download.ApkDownloadRequest
import org.mozilla.tryfox.download.model.DownloadStatus
import org.mozilla.tryfox.download.model.PersistedDownloadState
import org.mozilla.tryfox.model.HomeScreenLayout
import org.mozilla.tryfox.util.UNIVERSAL_ABI
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainCoroutineRule = MainCoroutineRule()

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `layout selection is persisted and cache clearing follows availability`() = runTest {
        val cacheManager = FakeCacheManager(tempDir)
        val downloads = FakeDownloadCoordinator()
        val userData = FakeUserDataRepository()
        val viewModel = SettingsViewModel(cacheManager, downloads, userData)
        advanceUntilIdle()

        assertEquals(HomeScreenLayout.OneCardPerApp, viewModel.uiState.value.homeScreenLayout)
        assertFalse(viewModel.uiState.value.canClearCache)

        cacheManager.setCacheSizeBytes(42)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canClearCache)

        viewModel.selectHomeScreenLayout(HomeScreenLayout.OneCardPerFlavor)
        advanceUntilIdle()
        assertEquals(HomeScreenLayout.OneCardPerFlavor, viewModel.uiState.value.homeScreenLayout)

        downloads.setActiveDownload()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canClearCache)

        downloads.clear()
        advanceUntilIdle()
        viewModel.clearCache()
        advanceUntilIdle()
        assertTrue(cacheManager.clearCacheCalled)
        assertEquals(0L, viewModel.uiState.value.cacheSizeBytes)
    }

    @Test
    fun `preferred ABI defaults to universal and offers every device ABI`() = runTest {
        val userData = FakeUserDataRepository()
        val viewModel = SettingsViewModel(
            FakeCacheManager(tempDir),
            FakeDownloadCoordinator(),
            userData,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )
        advanceUntilIdle()

        assertEquals(UNIVERSAL_ABI, viewModel.uiState.value.preferredAbi)
        assertEquals(listOf(UNIVERSAL_ABI, "arm64-v8a", "armeabi-v7a"), viewModel.uiState.value.abiOptions)

        viewModel.selectPreferredAbi("arm64-v8a")
        advanceUntilIdle()
        assertEquals("arm64-v8a", viewModel.uiState.value.preferredAbi)

        // An ABI this device cannot run is never persisted.
        viewModel.selectPreferredAbi("x86_64")
        advanceUntilIdle()
        assertEquals("arm64-v8a", viewModel.uiState.value.preferredAbi)
    }

    @Test
    fun `a stored ABI the device no longer runs resolves back to universal`() = runTest {
        val userData = FakeUserDataRepository()
        userData.savePreferredAbi("x86_64")
        val viewModel = SettingsViewModel(
            FakeCacheManager(tempDir),
            FakeDownloadCoordinator(),
            userData,
            supportedAbis = listOf("arm64-v8a"),
        )
        advanceUntilIdle()

        assertEquals(UNIVERSAL_ABI, viewModel.uiState.value.preferredAbi)
    }

    private class FakeDownloadCoordinator : ApkDownloadCoordinator {
        private val states = MutableStateFlow<Map<String, PersistedDownloadState>>(emptyMap())
        override val downloads = states

        override fun enqueue(request: ApkDownloadRequest): String = request.uniqueKey
        override fun retry(request: ApkDownloadRequest): String = request.uniqueKey
        override fun cancel(uniqueKey: String) = Unit
        override fun observe(uniqueKey: String): Flow<PersistedDownloadState?> = downloads.map { it[uniqueKey] }

        fun setActiveDownload() {
            states.value = mapOf(
                "download" to PersistedDownloadState(
                    uniqueKey = "download",
                    downloadUrl = "https://example.invalid/download.apk",
                    outputPath = "download.apk",
                    appName = "fenix",
                    fileName = "download.apk",
                    status = DownloadStatus.RUNNING,
                ),
            )
        }

        fun clear() {
            states.value = emptyMap()
        }
    }
}
