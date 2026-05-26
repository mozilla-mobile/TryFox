package org.mozilla.tryfox.data

data class TreeherderInstallHistoryEntry(
    val project: String,
    val revision: String,
    val commitMessage: String,
    val author: String?,
    val pushTimestamp: Long,
    val appName: String,
    val jobName: String,
    val jobSymbol: String,
    val taskId: String,
    val artifactName: String,
    val artifactFileName: String,
    val downloadUrl: String,
    val abiName: String?,
    val abiSupported: Boolean,
    val expires: String,
    val cacheRelativePath: String,
    val lastInstallerLaunchTimestamp: Long,
) {
    val uniqueKey: String
        get() = "$taskId/$artifactFileName"
}
