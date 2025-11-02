package org.mozilla.tryfox

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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.IFenixRepository
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ArtifactUiModel
import org.mozilla.tryfox.ui.models.JobDetailsUiModel
import java.io.File

/**
 * ViewModel for the TryFox feature, responsible for fetching job and artifact data from the repository,
 * managing the download and caching of artifacts, and exposing the UI state to the composable screens.
 *
 * @param repository The repository for fetching data from the network.
 * @param cacheManager The manager for handling application cache.
 * @param revision The initial revision to search for.
 * @param repo The initial repository to search in.
 */
class TryFoxViewModel(
    private val repository: IFenixRepository,
    private val cacheManager: CacheManager,
    revision: String?,
    repo: String?,
) : ViewModel() {
    var revision by mutableStateOf(revision ?: "")
        private set

    var selectedProject by mutableStateOf(repo ?: "try")
        private set

    var relevantPushComment by mutableStateOf<String?>(null)
        private set

    var relevantPushAuthor by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    // Expose cacheState directly from CacheManager
    val cacheState: StateFlow<CacheManagementState> = cacheManager.cacheState

    private val _isDownloadingAnyFile = MutableStateFlow(false)
    val isDownloadingAnyFile: StateFlow<Boolean> = _isDownloadingAnyFile.asStateFlow()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedJobs by mutableStateOf<List<JobDetailsUiModel>>(emptyList())
        private set

    val isLoadingJobArtifacts = mutableStateMapOf<String, Boolean>()

    var onInstallApk: ((File) -> Unit)? = null

    private val deviceSupportedAbis: List<String> by lazy { Build.SUPPORTED_ABIS.toList() }

    init {
        cacheManager.cacheState.onEach { state ->
            if (state is CacheManagementState.IdleEmpty) {
                // Reset download states for artifacts in this ViewModel
                // This logic was moved from clearAppCache
                val updatedSelectedJobs = selectedJobs.map {
                    val updatedArtifacts = it.artifacts.map { artifact ->
                        artifact.copy(downloadState = DownloadState.NotDownloaded)
                    }
                    it.copy(artifacts = updatedArtifacts)
                }
                selectedJobs = updatedSelectedJobs
                checkAndUpdateDownloadingStatus() // Downloads are effectively cancelled
            }
            // Potentially update _isDownloadingAnyFile or other states if they depend on cache state changes
        }.launchIn(viewModelScope)
    }

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

    fun setRevisionFromDeepLinkAndSearch(project: String?, newRevision: String) {
        Log.i("FenixInstallerViewModel", "Setting project to: ${project ?: "default (try)"}, revision from deep link to: $newRevision and triggering search.")
        selectedProject = project ?: "try"
        revision = newRevision
        relevantPushComment = null
        relevantPushAuthor = null
        selectedJobs = emptyList()
        isLoadingJobArtifacts.clear()
        errorMessage = null
        searchJobsAndArtifacts()
    }

    fun getDownloadedFile(artifactName: String, taskId: String): File? {
        if (taskId.isBlank()) return null
        // The cache directory for Treeherder artifacts is now under a "treeherder" subdirectory
        val taskSpecificDir = File(cacheManager.getCacheDir("treeherder"), taskId)
        val outputFile = File(taskSpecificDir, artifactName)
        return if (outputFile.exists()) outputFile else null
    }

    fun checkCacheStatus() {
        cacheManager.checkCacheStatus()
    }

    fun clearAppCache() {
        viewModelScope.launch {
            cacheManager.clearCache()
        }
    }

    fun searchJobsAndArtifacts() {
        if (revision.isBlank()) {
            errorMessage = "Please enter a revision to search."
            return
        }
        Log.d("FenixInstallerViewModel", "Starting job/artifact search for project: $selectedProject, revision: $revision")

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            relevantPushComment = null
            relevantPushAuthor = null
            selectedJobs = emptyList()
            isLoadingJobArtifacts.clear()
            cacheManager.checkCacheStatus() // Use CacheManager
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
                        relevantPushAuthor = firstPushResult.author
                    } else {
                        relevantPushAuthor = null
                    }
                    relevantPushComment = foundComment

                    if (pushData.results.isEmpty()) {
                        errorMessage = "No push found for project: $selectedProject, revision: $revision"
                        isLoading = false
                        return@launch
                    }
                    val pushId = pushData.results.first().id
                    Log.d("FenixInstallerViewModel", "Found push ID: $pushId for project: $selectedProject, revision: $revision")
                    fetchJobs(pushId)
                }
                is NetworkResult.Error -> {
                    errorMessage = "Error fetching revision details for $selectedProject: ${revisionResult.message}"
                    isLoading = false
                }
            }
        }
    }

    private suspend fun fetchJobs(pushId: Int) {
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
                            artifacts = emptyList(),
                        )
                    }

                    val updatedJobUiModels = initialJobUiModels.map {
                        viewModelScope.async(Dispatchers.IO) {
                            // Consider ioDispatcher if repository calls are blocking
                            val fetchedArtifacts = fetchArtifacts(it.taskId)
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

    private suspend fun fetchArtifacts(taskId: String): List<ArtifactUiModel> {
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
                    val downloadedFile = getDownloadedFile(artifactFileName, taskId)
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
                            isSupported = isCompatible,
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

    fun downloadArtifact(artifactUiModel: ArtifactUiModel) {
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
            // cacheManager.checkCacheStatus() will be called after download success/failure

            Log.i("FenixInstallerViewModel", "Starting download for $downloadKey")

            val downloadUrl = artifactUiModel.downloadUrl

            // Ensure the cache directory structure for Treeherder artifacts is respected
            val treeherderCacheDir = cacheManager.getCacheDir("treeherder")
            val outputDir = File(treeherderCacheDir, taskId)
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
                },
            )

            when (result) {
                is NetworkResult.Success -> {
                    Log.i("FenixInstallerViewModel", "Download completed for $downloadKey at ${result.data.absolutePath}")
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.Downloaded(result.data))
                    cacheManager.checkCacheStatus() // Update cache status via CacheManager
                    onInstallApk?.invoke(result.data)
                }
                is NetworkResult.Error -> {
                    val failureMessage = "Download failed for $artifactFileName: ${result.message}"
                    Log.e("FenixInstallerViewModel", failureMessage, result.cause)
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.DownloadFailed(result.message))
                    cacheManager.checkCacheStatus() // Update cache status via CacheManager
                }
            }
        }
    }

    private fun updateArtifactDownloadState(taskIdToUpdate: String, artifactNameToUpdate: String, newState: DownloadState) {
        selectedJobs = selectedJobs.map {
            if (it.taskId == taskIdToUpdate) {
                it.copy(
                    artifacts = it.artifacts.map {
                    if (it.name == artifactNameToUpdate) {
                        it.copy(downloadState = newState)
                    } else {
                        it
                    }
                },
                )
            } else {
                it
            }
        }
        checkAndUpdateDownloadingStatus()
    }
}
