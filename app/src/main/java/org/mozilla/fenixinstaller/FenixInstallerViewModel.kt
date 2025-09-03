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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.mozilla.fenixinstaller.data.ArtifactUiModel
import org.mozilla.fenixinstaller.data.DownloadState
import org.mozilla.fenixinstaller.data.FenixRepository
import org.mozilla.fenixinstaller.data.JobDetailsUiModel
import org.mozilla.fenixinstaller.data.NetworkResult
import java.io.File

class FenixInstallerViewModel : ViewModel() {
    var revision by mutableStateOf("c2f3f652a3a063cb7933c2781038a25974cd09ec")
        private set

    var selectedProject by mutableStateOf("try") // New state for selected project
        private set

    var relevantPushComment by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedJobs by mutableStateOf<List<JobDetailsUiModel>>(emptyList()) 
        private set

    val isLoadingJobArtifacts = mutableStateMapOf<String, Boolean>() 

    var onInstallApk: ((File) -> Unit)? = null

    private val repository = FenixRepository()
    private val deviceSupportedAbis: List<String> by lazy { Build.SUPPORTED_ABIS.toList() }

    fun updateRevision(newRevision: String) {
        Log.d("FenixInstallerViewModel", "Updating revision to: $newRevision")
        revision = newRevision
    }

    fun updateSelectedProject(newProject: String) { // New function to update project
        Log.d("FenixInstallerViewModel", "Updating selected project to: $newProject")
        selectedProject = newProject
    }

    // Updated to accept project from deep link
    fun setRevisionFromDeepLinkAndSearch(project: String?, newRevision: String, context: Context) {
        Log.i("FenixInstallerViewModel", "Setting project to: ${project ?: "default (try)"}, revision from deep link to: $newRevision and triggering search.")
        selectedProject = project ?: "try" // Default to "try" if project is null or blank from intent
        revision = newRevision
        relevantPushComment = null
        selectedJobs = emptyList()
        isLoadingJobArtifacts.clear()
        errorMessage = null
        searchJobsAndArtifacts(context)
    }

    fun getDownloadedFile(artifactName: String, context: Context, taskId: String): File? {
        if (taskId.isBlank()) return null
        val taskSpecificDir = File(context.cacheDir, taskId)
        val outputFile = File(taskSpecificDir, artifactName)
        return if (outputFile.exists()) outputFile else null
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
            selectedJobs = emptyList()
            isLoadingJobArtifacts.clear()

            // Use selectedProject in the repository call
            when (val revisionResult = repository.getPushByRevision(selectedProject, revision)) {
                is NetworkResult.Success -> {
                    val pushData = revisionResult.data
                    var foundComment: String? = null
                    for (result in pushData.results) {
                        for (revDetail in result.revisions) {
                            if (revDetail.comments.startsWith("Bug ")) {
                                foundComment = revDetail.comments
                                break
                            }
                        }
                        if (foundComment != null) break
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
                    selectedJobs = emptyList()
                } else {
                    val initialJobUiModels = networkJobDetailsList.map { netJob ->
                        Log.i("FenixInstallerViewModel", "Initial map for job: '${netJob.jobName}' (TaskID: ${netJob.taskId})")
                        isLoadingJobArtifacts[netJob.taskId] = true
                        JobDetailsUiModel(
                            appName = netJob.appName,
                            jobName = netJob.jobName,
                            jobSymbol = netJob.jobSymbol,
                            taskId = netJob.taskId,
                            isSignedBuild = netJob.isSignedBuild,
                            isTest = netJob.isTest, // Corrected typo here
                            artifacts = emptyList()
                        )
                    }
                    selectedJobs = initialJobUiModels

                    val updatedJobUiModels = initialJobUiModels.map { jobUiModel ->
                        viewModelScope.async { 
                            val fetchedArtifacts = fetchArtifacts(jobUiModel.taskId, context)
                            isLoadingJobArtifacts[jobUiModel.taskId] = false
                            jobUiModel.copy(artifacts = fetchedArtifacts)
                        }
                    }.awaitAll()

                    selectedJobs = updatedJobUiModels

                    if (updatedJobUiModels.all { it.artifacts.isEmpty() }) {
                         errorMessage = "Selected jobs found, but no APKs in any of them. Check build logs."
                    }
                }
            }
            is NetworkResult.Error -> {
                errorMessage = "Error fetching jobs: ${jobsResult.message}"
                Log.e("FenixInstallerViewModel", "Error fetching/parsing jobs for push ID $pushId: ${jobsResult.message}", jobsResult.cause)
            }
        }
        isLoading = false 
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
                        originalArtifact = artifact,
                        taskId = taskId,
                        isCompatibleAbi = isCompatible,
                        downloadState = downloadState
                    )
                }
            }
            is NetworkResult.Error -> {
                Log.e("FenixInstallerViewModel", "Error fetching artifacts for task ID $taskId: ${artifactsResult.message}", artifactsResult.cause)
                emptyList<ArtifactUiModel>() 
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
            updateArtifactDownloadState(taskId, artifactUiModel.originalArtifact.name, DownloadState.DownloadFailed(blankTaskIdMsg))
            return
        }

        viewModelScope.launch {
            updateArtifactDownloadState(taskId, artifactUiModel.originalArtifact.name, DownloadState.InProgress(0f))

            Log.i("FenixInstallerViewModel", "Starting download for $downloadKey")

            val downloadUrl = artifactUiModel.getDownloadUrl()
            val result = repository.downloadArtifact(
                context = context,
                taskId = taskId,
                downloadUrl = downloadUrl,
                fileName = artifactFileName,
                onProgress = { bytesDownloaded, totalBytes ->
                    val progress = if (totalBytes > 0) {
                        bytesDownloaded.toFloat() / totalBytes.toFloat()
                    } else {
                        0f 
                    }
                    Log.d("FenixInstallerViewModel", "Download progress for $downloadKey: ${(progress * 100).toInt()}%")
                }
            )

            when (result) {
                is NetworkResult.Success -> {
                    Log.i("FenixInstallerViewModel", "Download completed for $downloadKey at ${result.data.absolutePath}")
                    updateArtifactDownloadState(taskId, artifactUiModel.originalArtifact.name, DownloadState.Downloaded(result.data))
                    onInstallApk?.invoke(result.data)
                }
                is NetworkResult.Error -> {
                    val failureMessage = "Download failed for $artifactFileName: ${result.message}"
                    Log.e("FenixInstallerViewModel", failureMessage, result.cause)
                    updateArtifactDownloadState(taskId, artifactUiModel.originalArtifact.name, DownloadState.DownloadFailed(result.message))
                }
            }
        }
    }

    private fun updateArtifactDownloadState(taskIdToUpdate: String, artifactNameToUpdate: String, newState: DownloadState) {
        selectedJobs = selectedJobs.map {
            if (it.taskId == taskIdToUpdate) {
                it.copy(artifacts = it.artifacts.map {
                    if (it.originalArtifact.name == artifactNameToUpdate) {
                        it.copy(downloadState = newState)
                    } else it
                })
            } else it
        }
    }
}
