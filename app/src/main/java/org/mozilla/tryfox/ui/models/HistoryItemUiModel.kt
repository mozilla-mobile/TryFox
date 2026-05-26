package org.mozilla.tryfox.ui.models

import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry

data class HistoryItemUiModel(
    val entry: TreeherderInstallHistoryEntry,
    val downloadState: DownloadState,
)
