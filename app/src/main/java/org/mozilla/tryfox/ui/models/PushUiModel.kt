package org.mozilla.tryfox.ui.models

data class PushUiModel(
    val pushComment: String,
    val author: String,
    val jobs: List<JobDetailsUiModel>,
    val revision: String?,
)
