package org.mozilla.tryfox.download

import java.io.File

data class ApkDownloadRequest(
    val uniqueKey: String,
    val downloadUrl: String,
    val outputFile: File,
    val appName: String,
    val fileName: String,
    val cacheRelativePath: String? = null,
) {
    val outputPath: String = outputFile.absolutePath
}
