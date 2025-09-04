package org.mozilla.fenixinstaller.ui.screens

import android.content.Context
import android.os.Build
import android.util.Log // Added import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.fenixinstaller.data.DownloadState
import org.mozilla.fenixinstaller.data.IFenixRepository
import org.mozilla.fenixinstaller.data.UserDataRepository
import org.mozilla.fenixinstaller.data.NetworkResult
import org.mozilla.fenixinstaller.model.CacheManagementState
import org.mozilla.fenixinstaller.ui.models.AbiUiModel
import org.mozilla.fenixinstaller.ui.models.ArtifactUiModel
import org.mozilla.fenixinstaller.ui.models.JobDetailsUiModel
import org.mozilla.fenixinstaller.ui.models.PushUiModel
import java.io.File
import logcat.LogPriority
import logcat.logcat

/**
 * A ViewModel for the Profile screen.
 * @param fenixRepository The repository to use for fetching data.
 * @param userDataRepository The repository to use for storing user data.
 */
class ProfileViewModel(
    private val fenixRepository: IFenixRepository,
    private val userDataRepository: UserDataRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val _authorEmail = MutableStateFlow("")
    val authorEmail: StateFlow<String> = _authorEmail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _pushes = MutableStateFlow<List<PushUiModel>>(emptyList())
    val pushes: StateFlow<List<PushUiModel>> = _pushes.asStateFlow()

    private val _cacheState = MutableStateFlow<CacheManagementState>(CacheManagementState.IdleEmpty)
    val cacheState: StateFlow<CacheManagementState> = _cacheState.asStateFlow()

    private val deviceSupportedAbis: List<String> by lazy { Build.SUPPORTED_ABIS.toList() }
    var onInstallApk: ((File) -> Unit)? = null

    init {
        logcat(LogPriority.DEBUG, TAG) { "Initializing ProfileViewModel" }
        viewModelScope.launch {
            _authorEmail.value = userDataRepository.lastSearchedEmailFlow.first()
            logcat(LogPriority.DEBUG, TAG) { "Initial author email loaded: ${_authorEmail.value}" }
        }
    }

    fun updateAuthorEmail(email: String) {
        logcat(LogPriority.DEBUG, TAG) { "Updating author email to: $email" }
        _authorEmail.value = email
    }

    fun searchByAuthor(context: Context) {
        logcat(TAG) { "searchByAuthor called for email: ${_authorEmail.value}" }
        if (_authorEmail.value.isBlank()) {
            _errorMessage.value = "Please enter an author email to search."
            logcat(LogPriority.WARN, TAG) { "Search attempt with blank email" }
            return
        }
        viewModelScope.launch {
            userDataRepository.saveLastSearchedEmail(_authorEmail.value)
            _isLoading.value = true
            _errorMessage.value = null
            _pushes.value = emptyList()
            logcat(LogPriority.DEBUG, TAG) { "Starting search..." }

            when (val result = fenixRepository.getPushesByAuthor(_authorEmail.value)) {
                is NetworkResult.Success -> {
                    logcat(LogPriority.DEBUG, TAG) { "getPushesByAuthor success, processing ${result.data.results.size} pushes" }
                    val pushesWithJobsAndArtifacts = result.data.results.map { pushResult ->
                        async {
                            val jobsResult = fenixRepository.getJobsForPush(pushResult.id)
                            if (jobsResult is NetworkResult.Success) {
                                val filteredJobs = jobsResult.data.results.filter { it.isSignedBuild && !it.isTest }
                                if (filteredJobs.isNotEmpty()) {
                                    val jobsWithArtifacts = filteredJobs.map { jobDetails ->
                                        async {
                                            val artifacts = fetchArtifacts(jobDetails.taskId, context)
                                            if (artifacts.isNotEmpty()) {
                                                JobDetailsUiModel(
                                                    appName = jobDetails.appName,
                                                    jobName = jobDetails.jobName,
                                                    jobSymbol = jobDetails.jobSymbol,
                                                    taskId = jobDetails.taskId,
                                                    isSignedBuild = jobDetails.isSignedBuild,
                                                    isTest = jobDetails.isTest,
                                                    artifacts = artifacts
                                                )
                                            } else {
                                                null
                                            }
                                        }
                                    }.awaitAll().filterNotNull()

                                    if (jobsWithArtifacts.isNotEmpty()) {
                                        var determinedPushComment: String? = null
                                        for (revDetail in pushResult.revisions) {
                                            if (revDetail.comments.startsWith("Bug ")) {
                                                determinedPushComment = revDetail.comments
                                                break
                                            }
                                        }
                                        if (determinedPushComment == null) {
                                            determinedPushComment = pushResult.revisions.firstOrNull()?.comments ?: "No comment"
                                        }
                                        PushUiModel(
                                            pushComment = determinedPushComment,
                                            author = pushResult.author,
                                            jobs = jobsWithArtifacts,
                                            revision = pushResult.revision
                                        )
                                    } else {
                                        logcat(LogPriority.VERBOSE, TAG) { "No jobs with artifacts for push ID: ${pushResult.id}" }
                                        null
                                    }
                                } else {
                                    logcat(LogPriority.VERBOSE, TAG) { "No signed, non-test jobs for push ID: ${pushResult.id}" }
                                    null
                                }
                            } else {
                                logcat(LogPriority.WARN, TAG) { "getJobsForPush failed for push ID: ${pushResult.id}: ${(jobsResult as NetworkResult.Error).message}" }
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()

                    _pushes.value = pushesWithJobsAndArtifacts
                    logcat(TAG) { "Search finished, ${_pushes.value.size} pushes with artifacts found." }
                    if (pushesWithJobsAndArtifacts.isEmpty()) {
                        _errorMessage.value = "No signed builds found for this author."
                        logcat(TAG) { "No signed builds found for author." }
                    }
                }
                is NetworkResult.Error -> {
                    logcat(LogPriority.ERROR, TAG) { "Error fetching pushes: ${result.message}" }
                    _errorMessage.value = "Error fetching pushes: ${result.message}"
                }
            }
            _isLoading.value = false
        }
    }

    private suspend fun fetchArtifacts(taskId: String, context: Context): List<ArtifactUiModel> {
        logcat(LogPriority.DEBUG, TAG) { "fetchArtifacts called for taskId: $taskId" }
        return when (val artifactsResult = fenixRepository.getArtifactsForTask(taskId)) {
            is NetworkResult.Success -> {
                val filteredApks = artifactsResult.data.artifacts.filter {
                    it.name.endsWith(".apk", ignoreCase = true)
                }
                logcat(LogPriority.VERBOSE, TAG) { "Found ${filteredApks.size} APKs for taskId: $taskId" }
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
                logcat(LogPriority.WARN, TAG) { "fetchArtifacts error for taskId $taskId: ${artifactsResult.message}" }
                emptyList()
            }
        }
    }

    fun getDownloadedFile(artifactName: String, context: Context, taskId: String): File? {
        if (taskId.isBlank()) return null
        val taskSpecificDir = File(context.cacheDir, taskId)
        val outputFile = File(taskSpecificDir, artifactName)
        val exists = outputFile.exists()
        logcat(LogPriority.VERBOSE, TAG) { "getDownloadedFile for $artifactName in $taskId: exists=$exists" }
        if (exists) {
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
                val isEmpty = !(cacheDir.exists() && (cacheDir.listFiles()?.isNotEmpty() == true))
                logcat(LogPriority.DEBUG, TAG) { "checkCacheStatus: isEmpty=$isEmpty" }
                _cacheState.value = if (isEmpty) CacheManagementState.IdleEmpty else CacheManagementState.IdleNonEmpty
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, TAG) { "Error checking cache status: ${e.message}\n${Log.getStackTraceString(e)}" }
                _cacheState.value = CacheManagementState.IdleEmpty
            }
        }
    }

    fun clearAppCache(context: Context) {
        logcat(TAG) { "clearAppCache called" }
        viewModelScope.launch {
            _cacheState.value = CacheManagementState.Clearing
            try {
                withContext(Dispatchers.IO) {
                    val cacheDir = context.cacheDir
                    if (cacheDir.exists()) {
                        cacheDir.deleteRecursively()
                        logcat(LogPriority.DEBUG, TAG) { "Cache directory deleted" }
                    }
                }
                _cacheState.value = CacheManagementState.IdleEmpty
                val updatedPushes = _pushes.value.map {
                    it.copy(jobs = it.jobs.map { 
                        it.copy(artifacts = it.artifacts.map { 
                            it.copy(downloadState = DownloadState.NotDownloaded)
                        })
                    })
                }
                _pushes.value = updatedPushes
                logcat(LogPriority.DEBUG, TAG) { "Pushes updated to NotDownloaded state after cache clear" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, TAG) { "Error clearing cache: ${e.message}\n${Log.getStackTraceString(e)}" }
                checkCacheStatus(context) // Consider if this is the best action on error
            }
        }
    }

    fun downloadArtifact(artifactUiModel: ArtifactUiModel, context: Context) {
        val artifactFileName = artifactUiModel.name.substringAfterLast('/')
        val taskId = artifactUiModel.taskId
        logcat(TAG) { "downloadArtifact called for: ${artifactUiModel.name}, taskId: $taskId, uniqueKey: ${artifactUiModel.uniqueKey}" }

        if (artifactUiModel.downloadState is DownloadState.InProgress || artifactUiModel.downloadState is DownloadState.Downloaded) {
            logcat(LogPriority.WARN, TAG) { "Download attempt for already in progress or downloaded artifact: ${artifactUiModel.name}" }
            return
        }
         if (taskId.isBlank()) {
            val blankTaskIdMsg = "Task ID is blank for $artifactFileName"
            logcat(LogPriority.ERROR, TAG) { blankTaskIdMsg }
            updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.DownloadFailed(blankTaskIdMsg))
            return
        }

        viewModelScope.launch {
            logcat(LogPriority.DEBUG, TAG) { "Starting download coroutine for ${artifactUiModel.name}" }
            updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.InProgress(0f))
            if (_cacheState.value == CacheManagementState.IdleEmpty) {
                _cacheState.value = CacheManagementState.IdleNonEmpty
            }

            val downloadUrl = artifactUiModel.downloadUrl
            logcat(LogPriority.DEBUG, TAG) { "Download URL: $downloadUrl" }

            val outputDir = File(context.cacheDir, taskId)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
                logcat(LogPriority.VERBOSE, TAG) { "Created output directory: ${outputDir.absolutePath}" }
            }
            val outputFile = File(outputDir, artifactFileName)
            logcat(LogPriority.DEBUG, TAG) { "Output file: ${outputFile.absolutePath}" }

            var lastLoggedNumericProgress = 0f

            logcat(TAG) { "Calling fenixRepository.downloadArtifact for ${artifactUiModel.name}" }
            val result = fenixRepository.downloadArtifact(
                downloadUrl = downloadUrl,
                outputFile = outputFile,
                onProgress = { bytesDownloaded, totalBytes ->
                    val currentProgressFloat = if (totalBytes > 0) {
                        bytesDownloaded.toFloat() / totalBytes.toFloat()
                    } else {
                        0f
                    }

                    var shouldLog = false
                    if (bytesDownloaded == 0L) {
                        shouldLog = true
                        lastLoggedNumericProgress = 0f
                    } else if (bytesDownloaded == totalBytes) {
                        shouldLog = true
                        lastLoggedNumericProgress = currentProgressFloat
                    } else if (currentProgressFloat - lastLoggedNumericProgress >= 0.02f) {
                        shouldLog = true
                        lastLoggedNumericProgress = currentProgressFloat
                    }

                    if (shouldLog) {
                         logcat(LogPriority.VERBOSE, TAG) { "Download progress for ${artifactUiModel.name}: $bytesDownloaded / $totalBytes ($currentProgressFloat)" }
                    }
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.InProgress(currentProgressFloat))
                }
            )

            logcat(TAG) { "fenixRepository.downloadArtifact result for ${artifactUiModel.name}: $result" }
            when (result) {
                is NetworkResult.Success -> {
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.Downloaded(result.data))
                    if (_cacheState.value == CacheManagementState.IdleEmpty) {
                        _cacheState.value = CacheManagementState.IdleNonEmpty
                    }
                    logcat(TAG) { "Download success for ${artifactUiModel.name}. APK is ready to be installed." } 
                    // onInstallApk?.invoke(result.data) // LINE REMOVED in previous step, ensure it stays removed or handled if needed
                }
                is NetworkResult.Error -> {
                    val failureMessage = "Download failed for $artifactFileName: ${result.message}"
                    if (result.cause != null) {
                        logcat(LogPriority.ERROR, TAG) { "$failureMessage\n${Log.getStackTraceString(result.cause)}" }
                    } else {
                        logcat(LogPriority.ERROR, TAG) { "$failureMessage (No cause available)" }
                    }
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.DownloadFailed(result.message))
                    checkCacheStatus(context) // Consider if this is the best action on error
                }
            }
        }
    }

    private fun updateArtifactDownloadState(taskIdToUpdate: String, artifactNameToUpdate: String, newState: DownloadState) {
        _pushes.value = _pushes.value.map { push ->
            push.copy(jobs = push.jobs.map { job ->
                if (job.taskId == taskIdToUpdate) {
                    job.copy(artifacts = job.artifacts.map { artifact ->
                        if (artifact.name == artifactNameToUpdate) {
                            artifact.copy(downloadState = newState)
                        } else artifact
                    })
                } else job
            })
        }
    }
}
