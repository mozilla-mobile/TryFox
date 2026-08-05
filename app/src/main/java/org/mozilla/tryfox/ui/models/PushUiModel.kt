package org.mozilla.tryfox.ui.models

data class PushUiModel(
    /** Treeherder project that produced this push and its artifacts. */
    val project: String,
    val pushComment: String,
    val author: String,
    val jobs: List<JobDetailsUiModel>,
    val revision: String?,
    val pushTimestamp: Long,
    val unsignedJobs: List<JobDetailsUiModel> = emptyList(),
)
