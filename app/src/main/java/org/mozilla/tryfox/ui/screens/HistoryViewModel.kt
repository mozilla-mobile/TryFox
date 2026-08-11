package org.mozilla.tryfox.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.repositories.HistoryRepository
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.download.ApkDownloadRequest
import org.mozilla.tryfox.download.model.DownloadStatus
import org.mozilla.tryfox.download.model.PersistedDownloadState
import org.mozilla.tryfox.install.ApkInstallCoordinator
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.install.TryBuildProvenance
import org.mozilla.tryfox.ui.models.HistoryItemUiModel
import org.mozilla.tryfox.util.TREEHERDER
import java.io.File

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val downloadCoordinator: ApkDownloadCoordinator,
    private val cacheManager: CacheManager,
    private val installCoordinator: ApkInstallCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private companion object {
        const val TAG = "HistoryViewModel"
    }

    private val downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    private val cacheRefreshEvents = MutableStateFlow(0)

    private val _historyItems = MutableStateFlow<List<HistoryItemUiModel>>(emptyList())
    val historyItems: StateFlow<List<HistoryItemUiModel>> = _historyItems.asStateFlow()
    val installStates: StateFlow<Map<String, InstallState>> = installCoordinator.states

    init {
        logcat(LogPriority.DEBUG, TAG) { "init" }
        viewModelScope.launch(ioDispatcher) {
            historyRepository.refresh()
            cacheManager.checkCacheStatus()
        }

        downloadCoordinator.downloads
            .onEach { persistedDownloads ->
                downloadStates.value = persistedDownloads.toDownloadStates()
            }
            .launchIn(viewModelScope)

        historyRepository.historyEntries
            .combine(cacheManager.cacheState) { entries, _ -> entries }
            .combine(cacheRefreshEvents) { entries, _ -> entries }
            .combine(downloadStates) { entries, states -> entries.toUiModels(states) }
            .onEach { _historyItems.value = it }
            .launchIn(viewModelScope)
    }

    fun refreshCachedDownloadStates() {
        logcat(LogPriority.DEBUG, TAG) { "refreshCachedDownloadStates called" }
        viewModelScope.launch(ioDispatcher) {
            cacheManager.checkCacheStatus()
            cacheRefreshEvents.update { it + 1 }
        }
    }

    fun download(historyItem: HistoryItemUiModel) {
        val entry = historyItem.entry
        val currentState = downloadStates.value[entry.uniqueKey] ?: historyItem.downloadState
        logcat(LogPriority.DEBUG, TAG) {
            "download requested uniqueKey=${entry.uniqueKey}, currentState=${currentState.javaClass.simpleName}, " +
                "historyItemState=${historyItem.downloadState.javaClass.simpleName}"
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

        val outputFile = getCachedFile(entry).selectedFile
        outputFile.parentFile?.mkdirs()
        logcat(LogPriority.DEBUG, TAG) {
            "download enqueued uniqueKey=${entry.uniqueKey}, url=${entry.downloadUrl}, " +
                "outputPath=${outputFile.absolutePath}, parentExists=${outputFile.parentFile?.exists()}, " +
                "preExisting=${outputFile.exists()}, preExistingLength=${outputFile.length()}"
        }
        downloadCoordinator.enqueue(
            ApkDownloadRequest(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputFile = outputFile,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                notificationTitle = formatJobNameForDisplay(entry.jobName),
                cacheRelativePath = entry.cacheRelativePath,
            ),
        )
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
            installCoordinator.install(
                historyItem.entry.uniqueKey,
                file,
                TryBuildProvenance(
                    project = historyItem.entry.project,
                    revision = historyItem.entry.revision,
                    commitMessage = historyItem.entry.commitMessage,
                ),
            )
        }
    }

    fun openInstalledApp(packageName: String) = installCoordinator.openInstalledApp(packageName)

    fun delete(historyItem: HistoryItemUiModel) {
        val uniqueKey = historyItem.entry.uniqueKey
        viewModelScope.launch(ioDispatcher) {
            try {
                if (downloadStates.value[uniqueKey] is DownloadState.InProgress) {
                    downloadCoordinator.cancel(uniqueKey)
                }
                deleteDownloadFiles(historyItem.entry)
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

    private fun Map<String, PersistedDownloadState>.toDownloadStates(): Map<String, DownloadState> =
        mapValues { (_, persistedState) -> persistedState.toDownloadState() }

    private fun PersistedDownloadState.toDownloadState(): DownloadState =
        when (status) {
            DownloadStatus.QUEUED,
            DownloadStatus.RUNNING,
                -> DownloadState.InProgress(
                progress = progress ?: 0f,
                isIndeterminate = totalBytes <= 0L,
            )
            DownloadStatus.SUCCEEDED -> {
                val file = File(outputPath)
                if (file.exists()) {
                    DownloadState.Downloaded(file)
                } else {
                    DownloadState.NotDownloaded
                }
            }
            DownloadStatus.FAILED -> DownloadState.DownloadFailed(errorMessage)
            DownloadStatus.CANCELED -> DownloadState.NotDownloaded
        }

    private fun List<TreeherderInstallHistoryEntry>.toUiModels(
        states: Map<String, DownloadState>,
    ): List<HistoryItemUiModel> =
        map { entry ->
            val cacheResolution = getCachedFile(entry)
            val rememberedState = states[entry.uniqueKey]
            val downloadState = when {
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
