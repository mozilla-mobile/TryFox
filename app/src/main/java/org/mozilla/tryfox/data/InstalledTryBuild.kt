package org.mozilla.tryfox.data

import kotlinx.serialization.Serializable

/** Provenance for the Try build currently known to be installed on the device. */
@Serializable
data class InstalledTryBuild(
    val packageName: String,
    val project: String,
    val revision: String,
    val commitMessage: String,
    val versionName: String?,
    val versionCode: Long,
)
