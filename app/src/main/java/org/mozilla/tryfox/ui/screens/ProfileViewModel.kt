package org.mozilla.tryfox.ui.screens

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import org.mozilla.tryfox.data.repositories.UserDataRepository
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ArtifactUiModel
import org.mozilla.tryfox.ui.models.JobDetailsUiModel
import org.mozilla.tryfox.ui.models.PushUiModel
import org.mozilla.tryfox.util.TREEHERDER
import java.io.File

/**
 * ViewModel for the Profile screen, responsible for fetching pushes and artifacts by author, managing downloads, and handling user interactions.
 *
 * @param fenixRepository The repository for fetching Fenix-related data.
 * @param userDataRepository The repository for storing and retrieving user data, such as the last searched email.
 * @param cacheManager The manager for handling application cache.
 * @param intentManager The manager for handling intents, such as APK installation.
 * @param authorEmail The initial author email to search for, can be null.
 */
class ProfileViewModel(
    private val fenixRepository: TreeherderRepository,
    private val downloadFileRepository: DownloadFileRepository,
    private val userDataRepository: UserDataRepository,
    private val cacheManager: CacheManager,
    private val intentManager: IntentManager,
    private val historyRepository: HistoryRepository,
    authorEmail: String?,
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val _authorEmail = MutableStateFlow(authorEmail ?: "")
    val authorEmail: StateFlow<String> = _authorEmail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _pushes = MutableStateFlow<List<PushUiModel>>(emptyList())
    val pushes: StateFlow<List<PushUiModel>> = _pushes.asStateFlow()

    val cacheState: StateFlow<CacheManagementState> = cacheManager.cacheState

    private val deviceSupportedAbis: List<String> by lazy { Build.SUPPORTED_ABIS.toList() }

    init {
        logcat(LogPriority.DEBUG, TAG) { "Initializing ProfileViewModel for email: $authorEmail" }
        cacheManager.cacheState.onEach { state ->
            if (state is CacheManagementState.IdleEmpty) {
                val updatedPushes = _pushes.value.map {
                    it.copy(
                        jobs = it.jobs.map { job ->
                            job.copy(
                                artifacts = job.artifacts.map { artifact ->
                                    artifact.copy(downloadState = DownloadState.NotDownloaded)
                                },
                            )
                        },
                    )
                }
                _pushes.value = updatedPushes
            }
        }.launchIn(viewModelScope)

        if (authorEmail != null) {
            searchByAuthor()
        } else {
            loadLastSearchedEmail()
        }
    }

    private fun loadLastSearchedEmail() {
        viewModelScope.launch {
            val lastEmail = userDataRepository.lastSearchedEmailFlow.first()
            if (lastEmail.isNotBlank()) {
                _authorEmail.value = lastEmail
                logcat(
                    LogPriority.DEBUG,
                    TAG,
                ) { "Initial author email loaded from storage: ${_authorEmail.value}" }
            }
        }
    }

    fun updateAuthorEmail(email: String) {
        logcat(LogPriority.DEBUG, TAG) { "Updating author email to: $email" }
        _authorEmail.value = email
    }

    fun searchByAuthor() {
        val emailToSearch = _authorEmail.value
        logcat(TAG) { "searchByAuthor called for email: $emailToSearch" }
        if (emailToSearch.isBlank()) {
            _errorMessage.value = "Please enter an author email to search."
            logcat(LogPriority.WARN, TAG) { "Search attempt with blank email" }
            return
        }
        viewModelScope.launch {
            userDataRepository.saveLastSearchedEmail(emailToSearch)
            _isLoading.value = true
            _errorMessage.value = null
            _pushes.value = emptyList()
            logcat(LogPriority.DEBUG, TAG) { "Starting search..." }

            when (val result = fenixRepository.getPushesByAuthor(emailToSearch)) {
                is NetworkResult.Success -> {
                    logcat(
                        LogPriority.DEBUG,
                        TAG,
                    ) { "getPushesByAuthor success, processing ${result.data.results.size} pushes" }
                    val pushesWithJobsAndArtifacts = result.data.results.map { pushResult ->
                        async {
                            val jobsResult = fenixRepository.getJobsForPush(pushResult.id)
                            if (jobsResult is NetworkResult.Success) {
                                val filteredJobs =
                                    jobsResult.data.results.filter { it.isSignedBuild && !it.isTest }
                                if (filteredJobs.isNotEmpty()) {
                                    val jobsWithArtifacts = filteredJobs.map { jobDetails ->
                                        async {
                                            val artifacts = fetchArtifacts(jobDetails.taskId)
                                            if (artifacts.isNotEmpty()) {
                                                JobDetailsUiModel(
                                                    appName = jobDetails.appName,
                                                    jobName = jobDetails.jobName,
                                                    jobSymbol = jobDetails.jobSymbol,
                                                    taskId = jobDetails.taskId,
                                                    isSignedBuild = jobDetails.isSignedBuild,
                                                    isTest = jobDetails.isTest,
                                                    artifacts = artifacts,
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
                                            determinedPushComment = pushResult.revisions.firstOrNull()?.comments
                                                ?: "No comment"
                                        }
                                        PushUiModel(
                                            pushComment = determinedPushComment,
                                            author = pushResult.author,
                                            jobs = jobsWithArtifacts,
                                            revision = pushResult.revision,
                                            pushTimestamp = pushResult.pushTimestamp,
                                        )
                                    } else {
                                        logcat(LogPriority.VERBOSE, TAG) {
                                            "No jobs with artifacts for push ID: ${pushResult.id}"
                                        }
                                        null
                                    }
                                } else {
                                    logcat(LogPriority.VERBOSE, TAG) {
                                        "No signed, non-test jobs for push ID: ${pushResult.id}"
                                    }
                                    null
                                }
                            } else {
                                logcat(LogPriority.WARN, TAG) {
                                    "getJobsForPush failed for push ID: ${pushResult.id}: " +
                                    (jobsResult as NetworkResult.Error).message
                                }
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

    private suspend fun fetchArtifacts(taskId: String): List<ArtifactUiModel> {
        logcat(LogPriority.DEBUG, TAG) { "fetchArtifacts called for taskId: $taskId" }
        return when (val artifactsResult = fenixRepository.getArtifactsForTask(taskId)) {
            is NetworkResult.Success -> {
                val filteredApks = artifactsResult.data.artifacts.filter {
                    it.name.endsWith(".apk", ignoreCase = true)
                }
                logcat(
                    LogPriority.VERBOSE,
                    TAG,
                ) { "Found ${filteredApks.size} APKs for taskId: $taskId" }
                filteredApks.map { artifact ->
                    val artifactFileName = artifact.name.substringAfterLast('/')
                    val downloadedFile = getDownloadedFile(artifactFileName, taskId)
                    val downloadState = if (downloadedFile != null) {
                        DownloadState.Downloaded(downloadedFile)
                    } else {
                        DownloadState.NotDownloaded
                    }
                    val isCompatible =
                        artifact.abi != null && deviceSupportedAbis.any { deviceAbi ->
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
                logcat(LogPriority.WARN, TAG) {
                    "fetchArtifacts error for taskId $taskId: ${artifactsResult.message}"
                }
                emptyList()
            }
        }
    }

    fun getDownloadedFile(artifactName: String, taskId: String): File? {
        if (taskId.isBlank()) return null
        val taskSpecificDir = File(cacheManager.getCacheDir("treeherder"), taskId)
        val outputFile = File(taskSpecificDir, artifactName)
        val exists = outputFile.exists()
        logcat(
            LogPriority.DEBUG,
            TAG,
        ) {
            "getDownloadedFile artifactName=$artifactName, taskId=$taskId, " +
                "path=${outputFile.absolutePath}, exists=$exists"
        }
        return if (exists) outputFile else null
    }

    fun clearAppCache() {
        logcat(TAG) { "clearAppCache called" }
        viewModelScope.launch {
            cacheManager.clearCache()
        }
    }

    fun downloadArtifact(artifactUiModel: ArtifactUiModel) {
        val artifactFileName = artifactUiModel.name.substringAfterLast('/')
        val taskId = artifactUiModel.taskId
        logcat(TAG) {
            "downloadArtifact called for: ${artifactUiModel.name}, taskId: $taskId, " +
            "uniqueKey: ${artifactUiModel.uniqueKey}"
        }

        if (artifactUiModel.downloadState is DownloadState.InProgress ||
            artifactUiModel.downloadState is DownloadState.Downloaded
        ) {
            logcat(LogPriority.WARN, TAG) {
                "Download attempt for already in progress or downloaded artifact: ${artifactUiModel.name}"
            }
            return
        }
        if (taskId.isBlank()) {
            val blankTaskIdMsg = "Task ID is blank for $artifactFileName"
            logcat(LogPriority.ERROR, TAG) { blankTaskIdMsg }
            updateArtifactDownloadState(
                taskId,
                artifactUiModel.name,
                DownloadState.DownloadFailed(blankTaskIdMsg),
            )
            return
        }

        viewModelScope.launch {
            logcat(
                LogPriority.DEBUG,
                TAG,
            ) { "Starting download coroutine for ${artifactUiModel.name}" }
            updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.InProgress(0f))

            val downloadUrl = artifactUiModel.downloadUrl
            logcat(LogPriority.DEBUG, TAG) { "Download URL: $downloadUrl" }

            val outputDir = File(cacheManager.getCacheDir("treeherder"), taskId)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
                logcat(
                    LogPriority.VERBOSE,
                    TAG,
                ) { "Created output directory: ${outputDir.absolutePath}" }
            }
            val outputFile = File(outputDir, artifactFileName)
            logcat(LogPriority.DEBUG, TAG) { "Output file: ${outputFile.absolutePath}" }

            var lastLoggedNumericProgress = 0f

            logcat(TAG) { "Calling fenixRepository.downloadArtifact for ${artifactUiModel.name}" }
            val result = downloadFileRepository.downloadFile(
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
                         logcat(LogPriority.VERBOSE, TAG) {
                            "Download progress for ${artifactUiModel.name}: $bytesDownloaded / $totalBytes " +
                            "($currentProgressFloat)"
                        }
                    }
                    updateArtifactDownloadState(
                        taskId,
                        artifactUiModel.name,
                        DownloadState.InProgress(currentProgressFloat),
                    )
                },
            )

            logcat(TAG) { "fenixRepository.downloadArtifact result for ${artifactUiModel.name}: $result" }
            when (result) {
                is NetworkResult.Success -> {
                    updateArtifactDownloadState(
                        taskId,
                        artifactUiModel.name,
                        DownloadState.Downloaded(result.data),
                    )
                    cacheManager.checkCacheStatus()
                    logcat(TAG) { "Download success for ${artifactUiModel.name}. APK is ready to be installed." }
                    installApk(result.data)
                }

                is NetworkResult.Error -> {
                    val failureMessage = "Download failed for $artifactFileName: ${result.message}"
                    if (result.cause != null) {
                        logcat(
                            LogPriority.ERROR,
                            TAG,
                        ) { "$failureMessage\n${result.cause.stackTraceToString()}" }
                    } else {
                        logcat(LogPriority.ERROR, TAG) { "$failureMessage (No cause available)" }
                    }
                    updateArtifactDownloadState(
                        taskId,
                        artifactUiModel.name,
                        DownloadState.DownloadFailed(result.message),
                    )
                    cacheManager.checkCacheStatus()
                }
            }
        }
    }

    fun installApk(file: File) {
        val downloadedArtifact = findDownloadedArtifact(file)
        if (downloadedArtifact == null) {
            intentManager.installApk(file)
            return
        }

        viewModelScope.launch {
            try {
                recordInstallerLaunch(downloadedArtifact)
            } catch (_: Exception) {
                // History is best-effort; never block installation.
            }
            intentManager.installApk(file)
        }
    }

    private fun updateArtifactDownloadState(
        taskIdToUpdate: String,
        artifactNameToUpdate: String,
        newState: DownloadState,
    ) {
        _pushes.value = _pushes.value.map { push: PushUiModel ->
            push.updateTask(taskIdToUpdate, artifactNameToUpdate, newState)
        }
    }

    private fun PushUiModel.updateTask(
        taskId: String,
        artifactNameToUpdate: String,
        newState: DownloadState,
    ): PushUiModel =
        copy(
            jobs =
                jobs.map { job: JobDetailsUiModel ->
                    if (job.taskId != taskId) {
                        job
                    } else {
                        job.updateArtifact(artifactNameToUpdate, newState)
                    }
                },
        )

    private fun JobDetailsUiModel.updateArtifact(
        artifactNameToUpdate: String,
        newState: DownloadState,
    ): JobDetailsUiModel =
        copy(
            artifacts = artifacts.map {
                if (it.name != artifactNameToUpdate) {
                    it
                } else {
                    it.copy(downloadState = newState)
                }
            },
        )

    private fun findDownloadedArtifact(file: File): DownloadedArtifact? =
        _pushes.value.firstNotNullOfOrNull { push ->
            push.jobs.firstNotNullOfOrNull { job ->
                job.artifacts.firstOrNull { artifact ->
                    val downloadState = artifact.downloadState
                    downloadState is DownloadState.Downloaded && downloadState.file.absolutePath == file.absolutePath
                }?.let { artifact -> DownloadedArtifact(push, job, artifact) }
            }
        }

    private suspend fun recordInstallerLaunch(downloadedArtifact: DownloadedArtifact) {
        val push = downloadedArtifact.push
        val job = downloadedArtifact.job
        val artifact = downloadedArtifact.artifact
        val artifactFileName = artifact.name.substringAfterLast('/')

        historyRepository.recordInstallerLaunch(
            TreeherderInstallHistoryEntry(
                project = "try",
                revision = push.revision ?: "unknown_revision",
                commitMessage = push.pushComment,
                author = push.author,
                pushTimestamp = push.pushTimestamp,
                appName = job.appName,
                jobName = job.jobName,
                jobSymbol = job.jobSymbol,
                taskId = artifact.taskId,
                artifactName = artifact.name,
                artifactFileName = artifactFileName,
                downloadUrl = artifact.downloadUrl,
                abiName = artifact.abi.name,
                abiSupported = artifact.abi.isSupported,
                expires = artifact.expires,
                cacheRelativePath = "$TREEHERDER/${artifact.taskId}/$artifactFileName",
                lastInstallerLaunchTimestamp = currentTimeMillisProvider(),
            ),
        )
    }

    private data class DownloadedArtifact(
        val push: PushUiModel,
        val job: JobDetailsUiModel,
        val artifact: ArtifactUiModel,
    )
}
