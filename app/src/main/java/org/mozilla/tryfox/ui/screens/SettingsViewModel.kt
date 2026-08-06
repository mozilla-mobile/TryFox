package org.mozilla.tryfox.ui.screens

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

data class SettingsUiState(
    val cacheState: CacheManagementState = CacheManagementState.IdleEmpty,
    val cacheSizeBytes: Long = 0L,
    val hasActiveDownloads: Boolean = false,
    val homeScreenLayout: HomeScreenLayout = HomeScreenLayout.OneCardPerApp,
) {
    val canClearCache: Boolean
        get() = cacheState == CacheManagementState.IdleNonEmpty && !hasActiveDownloads
}

class SettingsViewModel(
    private val cacheManager: CacheManager,
    downloadCoordinator: ApkDownloadCoordinator,
    private val userDataRepository: UserDataRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        cacheManager.cacheState,
        cacheManager.cacheSizeBytes,
        downloadCoordinator.downloads,
        userDataRepository.homeScreenLayoutFlow,
    ) { cacheState, cacheSizeBytes, downloads, homeScreenLayout ->
        SettingsUiState(
            cacheState = cacheState,
            cacheSizeBytes = cacheSizeBytes,
            hasActiveDownloads = downloads.values.any { !it.isTerminal },
            homeScreenLayout = homeScreenLayout,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

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
}
