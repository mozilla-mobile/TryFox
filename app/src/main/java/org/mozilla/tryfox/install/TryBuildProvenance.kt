package org.mozilla.tryfox.install

data class TryBuildProvenance(
    val project: String,
    val revision: String,
    val commitMessage: String,
)
