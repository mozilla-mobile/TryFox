package org.mozilla.tryfox

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.managers.IntentManager
import org.mozilla.tryfox.data.repositories.DownloadFileRepository
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ArtifactUiModel
import org.mozilla.tryfox.ui.models.JobDetailsUiModel
import java.io.File

/**
 * ViewModel for the TryFox feature, responsible for fetching job and artifact data from the repository,
 * managing the download and caching of artifacts, and exposing the UI state to the composable screens.
 *
 * @param fenixRepository The repository for fetching data from the network.
 * @param cacheManager The manager for handling application cache.
 * @param revision The initial revision to search for.
 * @param project The initial repository to search in.
 */
class TryFoxViewModel(
    private val fenixRepository: TreeherderRepository,
    private val downloadFileRepository: DownloadFileRepository,
    private val cacheManager: CacheManager,
    private val intentManager: IntentManager,
    project: String?,
    revision: String?,
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    private val elapsedRealtimeProvider: () -> Long = SystemClock::elapsedRealtime,
    private val infoLogger: (String, String) -> Int = Log::i,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    companion object {
        private const val TAG = "TryFoxViewModel"
        private const val MAX_PARALLEL_ARTIFACT_REQUESTS = 6
        private val APK_JOB_NAME_HINTS = listOf(
            "signing-apk",
            "android-apk",
            "apk-focus",
            "apk-fenix",
            "apk-reference-browser",
            "apk-geckoview",
        )
        private val NON_ANDROID_PLATFORM_HINTS = listOf(
            "ios",
            "mac",
            "macos",
            "macosx",
            "win",
            "windows",
            "linux",
            "desktop",
        )
        private val ANDROID_PRODUCT_HINTS = listOf(
            "focus",
            "fenix",
            "reference-browser",
            "geckoview",
            "android",
        )
    }

    var revision by mutableStateOf(revision ?: "")
        private set

    var selectedProject by mutableStateOf(project ?: "try")
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

    private val deviceSupportedAbis: List<String> by lazy { supportedAbis }

    init {
        if (revision != null) {
            searchJobsAndArtifacts()
        }
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
        revision = newRevision
    }

    fun updateSelectedProject(newProject: String) {
        selectedProject = newProject
    }

    fun setRevisionFromDeepLinkAndSearch(project: String?, newRevision: String) {
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
        val loadStartMs = elapsedRealtimeProvider()

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            relevantPushComment = null
            relevantPushAuthor = null
            selectedJobs = emptyList()
            isLoadingJobArtifacts.clear()
            cacheManager.checkCacheStatus() // Use CacheManager
            checkAndUpdateDownloadingStatus() // Initial check before fetching

            when (val revisionResult = fenixRepository.getPushByRevision(selectedProject, revision)) {
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
                        isLoadingJobArtifacts.clear()
                        return@launch
                    }
                    val pushId = pushData.results.first().id
                    fetchJobs(pushId, loadStartMs)
                }
                is NetworkResult.Error -> {
                    errorMessage = "Error fetching revision details for $selectedProject: ${revisionResult.message}"
                    isLoading = false
                    isLoadingJobArtifacts.clear()
                }
            }
        }
    }

    private suspend fun fetchJobs(pushId: Int, loadStartMs: Long = elapsedRealtimeProvider()) {
        val pageSize = 2000
        val artifactSemaphore = Semaphore(MAX_PARALLEL_ARTIFACT_REQUESTS)
        val seenTaskIds = mutableSetOf<String>()
        val jobsWithArtifacts = mutableListOf<JobDetailsUiModel>()
        val artifactFetches = mutableListOf<kotlinx.coroutines.Deferred<JobDetailsUiModel>>()
        val fallbackJobUiModels = mutableListOf<JobDetailsUiModel>()
        var page = 1
        var pageFetchFailed = false

        coroutineScope {
            while (true) {
                when (val jobsResult = fenixRepository.getJobsForPushPage(pushId = pushId, page = page, count = pageSize)) {
                    is NetworkResult.Success -> {
                        val pageJobs = jobsResult.data.results

                        if (pageJobs.isEmpty()) {
                            break
                        }

                        val candidateJobs = pageJobs
                            .asSequence()
                            .filter { job -> seenTaskIds.add(job.taskId) }
                            .filter(::isAndroidArtifactCandidate)
                            .toList()

                        val preferredCandidates = candidateJobs.filter(::isPreferredSignedApkJob)
                        val fallbackCandidates = candidateJobs.filterNot(::isPreferredSignedApkJob)

                        val preferredJobUiModels = preferredCandidates.map { netJob ->
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

                        val fallbackPageJobUiModels = fallbackCandidates.map { netJob ->
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
                        fallbackJobUiModels += fallbackPageJobUiModels

                        if (preferredJobUiModels.isNotEmpty()) {
                            selectedJobs = selectedJobs + preferredJobUiModels
                            artifactFetches += preferredJobUiModels.map { jobUiModel ->
                                async(ioDispatcher) {
                                    val fetchedArtifacts = artifactSemaphore.withPermit {
                                        fetchArtifacts(jobUiModel.taskId)
                                    }
                                    val updatedJob = jobUiModel.copy(artifacts = fetchedArtifacts)

                                    withContext(mainDispatcher) {
                                        isLoadingJobArtifacts[jobUiModel.taskId] = false
                                        selectedJobs = if (fetchedArtifacts.isEmpty()) {
                                            selectedJobs.filterNot { it.taskId == jobUiModel.taskId }
                                        } else {
                                            selectedJobs.map {
                                                if (it.taskId == jobUiModel.taskId) {
                                                    updatedJob
                                                } else {
                                                    it
                                                }
                                            }
                                        }
                                    }
                                    updatedJob
                                }
                            }
                        }

                        if (pageJobs.size < pageSize) {
                            break
                        }
                        page += 1
                    }
                    is NetworkResult.Error -> {
                        pageFetchFailed = true
                        val hasStartedLoadingResults = selectedJobs.isNotEmpty() || artifactFetches.isNotEmpty() || jobsWithArtifacts.isNotEmpty()
                        errorMessage = if (!hasStartedLoadingResults) {
                            "Error fetching jobs: ${jobsResult.message}"
                        } else {
                            "Some jobs could not be loaded: ${jobsResult.message}"
                        }
                        break
                    }
                }
            }

            val updatedJobUiModels = artifactFetches.awaitAll()
            jobsWithArtifacts += updatedJobUiModels.filter { it.artifacts.isNotEmpty() }

            if (jobsWithArtifacts.isEmpty() && fallbackJobUiModels.isNotEmpty() && !pageFetchFailed) {
                selectedJobs = fallbackJobUiModels
                fallbackJobUiModels.forEach { isLoadingJobArtifacts[it.taskId] = true }

                val fallbackUpdatedJobs = fallbackJobUiModels.map { jobUiModel ->
                    async(ioDispatcher) {
                        val fetchedArtifacts = artifactSemaphore.withPermit {
                            fetchArtifacts(jobUiModel.taskId)
                        }
                        val updatedJob = jobUiModel.copy(artifacts = fetchedArtifacts)

                        withContext(mainDispatcher) {
                            isLoadingJobArtifacts[jobUiModel.taskId] = false
                            selectedJobs = if (fetchedArtifacts.isEmpty()) {
                                selectedJobs.filterNot { it.taskId == jobUiModel.taskId }
                            } else {
                                selectedJobs.map {
                                    if (it.taskId == jobUiModel.taskId) {
                                        updatedJob
                                    } else {
                                        it
                                    }
                                }
                            }
                        }
                        updatedJob
                    }
                }.awaitAll()

                jobsWithArtifacts += fallbackUpdatedJobs.filter { it.artifacts.isNotEmpty() }
            }
        }

        if (jobsWithArtifacts.isEmpty() && !pageFetchFailed) {
            errorMessage = "Selected jobs found, but no APKs in any of them. Check build logs."
        }

        selectedJobs = selectedJobs.filter { it.artifacts.isNotEmpty() }
        infoLogger(
            TAG,
            "searchJobsAndArtifacts: finished in ${elapsedRealtimeProvider() - loadStartMs} ms with ${selectedJobs.size} job(s) shown",
        )
        isLoadingJobArtifacts.clear()
        isLoading = false
        checkAndUpdateDownloadingStatus()
    }

    private fun isAndroidArtifactCandidate(job: org.mozilla.tryfox.data.JobDetails): Boolean {
        if (job.isTest) {
            return false
        }
        if (!job.isSignedBuild) {
            return false
        }
        return isLikelySignedApkProducer(job)
    }

    private fun isAndroidArtifactCandidateRaw(job: org.mozilla.tryfox.data.JobDetails): Boolean {
        val appName = job.appName.lowercase()
        val jobName = job.jobName.lowercase()

        return jobName.contains("build-android") ||
            jobName.contains("android-components") ||
            jobName.contains("fenix") ||
            jobName.contains("focus") ||
            jobName.contains("reference-browser") ||
            jobName.contains("geckoview") ||
            appName == "fenix" ||
            appName == "focus" ||
            appName == "reference-browser" ||
            appName == "geckoview"
    }

    private fun isLikelySignedApkProducer(job: org.mozilla.tryfox.data.JobDetails): Boolean {
        val appName = job.appName.lowercase()
        val jobName = job.jobName.lowercase()

        if (!isAndroidArtifactCandidateRaw(job)) {
            return false
        }

        if (NON_ANDROID_PLATFORM_HINTS.any { hint -> jobName.contains(hint) }) {
            return false
        }

        if (APK_JOB_NAME_HINTS.any { hint -> jobName.contains(hint) }) {
            return true
        }

        val hasAndroidProductHint = ANDROID_PRODUCT_HINTS.any { hint ->
            jobName.contains(hint) || appName == hint
        }
        val hasApkHint = jobName.contains("apk")

        return hasAndroidProductHint && hasApkHint
    }

    private fun isPreferredSignedApkJob(job: org.mozilla.tryfox.data.JobDetails): Boolean {
        val jobName = job.jobName.lowercase()
        return jobName.contains("signing-apk")
    }

    private suspend fun fetchArtifacts(taskId: String): List<ArtifactUiModel> {
        return when (val artifactsResult = fenixRepository.getArtifactsForTask(taskId)) {
            is NetworkResult.Success -> {
                artifactsResult.data.artifacts.filter { artifact ->
                    artifact.name.endsWith(".apk", ignoreCase = true)
                }.map { artifact ->
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
            is NetworkResult.Error -> emptyList()
        }
    }

    fun downloadArtifact(artifactUiModel: ArtifactUiModel) {
        val artifactFileName = artifactUiModel.name.substringAfterLast('/')
        val taskId = artifactUiModel.taskId

        if (artifactUiModel.downloadState is DownloadState.InProgress || artifactUiModel.downloadState is DownloadState.Downloaded) {
            return
        }
        if (taskId.isBlank()) {
            val blankTaskIdMsg = "Task ID is blank for $artifactFileName"
            updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.DownloadFailed(blankTaskIdMsg))
            return
        }

        viewModelScope.launch {
            updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.InProgress(0f))
            // cacheManager.checkCacheStatus() will be called after download success/failure

            val downloadUrl = artifactUiModel.downloadUrl

            // Ensure the cache directory structure for Treeherder artifacts is respected
            val treeherderCacheDir = cacheManager.getCacheDir("treeherder")
            val outputDir = File(treeherderCacheDir, taskId)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val outputFile = File(outputDir, artifactFileName)

            val result = downloadFileRepository.downloadFile(
                downloadUrl = downloadUrl,
                outputFile = outputFile,
                onProgress = { bytesDownloaded, totalBytes ->
                    val progress = if (totalBytes > 0) {
                        bytesDownloaded.toFloat() / totalBytes.toFloat()
                    } else {
                        0f
                    }
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.InProgress(progress))
                },
            )

            when (result) {
                is NetworkResult.Success -> {
                    updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.Downloaded(result.data))
                    cacheManager.checkCacheStatus() // Update cache status via CacheManager
                    onInstallApk?.invoke(result.data)
                }
                is NetworkResult.Error -> {
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

    fun installApk(file: File) {
        intentManager.installApk(file)
    }
}
