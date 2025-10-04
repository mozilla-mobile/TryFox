package org.mozilla.tryfox.ui.screens

import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.models.AppUiModel

/**
 * Represents the various states of the Home screen.
 */
sealed class HomeScreenState {
    /**
     * Initial loading state when the ViewModel is fetching initial data.
     */
    data object InitialLoading : HomeScreenState()

    /**
     * State when the main data for the screen has been loaded.
     */
    data class Loaded(
        val apps: Map<String, AppUiModel>,
        val cacheManagementState: CacheManagementState,
        val isDownloadingAnyFile: Boolean,
    ) : HomeScreenState()
}
