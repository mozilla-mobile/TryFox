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
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.managers.IntentManager
import org.mozilla.tryfox.data.repositories.DownloadFileRepository
import org.mozilla.tryfox.data.repositories.HistoryRepository
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ArtifactUiModel
import org.mozilla.tryfox.ui.models.JobDetailsUiModel
import org.mozilla.tryfox.util.TREEHERDER
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
    private val historyRepository: HistoryRepository,
    project: String?,
    revision: String?,
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    private val elapsedRealtimeProvider: () -> Long = SystemClock::elapsedRealtime,
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
    private val infoLogger: (String, String) -> Int = Log::d,
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

    var relevantPushTimestamp by mutableStateOf<Long?>(null)
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
            if (state !is CacheManagementState.Clearing) {
                refreshArtifactDownloadStatesFromCache()
            }
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
        relevantPushTimestamp = null
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
        infoLogger(
            TAG,
            "getDownloadedFile artifactName=$artifactName, taskId=$taskId, " +
                "path=${outputFile.absolutePath}, exists=${outputFile.exists()}",
        )
        return if (outputFile.exists()) outputFile else null
    }

    fun checkCacheStatus() {
        cacheManager.checkCacheStatus()
        refreshArtifactDownloadStatesFromCache()
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
            relevantPushTimestamp = null
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
                        if (foundComment == null) {
                            foundComment = firstPushResult.revisions.firstOrNull()?.comments ?: "No comment"
                        }
                        relevantPushAuthor = firstPushResult.author
                        relevantPushTimestamp = firstPushResult.pushTimestamp
                    } else {
                        relevantPushAuthor = null
                        relevantPushTimestamp = null
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
                    ).also {
                        infoLogger(
                            TAG,
                            "fetchArtifacts resolved artifact taskId=$taskId, artifactName=${artifact.name}, " +
                                "artifactFileName=$artifactFileName, uniqueKey=${it.uniqueKey}, " +
                                "downloadState=${downloadState.javaClass.simpleName}",
                        )
                    }
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
            try {
                upsertHistoryEntry(job = findJob(taskId), artifact = artifactUiModel)
            } catch (_: Exception) {
                // History is best-effort; never block downloads.
            }
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
            infoLogger(
                TAG,
                "downloadArtifact output taskId=$taskId, artifactName=${artifactUiModel.name}, " +
                    "artifactFileName=$artifactFileName, outputPath=${outputFile.absolutePath}",
            )

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
                    onInstallApk?.let { installCallback ->
                        try {
                            updateInstallTimestamp(job = findJob(taskId), artifact = artifactUiModel)
                        } catch (_: Exception) {
                            // History is best-effort; never block installation.
                        }
                        installCallback(result.data)
                    }
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

    private fun refreshArtifactDownloadStatesFromCache() {
        if (selectedJobs.isEmpty()) {
            checkAndUpdateDownloadingStatus()
            return
        }

        selectedJobs = selectedJobs.map { job ->
            job.copy(
                artifacts = job.artifacts.map { artifact ->
                    when (artifact.downloadState) {
                        is DownloadState.InProgress,
                        is DownloadState.DownloadFailed,
                        -> artifact
                        else -> {
                            val artifactFileName = artifact.name.substringAfterLast('/')
                            val downloadedFile = getDownloadedFile(artifactFileName, artifact.taskId)
                            val refreshedState = downloadedFile?.let { DownloadState.Downloaded(it) }
                                ?: DownloadState.NotDownloaded
                            artifact.copy(downloadState = refreshedState)
                        }
                    }
                },
            )
        }
        checkAndUpdateDownloadingStatus()
    }

    fun installApk(file: File) {
        val downloadedArtifact = findDownloadedArtifact(file)
        if (downloadedArtifact == null) {
            intentManager.installApk(file)
            return
        }

        viewModelScope.launch {
            try {
                updateInstallTimestamp(
                    job = downloadedArtifact.job,
                    artifact = downloadedArtifact.artifact,
                )
            } catch (_: Exception) {
                // History is best-effort; never block installation.
            }
            intentManager.installApk(file)
        }
    }

    private fun findJob(taskId: String): JobDetailsUiModel? =
        selectedJobs.firstOrNull { it.taskId == taskId }

    private fun findDownloadedArtifact(file: File): DownloadedArtifact? =
        selectedJobs.firstNotNullOfOrNull { job ->
            job.artifacts.firstOrNull { artifact ->
                val downloadState = artifact.downloadState
                downloadState is DownloadState.Downloaded && downloadState.file.absolutePath == file.absolutePath
            }?.let { artifact -> DownloadedArtifact(job, artifact) }
        }

    private suspend fun upsertHistoryEntry(
        job: JobDetailsUiModel?,
        artifact: ArtifactUiModel,
    ) {
        if (job == null || relevantPushTimestamp == null) {
            return
        }
        val existingEntry = historyRepository.historyEntries.value.firstOrNull { it.uniqueKey == artifact.uniqueKey }
        historyRepository.upsertHistoryEntry(buildHistoryEntry(job, artifact, existingEntry))
    }

    private suspend fun updateInstallTimestamp(
        job: JobDetailsUiModel?,
        artifact: ArtifactUiModel,
    ) {
        if (job == null || relevantPushTimestamp == null) {
            return
        }
        val existingEntry = historyRepository.historyEntries.value.firstOrNull { it.uniqueKey == artifact.uniqueKey }
        val baseEntry = existingEntry ?: buildHistoryEntry(job, artifact, existingEntry)
        historyRepository.upsertHistoryEntry(
            baseEntry.copy(lastInstallerLaunchTimestamp = currentTimeMillisProvider()),
        )
    }

    private fun buildHistoryEntry(
        job: JobDetailsUiModel,
        artifact: ArtifactUiModel,
        existingEntry: TreeherderInstallHistoryEntry?,
    ): TreeherderInstallHistoryEntry {
        val artifactFileName = artifact.name.substringAfterLast('/')
        return TreeherderInstallHistoryEntry(
            project = selectedProject,
            revision = revision,
            commitMessage = relevantPushComment ?: "No comment",
            author = relevantPushAuthor,
            pushTimestamp = relevantPushTimestamp ?: 0L,
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
            historyRecordedTimestamp = existingEntry?.historyRecordedTimestamp ?: currentTimeMillisProvider(),
            lastInstallerLaunchTimestamp = existingEntry?.lastInstallerLaunchTimestamp,
        )
    }

    private data class DownloadedArtifact(
        val job: JobDetailsUiModel,
        val artifact: ArtifactUiModel,
    )
}
