package org.mozilla.tryfox.ui.models

import org.mozilla.tryfox.data.DownloadState

data class ArtifactUiModel(
    val name: String,
    val taskId: String,
    val abi: AbiUiModel,
    val downloadUrl: String,
    val expires: String,
    var downloadState: DownloadState,
    val uniqueKey: String,
)
