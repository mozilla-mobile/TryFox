package org.mozilla.tryfox.ui.models

data class JobDetailsUiModel(
    val appName: String,
    val jobName: String,
    val jobSymbol: String,
    val taskId: String,
    val isSignedBuild: Boolean, // From original JobDetails
    val isTest: Boolean, // From original JobDetails
    var artifacts: List<ArtifactUiModel> = emptyList(), // To hold associated artifacts directly
)
