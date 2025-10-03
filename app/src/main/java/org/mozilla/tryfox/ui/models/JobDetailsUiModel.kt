package org.mozilla.tryfox.ui.models

data class JobDetailsUiModel(
    val appName: String,
    val jobName: String,
    val jobSymbol: String,
    val taskId: String,
    val isSignedBuild: Boolean,
    val isTest: Boolean,
    var artifacts: List<ArtifactUiModel> = emptyList(),
)
