package org.mozilla.tryfox.ui.screens

import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.models.ApksState

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
        val fenixBuildsState: ApksState,
        val focusBuildsState: ApksState,
        val referenceBrowserBuildsState: ApksState,
        val cacheManagementState: CacheManagementState,
        val isDownloadingAnyFile: Boolean,
    ) : HomeScreenState()

    // Consider if a global error state for the whole screen is needed,
    // e.g., if initial cache access fails catastrophically.
    // For now, errors related to fetching builds are handled within FocusApksState.
}
