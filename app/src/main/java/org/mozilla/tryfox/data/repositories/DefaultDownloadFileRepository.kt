package org.mozilla.tryfox.data.repositories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.network.DownloadApiService
import java.io.File
import java.io.FileOutputStream

/**
 * Default implementation of [DownloadFileRepository] for downloading files.
 */
class DefaultDownloadFileRepository(
    private val downloadApiService: DownloadApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DownloadFileRepository {
    override suspend fun downloadFile(downloadUrl: String, outputFile: File, onProgress: (Long, Long) -> Unit): NetworkResult<File> {
        return withContext(ioDispatcher) {
            val partialFile = File(outputFile.parentFile, "${outputFile.name}.part")
            var backupFile = File(outputFile.parentFile, "${outputFile.name}.bak")
            try {
                outputFile.parentFile?.mkdirs()
                if (partialFile.exists()) {
                    partialFile.delete()
                }
                if (!outputFile.exists()) {
                    val latestBackupFile = findLatestBackupFile(outputFile)
                    if (latestBackupFile != null && !latestBackupFile.renameTo(outputFile)) {
                        return@withContext NetworkResult.Error(
                            "Failed to restore backup file: ${latestBackupFile.absolutePath}",
                            null,
                        )
                    }
                }
                if (outputFile.exists() && backupFile.exists() && !backupFile.delete()) {
                    backupFile = createReplacementBackupFile(outputFile)
                    logcat(LogPriority.WARN, TAG) {
                        "Could not delete stale backup, using replacement backup path=${backupFile.absolutePath}"
                    }
                }
                logcat(LogPriority.DEBUG, TAG) {
                    "downloadFile started url=$downloadUrl, outputPath=${outputFile.absolutePath}, " +
                        "parentExists=${outputFile.parentFile?.exists()}, existing=${outputFile.exists()}, " +
                        "existingLength=${outputFile.length()}, partialPath=${partialFile.absolutePath}"
                }
                val response = downloadApiService.downloadFile(downloadUrl)
                val body = response.byteStream()
                val totalBytes = response.contentLength()
                var bytesCopied: Long = 0
                val progressLogStepBytes = if (totalBytes > 0) {
                    (totalBytes / PROGRESS_LOG_SLICES).coerceAtLeast(1L)
                } else {
                    0L
                }
                var nextProgressLogBytes = progressLogStepBytes
                logcat(LogPriority.DEBUG, TAG) {
                    "downloadFile response url=$downloadUrl, contentLength=$totalBytes"
                }

                body.use { inputStream ->
                    FileOutputStream(partialFile).use { outputStream ->
                        val buffer = ByteArray(4 * 1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                            bytesCopied += read
                            onProgress(bytesCopied, totalBytes)
                            if (totalBytes > 0 && bytesCopied >= nextProgressLogBytes) {
                                logcat(LogPriority.DEBUG, TAG) {
                                    "downloadFile progress partialPath=${partialFile.absolutePath}, " +
                                        "bytesCopied=$bytesCopied, totalBytes=$totalBytes, " +
                                        "partialExists=${partialFile.exists()}, partialLength=${partialFile.length()}"
                                }
                                nextProgressLogBytes += progressLogStepBytes
                            }
                        }
                    }
                }

                if (totalBytes >= 0 && bytesCopied != totalBytes) {
                    partialFile.delete()
                    return@withContext NetworkResult.Error(
                        "Failed to download file: expected $totalBytes bytes but received $bytesCopied",
                        null,
                    )
                }

                val hadExistingOutput = outputFile.exists()
                if (hadExistingOutput && !outputFile.renameTo(backupFile)) {
                    partialFile.delete()
                    return@withContext NetworkResult.Error(
                        "Failed to prepare existing file for replacement: ${outputFile.absolutePath}",
                        null,
                    )
                }

                if (!partialFile.renameTo(outputFile)) {
                    partialFile.delete()
                    if (backupFile.exists()) {
                        backupFile.renameTo(outputFile)
                    }
                    return@withContext NetworkResult.Error(
                        "Failed to move downloaded file into place: ${outputFile.absolutePath}",
                        null,
                    )
                }
                if (backupFile.exists() && !backupFile.delete()) {
                    logcat(LogPriority.WARN, TAG) {
                        "Failed to delete backup after successful replacement path=${backupFile.absolutePath}"
                    }
                }

                logcat(LogPriority.DEBUG, TAG) {
                    "downloadFile finished outputPath=${outputFile.absolutePath}, bytesCopied=$bytesCopied, " +
                        "totalBytes=$totalBytes, exists=${outputFile.exists()}, length=${outputFile.length()}"
                }
                NetworkResult.Success(outputFile)
            } catch (e: Exception) {
                partialFile.delete()
                if (backupFile.exists() && !outputFile.exists()) {
                    backupFile.renameTo(outputFile)
                }
                logcat(LogPriority.ERROR, TAG) {
                    "downloadFile failed outputPath=${outputFile.absolutePath}, partialPath=${partialFile.absolutePath}, " +
                        "partialExists=${partialFile.exists()}, message=${e.message}"
                }
                NetworkResult.Error("Failed to download file: ${e.message}", e)
            }
        }
    }

    private companion object {
        const val TAG = "DefaultDownloadFileRepository"
        const val PROGRESS_LOG_SLICES = 10

        fun createReplacementBackupFile(outputFile: File): File {
            var index = 1
            while (true) {
                val candidate = File(outputFile.parentFile, "${outputFile.name}.bak.$index")
                if (!candidate.exists()) {
                    return candidate
                }
                index += 1
            }
        }

        fun findLatestBackupFile(outputFile: File): File? =
            outputFile.parentFile
                ?.listFiles { file -> file.isFile && file.isManagedBackupFile(outputFile) }
                ?.maxWithOrNull(
                    compareBy<File> { it.backupSequence(outputFile) }
                        .thenBy { it.lastModified() },
                )

        fun File.isManagedBackupFile(outputFile: File): Boolean = backupSequence(outputFile) >= 0

        fun File.backupSequence(outputFile: File): Int {
            val baseBackupName = "${outputFile.name}.bak"
            if (name == baseBackupName) {
                return 0
            }
            if (!name.startsWith("$baseBackupName.")) {
                return -1
            }
            return name
                .removePrefix("$baseBackupName.")
                .toIntOrNull()
                ?: -1
        }
    }
}
