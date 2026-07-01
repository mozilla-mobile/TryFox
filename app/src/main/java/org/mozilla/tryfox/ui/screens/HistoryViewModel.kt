package org.mozilla.tryfox.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.managers.IntentManager
import org.mozilla.tryfox.data.repositories.DownloadFileRepository
import org.mozilla.tryfox.data.repositories.HistoryRepository
import org.mozilla.tryfox.ui.models.HistoryItemUiModel
import org.mozilla.tryfox.util.TREEHERDER
import java.io.File

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val downloadFileRepository: DownloadFileRepository,
    private val cacheManager: CacheManager,
    private val intentManager: IntentManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private companion object {
        const val TAG = "HistoryViewModel"
    }

    private val downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    private val activeDownloads = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    private val canceledDownloads = MutableStateFlow<Map<DownloadIdentity, TreeherderInstallHistoryEntry>>(emptyMap())
    private val cacheRefreshEvents = MutableStateFlow(0)
    private var nextDownloadGeneration = 0L

    private val _historyItems = MutableStateFlow<List<HistoryItemUiModel>>(emptyList())
    val historyItems: StateFlow<List<HistoryItemUiModel>> = _historyItems.asStateFlow()

    init {
        logcat(LogPriority.DEBUG, TAG) { "init" }
        viewModelScope.launch {
            historyRepository.refresh()
            cacheManager.checkCacheStatus()
        }

        historyRepository.historyEntries
            .combine(cacheManager.cacheState) { entries, _ -> entries }
            .combine(cacheRefreshEvents) { entries, _ -> entries }
            .combine(downloadStates) { entries, states -> entries.toUiModels(states) }
            .onEach { _historyItems.value = it }
            .launchIn(viewModelScope)
    }

    fun refreshCachedDownloadStates() {
        logcat(LogPriority.DEBUG, TAG) { "refreshCachedDownloadStates called" }
        cacheManager.checkCacheStatus()
        cacheRefreshEvents.update { it + 1 }
    }

    fun download(historyItem: HistoryItemUiModel) {
        val entry = historyItem.entry
        val currentState = downloadStates.value[entry.uniqueKey] ?: historyItem.downloadState
        logcat(LogPriority.DEBUG, TAG) {
            "download requested uniqueKey=${entry.uniqueKey}, currentState=${currentState.javaClass.simpleName}, " +
                "historyItemState=${historyItem.downloadState.javaClass.simpleName}"
        }
        if (canceledDownloads.value.keys.any { it.uniqueKey == entry.uniqueKey }) {
            logcat(LogPriority.DEBUG, TAG) {
                "download ignored because a canceled download is still finishing uniqueKey=${entry.uniqueKey}"
            }
            return
        }
        when (currentState) {
            is DownloadState.InProgress -> {
                logcat(LogPriority.DEBUG, TAG) {
                    "download ignored because already in progress uniqueKey=${entry.uniqueKey}"
                }
                return
            }
            is DownloadState.Downloaded -> {
                if (currentState.file.exists()) {
                    logcat(LogPriority.DEBUG, TAG) {
                        "download ignored because remembered file exists uniqueKey=${entry.uniqueKey}, " +
                            "path=${currentState.file.absolutePath}, length=${currentState.file.length()}"
                    }
                    return
                }
                logcat(LogPriority.DEBUG, TAG) {
                    "download retrying because remembered file is missing uniqueKey=${entry.uniqueKey}, " +
                        "path=${currentState.file.absolutePath}"
                }
                updateDownloadState(entry.uniqueKey, DownloadState.NotDownloaded)
            }
            else -> Unit
        }

        val generation = nextDownloadGeneration++
        lateinit var downloadJob: Job
        downloadJob = viewModelScope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
            updateDownloadStateIfActive(entry.uniqueKey, generation, DownloadState.InProgress(0f))
            val outputFile = getCachedFile(entry).selectedFile
            outputFile.parentFile?.mkdirs()
            logcat(LogPriority.DEBUG, TAG) {
                "download started uniqueKey=${entry.uniqueKey}, url=${entry.downloadUrl}, " +
                    "outputPath=${outputFile.absolutePath}, parentExists=${outputFile.parentFile?.exists()}, " +
                    "preExisting=${outputFile.exists()}, preExistingLength=${outputFile.length()}"
            }

            when (
                val result = downloadFileRepository.downloadFile(
                    downloadUrl = entry.downloadUrl,
                    outputFile = outputFile,
                    onProgress = { bytesDownloaded, totalBytes ->
                        val progress = if (totalBytes > 0) {
                            bytesDownloaded.toFloat() / totalBytes.toFloat()
                        } else {
                            0f
                        }
                        updateDownloadStateIfActive(
                            entry.uniqueKey,
                            generation,
                            DownloadState.InProgress(progress),
                        )
                    },
                )
            ) {
                is NetworkResult.Success -> {
                    logcat(LogPriority.DEBUG, TAG) {
                        "download repository success uniqueKey=${entry.uniqueKey}, " +
                            "resultPath=${result.data.absolutePath}, resultExists=${result.data.exists()}, " +
                            "resultLength=${result.data.length()}, outputExists=${outputFile.exists()}, " +
                            "outputLength=${outputFile.length()}, parentExists=${outputFile.parentFile?.exists()}"
                    }
                    val downloadedFile = result.data.takeIf { it.exists() } ?: outputFile.takeIf { it.exists() }
                    if (downloadedFile == null) {
                        logcat(LogPriority.ERROR, TAG) {
                            "download success but file is missing uniqueKey=${entry.uniqueKey}, " +
                                "resultPath=${result.data.absolutePath}, outputPath=${outputFile.absolutePath}"
                        }
                        updateDownloadStateIfActive(
                            entry.uniqueKey,
                            generation,
                            DownloadState.DownloadFailed("Downloaded file is missing"),
                        )
                    } else {
                        logcat(LogPriority.DEBUG, TAG) {
                            "download marked downloaded uniqueKey=${entry.uniqueKey}, " +
                                "path=${downloadedFile.absolutePath}, length=${downloadedFile.length()}"
                        }
                        updateDownloadStateIfActive(
                            entry.uniqueKey,
                            generation,
                            DownloadState.Downloaded(downloadedFile),
                        )
                    }
                    cacheManager.checkCacheStatus()
                    cacheRefreshEvents.update { it + 1 }
                }
                is NetworkResult.Error -> {
                    logcat(LogPriority.ERROR, TAG) {
                        "download repository error uniqueKey=${entry.uniqueKey}, message=${result.message}"
                    }
                    updateDownloadStateIfActive(
                        entry.uniqueKey,
                        generation,
                        DownloadState.DownloadFailed(result.message),
                    )
                    cacheManager.checkCacheStatus()
                    cacheRefreshEvents.update { it + 1 }
                }
            }
        }
        val activeDownload = ActiveDownload(
            job = downloadJob,
            generation = generation,
            entry = entry,
        )
        val downloadIdentity = DownloadIdentity(entry.uniqueKey, generation)
        activeDownloads.update { it + (entry.uniqueKey to activeDownload) }
        downloadJob.invokeOnCompletion {
            activeDownloads.update { downloads ->
                if (downloads[entry.uniqueKey]?.generation == generation) {
                    downloads - entry.uniqueKey
                } else {
                    downloads
                }
            }
            canceledDownloads.value[downloadIdentity]?.let { canceledEntry ->
                if (activeDownloads.value[canceledEntry.uniqueKey] == null) {
                    deleteDownloadFiles(canceledEntry)
                    cacheManager.checkCacheStatus()
                    cacheRefreshEvents.update { it + 1 }
                }
                canceledDownloads.update { it - downloadIdentity }
            }
        }
        downloadJob.start()
    }

    fun install(historyItem: HistoryItemUiModel, file: File) {
        viewModelScope.launch {
            try {
                historyRepository.upsertHistoryEntry(
                    historyItem.entry.copy(lastInstallerLaunchTimestamp = currentTimeMillisProvider()),
                )
            } catch (_: Exception) {
                // History is best-effort; never block installation.
            }
            intentManager.installApk(file)
        }
    }

    fun delete(historyItem: HistoryItemUiModel) {
        val uniqueKey = historyItem.entry.uniqueKey
        viewModelScope.launch(ioDispatcher) {
            try {
                activeDownloads.value[uniqueKey]?.let { activeDownload ->
                    val downloadIdentity = DownloadIdentity(uniqueKey, activeDownload.generation)
                    canceledDownloads.update { it + (downloadIdentity to activeDownload.entry) }
                    activeDownloads.update { downloads ->
                        if (downloads[uniqueKey]?.generation == activeDownload.generation) {
                            downloads - uniqueKey
                        } else {
                            downloads
                        }
                    }
                    activeDownload.job.cancel()
                    deleteDownloadFiles(activeDownload.entry)
                }
                historyRepository.delete(uniqueKey)
                downloadStates.update { it - uniqueKey }
                cacheManager.checkCacheStatus()
                cacheRefreshEvents.update { it + 1 }
            } catch (exception: Exception) {
                logcat(LogPriority.ERROR, TAG) {
                    "delete failed uniqueKey=$uniqueKey\n${exception.stackTraceToString()}"
                }
            }
        }
    }

    private fun updateDownloadState(uniqueKey: String, downloadState: DownloadState) {
        downloadStates.update { it + (uniqueKey to downloadState) }
    }

    private fun updateDownloadStateIfActive(
        uniqueKey: String,
        generation: Long,
        downloadState: DownloadState,
    ) {
        if (activeDownloads.value[uniqueKey]?.generation == generation) {
            updateDownloadState(uniqueKey, downloadState)
        } else {
            logcat(LogPriority.DEBUG, TAG) {
                "ignored stale download state uniqueKey=$uniqueKey, generation=$generation, " +
                    "state=${downloadState.javaClass.simpleName}"
            }
        }
    }

    private fun List<TreeherderInstallHistoryEntry>.toUiModels(
        states: Map<String, DownloadState>,
    ): List<HistoryItemUiModel> =
        map { entry ->
            val cacheResolution = getCachedFile(entry)
            val rememberedState = states[entry.uniqueKey]
            val isCanceledDownloadFinishing = canceledDownloads.value.keys.any { it.uniqueKey == entry.uniqueKey }
            val downloadState = when {
                isCanceledDownloadFinishing -> DownloadState.InProgress(0f, isIndeterminate = true)
                rememberedState is DownloadState.InProgress -> rememberedState
                rememberedState is DownloadState.DownloadFailed -> rememberedState

                rememberedState is DownloadState.Downloaded && rememberedState.file.exists() -> rememberedState
                cacheResolution.selectedFile.exists() -> DownloadState.Downloaded(cacheResolution.selectedFile)
                else -> DownloadState.NotDownloaded
            }
            logcat(LogPriority.DEBUG, TAG) {
                "history item resolved uniqueKey=${entry.uniqueKey}, taskId=${entry.taskId}, " +
                    "artifactName=${entry.artifactName}, artifactFileName=${entry.artifactFileName}, " +
                    "jobSymbol=${entry.jobSymbol}, cacheRelativePath=${entry.cacheRelativePath}, " +
                    "relativePath=${cacheResolution.relativePathFile.absolutePath}, " +
                    "relativeExists=${cacheResolution.relativePathFile.exists()}, " +
                    "fallbackPath=${cacheResolution.fallbackFile.absolutePath}, " +
                    "fallbackExists=${cacheResolution.fallbackFile.exists()}, " +
                    "selectedPath=${cacheResolution.selectedFile.absolutePath}, " +
                    "rememberedState=${rememberedState?.javaClass?.simpleName}, " +
                    "resolvedState=${downloadState.javaClass.simpleName}"
            }

            HistoryItemUiModel(
                entry = entry,
                downloadState = downloadState,
            )
        }

    private fun getCachedFile(entry: TreeherderInstallHistoryEntry): CacheResolution {
        val treeherderCacheDir = cacheManager.getCacheDir(TREEHERDER)
        val relativePathFile = File(treeherderCacheDir.parentFile, entry.cacheRelativePath)
        val fallbackFile = File(treeherderCacheDir, "${entry.taskId}/${entry.artifactFileName}")
        val selectedFile = if (entry.cacheRelativePath.isNotBlank() && relativePathFile.exists()) {
            relativePathFile
        } else {
            fallbackFile
        }
        return CacheResolution(
            relativePathFile = relativePathFile,
            fallbackFile = fallbackFile,
            selectedFile = selectedFile,
        )
    }

    private data class CacheResolution(
        val relativePathFile: File,
        val fallbackFile: File,
        val selectedFile: File,
    )

    private data class ActiveDownload(
        val job: Job,
        val generation: Long,
        val entry: TreeherderInstallHistoryEntry,
    )

    private data class DownloadIdentity(
        val uniqueKey: String,
        val generation: Long,
    )

    private fun deleteDownloadFiles(entry: TreeherderInstallHistoryEntry) {
        val cacheResolution = getCachedFile(entry)
        setOf(
            cacheResolution.relativePathFile,
            cacheResolution.fallbackFile,
            cacheResolution.selectedFile,
        ).forEach(::deleteDownloadFilesForOutput)
    }

    private fun deleteDownloadFilesForOutput(outputFile: File) {
        val relatedFiles = buildList {
            add(outputFile)
            add(File(outputFile.parentFile, "${outputFile.name}.part"))
            add(File(outputFile.parentFile, "${outputFile.name}.bak"))
            outputFile.parentFile?.listFiles { file ->
                file.isFile && file.isManagedNumberedBackupFile(outputFile)
            }?.let(::addAll)
        }
        relatedFiles.forEach { file ->
            if (file.exists() && !file.delete()) {
                logcat(LogPriority.WARN, TAG) {
                    "failed to delete history download file path=${file.absolutePath}"
                }
            }
        }
    }

    private fun File.isManagedNumberedBackupFile(outputFile: File): Boolean {
        val backupPrefix = "${outputFile.name}.bak."
        if (!name.startsWith(backupPrefix)) {
            return false
        }
        return name.removePrefix(backupPrefix).toIntOrNull() != null
    }
}
