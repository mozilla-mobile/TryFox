package org.mozilla.fenixinstaller

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.fenixinstaller.data.DownloadState
import org.mozilla.fenixinstaller.data.IFenixRepository // Changed to interface
import org.mozilla.fenixinstaller.data.NetworkResult
import org.mozilla.fenixinstaller.model.CacheManagementState
import org.mozilla.fenixinstaller.ui.models.AbiUiModel
import org.mozilla.fenixinstaller.ui.models.ArtifactUiModel
import org.mozilla.fenixinstaller.ui.models.JobDetailsUiModel
import java.io.File

class FenixInstallerViewModel(
    private val repository: IFenixRepository // Changed to interface
) : ViewModel() {
    var revision by mutableStateOf("c2f3f652a3a063cb7933c2781038a25974cd09ec")
        private set

    var selectedProject by mutableStateOf("try")
        private set

    var relevantPushComment by mutableStateOf<String?>(null)
        private set

    var relevantPushAuthor by mutableStateOf<String?>(null) // Changed to String?
        private set

    var isLoading by mutableStateOf(false) // For job/artifact search loading
        private set

    private val _cacheState = MutableStateFlow<CacheManagementState>(CacheManagementState.IdleEmpty)
    val cacheState: StateFlow<CacheManagementState> = _cacheState.asStateFlow()

    private val _isDownloadingAnyFile = MutableStateFlow(false)
    val isDownloadingAnyFile: StateFlow<Boolean> = _isDownloadingAnyFile.asStateFlow()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedJobs by mutableStateOf<List<JobDetailsUiModel>>(emptyList())
        private set

    val isLoadingJobArtifacts = mutableStateMapOf<String, Boolean>()

    var onInstallApk: ((File) -> Unit)? = null

    private val deviceSupportedAbis: List<String> by lazy { Build.SUPPORTED_ABIS.toList() }

    private fun checkAndUpdateDownloadingStatus() {
        val isDownloading = selectedJobs.any { job ->
            job.artifacts.any { artifact -> artifact.downloadState is DownloadState.InProgress }
        }
        _isDownloadingAnyFile.value = isDownloading
    }

    fun updateRevision(newRevision: String) {
        Log.d("FenixInstallerViewModel", "Updating revision to: $newRevision")
        revision = newRevision
    }

    fun updateSelectedProject(newProject: String) {
        Log.d("FenixInstallerViewModel", "Updating selected project to: $newProject")
        selectedProject = newProject
    }

    fun setRevisionFromDeepLinkAndSearch(project: String?, newRevision: String, context: Context) {
        Log.i("FenixInstallerViewModel", "Setting project to: ${project ?: "default (try)"}, revision from deep link to: $newRevision and triggering search.")
        selectedProject = project ?: "try"
        revision = newRevision
        relevantPushComment = null
        relevantPushAuthor = null // Reset author
        selectedJobs = emptyList()
        isLoadingJobArtifacts.clear()
        errorMessage = null
        searchJobsAndArtifacts(context)
    }

    fun getDownloadedFile(artifactName: String, context: Context, taskId: String): File? {
        if (taskId.isBlank()) return null
        val taskSpecificDir = File(context.cacheDir, taskId)
        val outputFile = File(taskSpecificDir, artifactName)
        if (outputFile.exists()) {
            if (_cacheState.value == CacheManagementState.IdleEmpty) {
                _cacheState.value = CacheManagementState.IdleNonEmpty
            }
            return outputFile
        }
        return null
    }

    fun checkCacheStatus(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                val isEmpty = !(cacheDir.exists() &&
                                (cacheDir.listFiles()?.any { file ->
                                    file.isFile || (file.isDirectory && file.listFiles()?.isNotEmpty() == true)
                                } ?: false))
                _cacheState.value = if (isEmpty) CacheManagementState.IdleEmpty else CacheManagementState.IdleNonEmpty
                Log.d("FenixInstallerViewModel", "Cache status checked. State: ${_cacheState.value}")
            } catch (e: Exception) {
                Log.e("FenixInstallerViewModel", "Error checking cache status", e)
                _cacheState.value = CacheManagementState.IdleEmpty
            }
        }
    }

    fun clearAppCache(context: Context) {
        viewModelScope.launch {
            _cacheState.value = CacheManagementState.Clearing
            try {
                withContext(Dispatchers.IO) {
                    val cacheDir = context.cacheDir
                    if (cacheDir.exists()) {
                        val deleted = cacheDir.deleteRecursively()
                        Log.d("FenixInstallerViewModel", "Cache directory delete attempt result: $deleted")
                    }
                }
                _cacheState.value = CacheManagementState.IdleEmpty
                Log.d("FenixInstallerViewModel", "Cache cleared. State: ${_cacheState.value}")

                val updatedSelectedJobs = selectedJobs.map {
                    val updatedArtifacts = it.artifacts.map {
                        it.copy(downloadState = DownloadState.NotDownloaded)
                    }
                    it.copy(artifacts = updatedArtifacts)
                }
                selectedJobs = updatedSelectedJobs
            } catch (e: Exception) {
                Log.e("FenixInstallerViewModel", "Error clearing cache", e)
                checkCacheStatus(context)
            } finally {
                checkAndUpdateDownloadingStatus()
            }
        }
    }

    fun searchJobsAndArtifacts(context: Context) {
        if (revision.isBlank()) {
            errorMessage = "Please enter a revision to search."
            return
        }
        Log.d("FenixInstallerViewModel", "Starting job/artifact search for project: $selectedProject, revision: $revision")

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            relevantPushComment = null
            relevantPushAuthor = null // Reset author
            selectedJobs = emptyList()
            isLoadingJobArtifacts.clear()
            checkCacheStatus(context)
            checkAndUpdateDownloadingStatus() // Initial check before fetching

            when (val revisionResult = repository.getPushByRevision(selectedProject, revision)) {
                is NetworkResult.Success -> {
                    val pushData = revisionResult.data
                    var foundComment: String? = null
                    val firstPushResult = pushData.results.firstOrNull()

                    if (firstPushResult != null) {
                        for (revDetail in firstPushResult.revisions) {
                            if (revDetail.comments.startsWith("Bug ")) {
                                foundComment = revDetail.comments
                                break
                            }
                        }
                        relevantPushAuthor = firstPushResult.author // Use raw string
                    } else {
                        relevantPushAuthor = null // No push result, no author
                    }
                    relevantPushComment = foundComment

                    if (pushData.results.isEmpty()) {
                        errorMessage = "No push found for project: $selectedProject, revision: $revision"
                        isLoading = false
                        return@launch
                    }
                    val pushId = pushData.results.first().id
                    Log.d("FenixInstallerViewModel", "Found push ID: $pushId for project: $selectedProject, revision: $revision")
                    fetchJobs(pushId, context)
                }
                is NetworkResult.Error -> {
                    errorMessage = "Error fetching revision details for $selectedProject: ${revisionResult.message}"
                    isLoading = false
                }
            }
        }
    }

    private suspend fun fetchJobs(pushId: Int, context: Context) {
        Log.d("FenixInstallerViewModel", "Fetching jobs for push ID: $pushId")
        when (val jobsResult = repository.getJobsForPush(pushId)) {
            is NetworkResult.Success -> {
                val networkJobDetailsList = jobsResult.data.results
                    .filter { it.isSignedBuild && !it.isTest }

                if (networkJobDetailsList.isEmpty()) {
                    errorMessage = "No jobs found matching the criteria for this push."
                } else {
                    val initialJobUiModels = networkJobDetailsList.map { netJob ->
                        Log.i("FenixInstallerViewModel", "Preparing to fetch artifacts for job: '${netJob.jobName}' (TaskID: ${netJob.taskId})")
                        isLoadingJobArtifacts[netJob.taskId] = true
                        JobDetailsUiModel(
                            appName = netJob.appName,
                            jobName = netJob.jobName,
                            jobSymbol = netJob.jobSymbol,
                            taskId = netJob.taskId,
                            isSignedBuild = netJob.isSignedBuild,
                            isTest = netJob.isTest,
                            artifacts = emptyList()
                        )
                    }

                    val updatedJobUiModels = initialJobUiModels.map {
                        viewModelScope.async(Dispatchers.IO) { 
                            val fetchedArtifacts = fetchArtifacts(it.taskId, context)
                            isLoadingJobArtifacts[it.taskId] = false
                            it.copy(artifacts = fetchedArtifacts)
                        }
                    }.awaitAll()

                    val finalJobsToShow = updatedJobUiModels.filter { it.artifacts.isNotEmpty() }

                    if (finalJobsToShow.isEmpty()) {
                        errorMessage = "Selected jobs found, but no APKs in any of them. Check build logs."
                    }
                    selectedJobs = finalJobsToShow
                }
            }
            is NetworkResult.Error -> {
                errorMessage = "Error fetching jobs: ${jobsResult.message}"
                Log.e("FenixInstallerViewModel", "Error fetching/parsing jobs for push ID $pushId: ${jobsResult.message}", jobsResult.cause)
            }
        }
        isLoading = false
        checkAndUpdateDownloadingStatus()
    }

    private suspend fun fetchArtifacts(taskId: String, context: Context): List<ArtifactUiModel> {
        Log.d("FenixInstallerViewModel", "Fetching artifacts for task ID: $taskId")
        return when (val artifactsResult = repository.getArtifactsForTask(taskId)) {
            is NetworkResult.Success -> {
                val filteredApks = artifactsResult.data.artifacts.filter {
                    it.name.endsWith(".apk", ignoreCase = true)
                }
                Log.i("FenixInstallerViewModel", "Found ${filteredApks.size} APK(s) for task ID: $taskId.")
                if (filteredApks.isEmpty()) {
                    Log.w("FenixInstallerViewModel", "No APKs found for task ID: $taskId. Check the build logs.")
                }
                filteredApks.map { artifact ->
                    val artifactFileName = artifact.name.substringAfterLast('/')
                    val downloadedFile = getDownloadedFile(artifactFileName, context, taskId) 
                    val downloadState = if (downloadedFile != null) {
                        DownloadState.Downloaded(downloadedFile)
                    } else {
                        DownloadState.NotDownloaded
                    }
                    val isCompatible = artifact.abi != null && deviceSupportedAbis.any { deviceAbi -> 
                        deviceAbi.equals(artifact.abi, ignoreCase = true)
                    }
                    ArtifactUiModel(
                        name = artifact.name,
                        taskId = taskId,
                        abi = AbiUiModel(
                            name = artifact.abi,
                            isSupported = isCompatible
                        ),
                        downloadUrl = artifact.getDownloadUrl(taskId),
                        expires = artifact.expires,
                        downloadState = downloadState,
                        uniqueKey = "$taskId/${artifact.name.substringAfterLast('/')}",
                    )
                }
            }
            is NetworkResult.Error -> {
                Log.e("FenixInstallerViewModel", "Error fetching artifacts for task ID $taskId: ${artifactsResult.message}", artifactsResult.cause)
                emptyList()
            }
        }
    }

    fun downloadArtifact(artifactUiModel: ArtifactUiModel, context: Context) {
        val artifactFileName = artifactUiModel.name.substringAfterLast('/')
        val taskId = artifactUiModel.taskId
        val downloadKey = artifactUiModel.uniqueKey

        if (artifactUiModel.downloadState is DownloadState.InProgress || artifactUiModel.downloadState is DownloadState.Downloaded) {
            Log.d("FenixInstallerViewModel", "Download action for $downloadKey - already in progress or downloaded. State: ${artifactUiModel.downloadState}")
            return
        }
         if (taskId.isBlank()) {
            val blankTaskIdMsg = "Task ID is blank for $artifactFileName"
            Log.e("FenixInstallerViewModel", blankTaskIdMsg)
            updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.DownloadFailed(blankTaskIdMsg))
            return
        }

        viewModelScope.launch {
            updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.InProgress(0f))
            if (_cacheState.value == CacheManagementState.IdleEmpty) { 
                _cacheState.value = CacheManagementState.IdleNonEmpty
            }

            Log.i("FenixInstallerViewModel", "Starting download for $downloadKey")

            val downloadUrl = artifactUiModel.downloadUrl
            
            val outputDir = File(context.cacheDir, taskId)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val outputFile = File(outputDir, artifactFileName)

            val result = repository.downloadArtifact(
                downloadUrl = downloadUrl,
                outputFile = outputFile,
                onProgress = { bytesDownloaded, totalBytes ->
                    val progress = if (totalBytes > 0) {
                        bytesDownloaded.toFloat() / totalBytes.toFloat()
                    } else {
                        0f 
                    }
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.InProgress(progress))
                    Log.d("FenixInstallerViewModel", "Download progress for $downloadKey: ${(progress * 100).toInt()}%")
                }
            )

            when (result) {
                is NetworkResult.Success -> {
                    Log.i("FenixInstallerViewModel", "Download completed for $downloadKey at ${result.data.absolutePath}")
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.Downloaded(result.data))
                    if (_cacheState.value == CacheManagementState.IdleEmpty) { 
                        _cacheState.value = CacheManagementState.IdleNonEmpty
                    }
                    onInstallApk?.invoke(result.data)
                }
                is NetworkResult.Error -> {
                    val failureMessage = "Download failed for $artifactFileName: ${result.message}"
                    Log.e("FenixInstallerViewModel", failureMessage, result.cause)
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.DownloadFailed(result.message))
                    checkCacheStatus(context)
                }
            }
        }
    }

    private fun updateArtifactDownloadState(taskIdToUpdate: String, artifactNameToUpdate: String, newState: DownloadState) {
        selectedJobs = selectedJobs.map {
            if (it.taskId == taskIdToUpdate) {
                it.copy(artifacts = it.artifacts.map {
                    if (it.name == artifactNameToUpdate) {
                        it.copy(downloadState = newState)
                    } else it
                })
            } else it
        }
        checkAndUpdateDownloadingStatus()
    }
}
