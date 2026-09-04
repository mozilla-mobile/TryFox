package org.mozilla.tryfox.ui.screens

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.repositories.UserDataRepository
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.model.HomeScreenLayout
import org.mozilla.tryfox.util.DEFAULT_PREFERRED_ABI
import org.mozilla.tryfox.util.abiPreferenceOptions
import org.mozilla.tryfox.util.resolvePreferredAbi

data class SettingsUiState(
    val cacheState: CacheManagementState = CacheManagementState.IdleEmpty,
    val cacheSizeBytes: Long = 0L,
    val hasActiveDownloads: Boolean = false,
    val homeScreenLayout: HomeScreenLayout = HomeScreenLayout.OneCardPerApp,
    val preferredAbi: String = DEFAULT_PREFERRED_ABI,
    val abiOptions: List<String> = listOf(DEFAULT_PREFERRED_ABI),
) {
    val canClearCache: Boolean
        get() = cacheState == CacheManagementState.IdleNonEmpty && !hasActiveDownloads
}

class SettingsViewModel(
    private val cacheManager: CacheManager,
    downloadCoordinator: ApkDownloadCoordinator,
    private val userDataRepository: UserDataRepository,
    supportedAbis: List<String> = runCatching { Build.SUPPORTED_ABIS.toList() }.getOrDefault(emptyList()),
) : ViewModel() {
    private val abiOptions = abiPreferenceOptions(supportedAbis)

    val uiState: StateFlow<SettingsUiState> = combine(
        cacheManager.cacheState,
        cacheManager.cacheSizeBytes,
        downloadCoordinator.downloads,
        userDataRepository.homeScreenLayoutFlow,
        userDataRepository.preferredAbiFlow,
    ) { cacheState, cacheSizeBytes, downloads, homeScreenLayout, preferredAbi ->
        SettingsUiState(
            cacheState = cacheState,
            cacheSizeBytes = cacheSizeBytes,
            hasActiveDownloads = downloads.values.any { !it.isTerminal },
            homeScreenLayout = homeScreenLayout,
            preferredAbi = resolvePreferredAbi(preferredAbi, supportedAbis),
            abiOptions = abiOptions,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(abiOptions = abiOptions))

    init {
        viewModelScope.launch { cacheManager.checkCacheStatus() }
    }

    fun clearCache() {
        if (!uiState.value.canClearCache) return
        viewModelScope.launch { cacheManager.clearCache() }
    }

    fun selectHomeScreenLayout(layout: HomeScreenLayout) {
        viewModelScope.launch { userDataRepository.saveHomeScreenLayout(layout) }
    }

    fun selectPreferredAbi(abiName: String) {
        if (abiName !in abiOptions) return
        viewModelScope.launch { userDataRepository.savePreferredAbi(abiName) }
    }
}
