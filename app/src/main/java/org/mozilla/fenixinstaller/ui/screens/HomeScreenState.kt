package org.mozilla.fenixinstaller.ui.screens

import org.mozilla.fenixinstaller.model.AppState
import org.mozilla.fenixinstaller.model.CacheManagementState
import org.mozilla.fenixinstaller.ui.models.FocusApksState

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
     * It contains individual states for Fenix builds, Focus builds, app information,
     * and cache status.
     */
    data class Loaded(
        val fenixBuildsState: FocusApksState,
        val focusBuildsState: FocusApksState,
        val fenixAppInfo: AppState?,
        val focusAppInfo: AppState?,
        val cacheManagementState: CacheManagementState,
        val isDownloadingAnyFile: Boolean // Derived from fenixBuildsState and focusBuildsState
    ) : HomeScreenState()

    // Consider if a global error state for the whole screen is needed,
    // e.g., if initial cache access fails catastrophically.
    // For now, errors related to fetching builds are handled within FocusApksState.
}
