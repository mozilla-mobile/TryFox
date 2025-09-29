package org.mozilla.tryfox.ui.models

data class PushUiModel(
    val pushComment: String,
    val author: String, // Changed to String
    val jobs: List<JobDetailsUiModel>,
    val revision: String? // Added revision field
)
