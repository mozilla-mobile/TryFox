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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import logcat.logcat
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.RevisionDetail
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.repositories.HistoryRepository
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import org.mozilla.tryfox.data.repositories.UserDataRepository
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.download.ApkDownloadRequest
import org.mozilla.tryfox.download.model.DownloadStatus
import org.mozilla.tryfox.download.model.PersistedDownloadState
import org.mozilla.tryfox.install.ApkInstallCoordinator
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ArtifactUiModel
import org.mozilla.tryfox.ui.models.JobDetailsUiModel
import org.mozilla.tryfox.ui.models.PushUiModel
import org.mozilla.tryfox.util.TREEHERDER
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private fun bugComment(revisions: List<RevisionDetail>): String? {
    return revisions.firstOrNull { revision ->
        revision.comments.trimStart().startsWith("Bug ", ignoreCase = true) ||
            revision.comments.trimStart().startsWith("[Bug ", ignoreCase = true)
    }?.comments
}

internal fun selectPreferredPushComment(
    revisions: List<RevisionDetail>,
    precedingPushRevisions: List<List<RevisionDetail>> = emptyList(),
): String {
    val ownComment = bugComment(revisions) ?: revisions.firstOrNull()?.comments.orEmpty().ifBlank { "No comment" }
    val isPerfAgainRetry = ownComment.contains("Pushed via `mach try perf-again`", ignoreCase = true)
    return if (isPerfAgainRetry) {
        precedingPushRevisions.firstNotNullOfOrNull(::bugComment) ?: ownComment
    } else {
        ownComment
    }
}

internal fun isPerfAgainRetry(revisions: List<RevisionDetail>): Boolean {
    val firstComment = revisions.firstOrNull()?.comments.orEmpty()
    return firstComment.contains("Pushed via `mach try perf-again`", ignoreCase = true)
}

private val unsignedApkJobNamePattern = Regex("^build-apk-(.+)$", RegexOption.IGNORE_CASE)

/** Removes an unsigned build only when its corresponding signed build has a displayable APK. */
internal fun filterRedundantUnsignedApkJobs(jobs: List<JobDetailsUiModel>): List<JobDetailsUiModel> {
    val availableSignedJobNames = jobs.asSequence()
        .filter(JobDetailsUiModel::isSignedBuild)
        .map { it.jobName.trim().lowercase() }
        .toSet()

    return jobs.filter { job ->
        if (job.isSignedBuild) return@filter true
        val signedEquivalent = unsignedApkJobNamePattern.matchEntire(job.jobName.trim())
            ?.groupValues
            ?.get(1)
            ?.let { "signing-apk-$it" }
            ?.lowercase()
        signedEquivalent == null || signedEquivalent !in availableSignedJobNames
    }
}

/**
 * ViewModel for the Profile screen, responsible for fetching pushes and artifacts by author, managing downloads, and handling user interactions.
 *
 * @param fenixRepository The repository for fetching Fenix-related data.
 * @param userDataRepository The repository for storing and retrieving user data, such as the last searched email.
 * @param cacheManager The manager for handling application cache.
 * @param intentManager The manager for handling intents, such as APK installation.
 * @param authorEmail The initial author email to search for, can be null.
 */
/**
 * State holder for every Treeherder search.  The input is classified once at submission
 * time; from that point on the presentation only consumes [pushes], regardless of whether
 * the repository request was made by revision or by author email.
 */
class SearchViewModel(
    private val fenixRepository: TreeherderRepository,
    private val userDataRepository: UserDataRepository,
    private val cacheManager: CacheManager,
    private val historyRepository: HistoryRepository,
    private val downloadCoordinator: ApkDownloadCoordinator,
    private val installCoordinator: ApkInstallCoordinator,
    authorEmail: String?,
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
    project: String = "try",
) : ViewModel() {

    private data class ArtifactLoadResult(
        val artifacts: List<ArtifactUiModel>,
        val failed: Boolean,
    )

    companion object {
        private const val TAG = "SearchViewModel"
        private const val MAX_PARALLEL_ARTIFACT_REQUESTS = 6
        private val apkJobNameHints = listOf("signing-apk", "android-apk", "apk-focus", "apk-fenix", "apk-reference-browser", "apk-geckoview")
        private val nonAndroidPlatformHints = listOf("ios", "mac", "macos", "macosx", "win", "windows", "linux", "desktop")
        private val androidProductHints = listOf("focus", "fenix", "reference-browser", "geckoview", "android")
    }

    private val _authorEmail = MutableStateFlow(authorEmail ?: "")
    val authorEmail: StateFlow<String> = _authorEmail.asStateFlow()

    /** The shared search field value. Kept as an alias during the email API migration. */
    val query: StateFlow<String> = _authorEmail.asStateFlow()

    private val _selectedProject = MutableStateFlow(project)
    val selectedProject: StateFlow<String> = _selectedProject.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _pushes = MutableStateFlow<List<PushUiModel>>(emptyList())
    val pushes: StateFlow<List<PushUiModel>> = _pushes.asStateFlow()
    private val downloadStates = MutableStateFlow<Map<String, PersistedDownloadState>>(emptyMap())

    val cacheState: StateFlow<CacheManagementState> = cacheManager.cacheState
    val searchHistory = userDataRepository.searchHistoryFlow
    val installStates: StateFlow<Map<String, InstallState>> = installCoordinator.states

    private val deviceSupportedAbis: List<String> by lazy {
        runCatching { Build.SUPPORTED_ABIS.toList() }.getOrDefault(emptyList())
    }

    init {
        logcat(LogPriority.DEBUG, TAG) { "Initializing SearchViewModel for query: $authorEmail" }
        downloadCoordinator.downloads
            .onEach { persistedDownloads ->
                downloadStates.value = persistedDownloads
                syncLoadedStateDownloadStates()
            }
            .launchIn(viewModelScope)

        installCoordinator.successfulInstalls
            .onEach { artifactKey ->
                findArtifact(artifactKey)?.let { downloadedArtifact ->
                    try {
                        updateInstallTimestamp(downloadedArtifact)
                    } catch (_: Exception) {
                        // History is best-effort; installation has already succeeded.
                    }
                }
            }
            .launchIn(viewModelScope)

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
                        unsignedJobs = it.unsignedJobs.map { job ->
                            job.copy(
                                artifacts = job.artifacts.map { artifact ->
                                    artifact.copy(downloadState = DownloadState.NotDownloaded)
                                },
                            )
                        },
                    )
                }
                _pushes.value = updatedPushes
                syncLoadedStateDownloadStates()
            }
        }.launchIn(viewModelScope)

        if (authorEmail != null) {
            submitSearch()
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
        _errorMessage.value = null
    }

    fun updateQuery(query: String) = updateAuthorEmail(query)

    fun updateSelectedProject(project: String) {
        _selectedProject.value = project
    }

    fun setEmailFromDeepLinkAndSearch(project: String?, email: String) {
        _selectedProject.value = project ?: "try"
        _authorEmail.value = email
        searchByAuthor()
    }

    /** Applies either kind of deep link to the same model and executes the matching request. */
    fun setQueryFromDeepLinkAndSearch(project: String?, query: String) {
        _selectedProject.value = project ?: "try"
        _authorEmail.value = query
        submitSearch()
    }

    /**
     * The sole search entry point used by the shared screen. Query kind changes only the
     * Treeherder operation; loading, errors, results and artifact actions stay shared.
     */
    fun submitSearch() {
        when (val parsed = SearchQueryClassifier.classify(_authorEmail.value).getOrNull()) {
            is SearchQuery.Email -> searchByAuthor()
            is SearchQuery.Revision -> searchByRevision(parsed.value)
            null -> Unit
        }
    }

    private fun searchByRevision(revision: String) {
        if (revision.isBlank()) {
            _errorMessage.value = "Please enter a revision to search."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _pushes.value = emptyList()
            when (val pushResult = fenixRepository.getPushByRevision(_selectedProject.value, revision)) {
                is NetworkResult.Success -> {
                    val push = pushResult.data.results.firstOrNull()
                    if (push == null) {
                        _errorMessage.value = "No push found for project: ${_selectedProject.value}, revision: $revision"
                    } else {
                        val jobsResult = fenixRepository.getJobsForPush(push.id)
                        if (jobsResult is NetworkResult.Success) {
                            val (signedCandidates, unsignedCandidates) = selectJobsBySigning(
                                jobsResult.data.results.filter(::isAndroidApkCandidate),
                            )
                            val jobs = mutableListOf<JobDetailsUiModel>()
                            for (job in signedCandidates + unsignedCandidates) {
                                loadJob(job)?.let(jobs::add)
                            }
                            val visibleJobs = filterRedundantUnsignedApkJobs(jobs)
                            val signedJobs = visibleJobs.filter(JobDetailsUiModel::isSignedBuild)
                            val unsignedJobs = visibleJobs.filterNot(JobDetailsUiModel::isSignedBuild)
                            if (signedJobs.isEmpty() && unsignedJobs.isEmpty()) {
                                _errorMessage.value = "No APK builds found for this revision."
                            } else {
                                val precedingRevisions = if (isPerfAgainRetry(push.revisions)) {
                                    when (
                                        val authorPushes = fenixRepository.getPushesByAuthor(
                                            _selectedProject.value,
                                            push.author,
                                        )
                                    ) {
                                        is NetworkResult.Success -> {
                                            val pushIndex = authorPushes.data.results.indexOfFirst { it.id == push.id }
                                            authorPushes.data.results
                                                .take(pushIndex.coerceAtLeast(0))
                                                .asReversed()
                                                .map { it.revisions }
                                        }
                                        is NetworkResult.Error -> emptyList()
                                    }
                                } else {
                                    emptyList()
                                }
                                _pushes.value = listOf(
                                    PushUiModel(
                                        pushComment = selectPreferredPushComment(push.revisions, precedingRevisions),
                                        author = push.author,
                                        jobs = signedJobs,
                                        unsignedJobs = unsignedJobs,
                                        revision = push.revision,
                                        pushTimestamp = push.pushTimestamp,
                                    ),
                                )
                                syncLoadedStateDownloadStates()
                                userDataRepository.recordSearch(_selectedProject.value, revision)
                            }
                        } else {
                            _errorMessage.value = "Error fetching jobs: ${(jobsResult as NetworkResult.Error).message}"
                        }
                    }
                }
                is NetworkResult.Error -> {
                    _errorMessage.value = "Error fetching revision details for ${_selectedProject.value}: ${pushResult.message}"
                }
            }
            _isLoading.value = false
        }
    }

    fun searchByAuthor() {
        val emailToSearch = _authorEmail.value
        logcat(TAG) { "searchByAuthor called for email: $emailToSearch" }
        if (emailToSearch.isBlank()) {
            _errorMessage.value = "Please enter an author email to search."
            logcat(LogPriority.WARN, TAG) { "Search attempt with blank email" }
            return
        }
        if (SearchQueryClassifier.classify(emailToSearch).getOrNull() !is SearchQuery.Email) {
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _pushes.value = emptyList()
            logcat(LogPriority.DEBUG, TAG) { "Starting search..." }
            val artifactSemaphore = Semaphore(MAX_PARALLEL_ARTIFACT_REQUESTS)

            when (val result = fenixRepository.getPushesByAuthor(_selectedProject.value, emailToSearch)) {
                is NetworkResult.Success -> {
                    logcat(
                        LogPriority.DEBUG,
                        TAG,
                    ) { "getPushesByAuthor success, processing ${result.data.results.size} pushes" }
                    val failedPushCount = AtomicInteger(0)
                    val pushesWithJobsAndArtifacts = result.data.results.mapIndexed { pushIndex, pushResult ->
                        async {
                            val jobsResult = fenixRepository.getJobsForPush(pushResult.id)
                            if (jobsResult is NetworkResult.Success) {
                                val (signedCandidates, unsignedCandidates) = selectJobsBySigning(
                                    jobsResult.data.results.filter(::isAndroidApkCandidate),
                                )
                                val selectedJobs = signedCandidates + unsignedCandidates
                                if (selectedJobs.isNotEmpty()) {
                                    val jobsWithArtifacts = selectedJobs.map { jobDetails ->
                                        async {
                                            val artifactResult = artifactSemaphore.withPermit {
                                                fetchArtifacts(jobDetails.taskId)
                                            }
                                            if (artifactResult.failed) {
                                                failedPushCount.incrementAndGet()
                                            }
                                            artifactResult.artifacts.takeIf { it.isNotEmpty() }?.let { artifacts ->
                                                jobWithArtifacts(jobDetails, artifacts)
                                            }
                                        }
                                    }.awaitAll().filterNotNull()

                                    val visibleJobs = filterRedundantUnsignedApkJobs(jobsWithArtifacts)
                                    if (visibleJobs.isNotEmpty()) {
                                        PushUiModel(
                                            pushComment = selectPreferredPushComment(
                                                revisions = pushResult.revisions,
                                                precedingPushRevisions = result.data.results
                                                    .take(pushIndex)
                                                    .asReversed()
                                                    .map { it.revisions },
                                            ),
                                            author = pushResult.author,
                                            jobs = visibleJobs.filter(JobDetailsUiModel::isSignedBuild),
                                            unsignedJobs = visibleJobs.filterNot(JobDetailsUiModel::isSignedBuild),
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
                                        "No eligible, non-test APK jobs for push ID: ${pushResult.id}"
                                    }
                                    null
                                }
                            } else {
                                failedPushCount.incrementAndGet()
                                logcat(LogPriority.WARN, TAG) {
                                    "getJobsForPush failed for push ID: ${pushResult.id}: " +
                                    (jobsResult as NetworkResult.Error).message
                                }
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()

                    _pushes.value = pushesWithJobsAndArtifacts
                    syncLoadedStateDownloadStates()
                    if (pushesWithJobsAndArtifacts.isNotEmpty()) {
                        userDataRepository.recordSearch(_selectedProject.value, emailToSearch)
                    }
                    logcat(TAG) { "Search finished, ${_pushes.value.size} pushes with artifacts found." }
                    if (failedPushCount.get() > 0) {
                        _errorMessage.value = "Some pushes could not be loaded."
                    } else if (pushesWithJobsAndArtifacts.isEmpty()) {
                        _errorMessage.value = "No APK builds found for this author."
                        logcat(TAG) { "No APK builds found for author." }
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

    private suspend fun fetchArtifacts(taskId: String): ArtifactLoadResult {
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
                // A task can expose several ABI variants.  Surface only the first variant
                // matching Android's device ABI preference order.
                val selectedArtifact = deviceSupportedAbis.asSequence().mapNotNull { preferredAbi ->
                    filteredApks.firstOrNull { artifact ->
                        artifact.abi.equals(preferredAbi, ignoreCase = true)
                    }
                }.firstOrNull() ?: deviceSupportedAbis
                    .takeIf { it.isEmpty() }
                    ?.let { filteredApks.firstOrNull() }
                val artifacts = selectedArtifact?.let { artifact -> listOf(artifact) }.orEmpty().map { artifact ->
                    val artifactFileName = artifact.name.substringAfterLast('/')
                    val uniqueKey = "$taskId/$artifactFileName"
                    val downloadState = resolveDownloadState(
                        artifactName = artifactFileName,
                        taskId = taskId,
                        uniqueKey = uniqueKey,
                    )
                    ArtifactUiModel(
                        name = artifact.name,
                        taskId = taskId,
                        abi = AbiUiModel(
                            name = artifact.abi,
                            isSupported = true,
                        ),
                        downloadUrl = artifact.getDownloadUrl(taskId),
                        expires = artifact.expires,
                        downloadState = downloadState,
                        uniqueKey = "$taskId/${artifact.name.substringAfterLast('/')}",
                    )
                }
                ArtifactLoadResult(artifacts = artifacts, failed = false)
            }

            is NetworkResult.Error -> {
                logcat(LogPriority.WARN, TAG) {
                    "fetchArtifacts error for taskId $taskId: ${artifactsResult.message}"
                }
                ArtifactLoadResult(artifacts = emptyList(), failed = true)
            }
        }
    }

    private fun isAndroidApkCandidate(job: org.mozilla.tryfox.data.JobDetails): Boolean {
        if (job.isTest) return false
        val appName = job.appName.lowercase()
        val jobName = job.jobName.lowercase()
        val hasAndroidSource = jobName.contains("build-android") ||
            androidProductHints.any { hint -> jobName.contains(hint) || appName == hint }
        if (!hasAndroidSource || nonAndroidPlatformHints.any(jobName::contains)) return false
        return apkJobNameHints.any(jobName::contains) || jobName.contains("apk")
    }

    /** Keeps the existing signing-job preference while loading unsigned candidates as a separate group. */
    private fun selectJobsBySigning(
        candidates: List<org.mozilla.tryfox.data.JobDetails>,
    ): Pair<List<org.mozilla.tryfox.data.JobDetails>, List<org.mozilla.tryfox.data.JobDetails>> {
        val signedCandidates = candidates.filter { it.isSignedBuild }
        val preferredSignedCandidates = signedCandidates
            .filter { it.jobName.contains("signing-apk", ignoreCase = true) }
            .ifEmpty { signedCandidates }
        return preferredSignedCandidates to candidates.filterNot { it.isSignedBuild }
    }

    private suspend fun loadJob(job: org.mozilla.tryfox.data.JobDetails): JobDetailsUiModel? {
        val artifactResult = fetchArtifacts(job.taskId)
        return artifactResult.artifacts.takeIf { it.isNotEmpty() }?.let { artifacts -> jobWithArtifacts(job, artifacts) }
    }

    private fun jobWithArtifacts(
        job: org.mozilla.tryfox.data.JobDetails,
        artifacts: List<ArtifactUiModel>,
    ) = JobDetailsUiModel(
        appName = job.appName,
        jobName = job.jobName,
        jobSymbol = job.jobSymbol,
        taskId = job.taskId,
        isSignedBuild = job.isSignedBuild,
        isTest = job.isTest,
        artifacts = artifacts,
    )

    fun getDownloadedFile(artifactName: String, taskId: String): File? {
        if (taskId.isBlank()) return null
        val taskSpecificDir = File(cacheManager.getCacheDir(TREEHERDER), taskId)
        val outputFile = File(taskSpecificDir, artifactName)
        val exists = outputFile.exists()
        logcat(
            LogPriority.VERBOSE,
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
            ) { "Enqueuing WorkManager download for ${artifactUiModel.name}" }
            val downloadedArtifact = findArtifact(artifactUiModel.uniqueKey)
            if (downloadedArtifact != null) {
                try {
                    upsertHistoryEntry(downloadedArtifact)
                } catch (_: Exception) {
                    // History is best-effort; never block downloads.
                }
            }
            updateArtifactDownloadState(taskId, artifactUiModel.name, DownloadState.InProgress(0f))

            val outputDir = File(cacheManager.getCacheDir(TREEHERDER), taskId)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
                logcat(
                    LogPriority.VERBOSE,
                    TAG,
                ) { "Created output directory: ${outputDir.absolutePath}" }
            }
            val outputFile = File(outputDir, artifactFileName)
            logcat(LogPriority.DEBUG, TAG) { "Output file: ${outputFile.absolutePath}" }

            val request = ApkDownloadRequest(
                uniqueKey = artifactUiModel.uniqueKey,
                downloadUrl = artifactUiModel.downloadUrl,
                outputFile = outputFile,
                appName = TREEHERDER,
                fileName = artifactFileName,
                cacheRelativePath = "$TREEHERDER/$taskId/$artifactFileName",
            )

            try {
                val workId = downloadCoordinator.enqueue(request)
                logcat(LogPriority.DEBUG, TAG) {
                    "Download enqueued uniqueKey=${artifactUiModel.uniqueKey} workId=$workId " +
                        "outputPath=${outputFile.absolutePath}"
                }
                downloadStates.value = downloadCoordinator.downloads.value
                syncLoadedStateDownloadStates()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, TAG) {
                    "Failed to enqueue download for ${artifactUiModel.name}: ${e.message}"
                }
                updateArtifactDownloadState(
                    taskId,
                    artifactUiModel.name,
                    DownloadState.DownloadFailed(e.message),
                )
                cacheManager.checkCacheStatus()
            }
        }
    }

    fun installArtifact(artifactUiModel: ArtifactUiModel) {
        val downloadState = artifactUiModel.downloadState as? DownloadState.Downloaded ?: return
        installCoordinator.install(artifactUiModel.uniqueKey, downloadState.file)
    }

    fun cancelInstallConflict(artifactKey: String) = installCoordinator.cancelConflict(artifactKey)

    fun confirmUninstallAndRetry(artifactKey: String) = installCoordinator.confirmUninstallAndRetry(artifactKey)

    fun openInstalledApp(packageName: String) = installCoordinator.openInstalledApp(packageName)

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
            unsignedJobs =
                unsignedJobs.map { job: JobDetailsUiModel ->
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

    private fun syncLoadedStateDownloadStates() {
        val persistedDownloads = downloadStates.value
        _pushes.value = _pushes.value.map { push ->
            push.copy(
                jobs = push.jobs.map { job ->
                    job.copy(
                        artifacts = job.artifacts.map { artifact ->
                            artifact.copy(
                                downloadState = resolveDownloadState(
                                    artifactName = artifact.name.substringAfterLast('/'),
                                    taskId = artifact.taskId,
                                    uniqueKey = artifact.uniqueKey,
                                ),
                            )
                        },
                    )
                },
                unsignedJobs = push.unsignedJobs.map { job ->
                    job.copy(
                        artifacts = job.artifacts.map { artifact ->
                            artifact.copy(
                                downloadState = resolveDownloadState(
                                    artifactName = artifact.name.substringAfterLast('/'),
                                    taskId = artifact.taskId,
                                    uniqueKey = artifact.uniqueKey,
                                ),
                            )
                        },
                    )
                },
            )
        }
    }

    private fun resolveDownloadState(
        artifactName: String,
        taskId: String,
        uniqueKey: String,
    ): DownloadState {
        val downloadedFile = getDownloadedFile(artifactName, taskId)
        return downloadStates.value[uniqueKey]?.toDownloadState(downloadedFile)
            ?: if (downloadedFile != null) {
                DownloadState.Downloaded(downloadedFile)
            } else {
                DownloadState.NotDownloaded
            }
    }

    private fun PersistedDownloadState.toDownloadState(file: File?): DownloadState =
        when (status) {
            DownloadStatus.QUEUED,
            DownloadStatus.RUNNING,
                -> DownloadState.InProgress(
                progress = progress ?: 0f,
                isIndeterminate = totalBytes <= 0L,
            )

            DownloadStatus.SUCCEEDED -> if (file != null && file.exists()) {
                DownloadState.Downloaded(file)
            } else {
                DownloadState.NotDownloaded
            }

            DownloadStatus.FAILED -> DownloadState.DownloadFailed(errorMessage)
            DownloadStatus.CANCELED -> DownloadState.NotDownloaded
        }

    private fun findArtifact(uniqueKey: String): DownloadedArtifact? =
        _pushes.value.firstNotNullOfOrNull { push ->
            (push.jobs + push.unsignedJobs).firstNotNullOfOrNull { job ->
                job.artifacts.firstOrNull { artifact ->
                    artifact.uniqueKey == uniqueKey
                }?.let { artifact -> DownloadedArtifact(push, job, artifact) }
            }
        }

    private suspend fun upsertHistoryEntry(downloadedArtifact: DownloadedArtifact) {
        val push = downloadedArtifact.push
        val job = downloadedArtifact.job
        val artifact = downloadedArtifact.artifact
        val existingEntry = historyRepository.historyEntries.value.firstOrNull { it.uniqueKey == artifact.uniqueKey }
        historyRepository.upsertHistoryEntry(buildHistoryEntry(push, job, artifact, existingEntry))
    }

    private suspend fun updateInstallTimestamp(downloadedArtifact: DownloadedArtifact) {
        val existingEntry = historyRepository.historyEntries.value.firstOrNull {
            it.uniqueKey == downloadedArtifact.artifact.uniqueKey
        }
        val baseEntry = existingEntry ?: buildHistoryEntry(
            downloadedArtifact.push,
            downloadedArtifact.job,
            downloadedArtifact.artifact,
            existingEntry,
        )
        historyRepository.upsertHistoryEntry(
            baseEntry.copy(lastInstallerLaunchTimestamp = currentTimeMillisProvider()),
        )
    }

    private fun buildHistoryEntry(
        push: PushUiModel,
        job: JobDetailsUiModel,
        artifact: ArtifactUiModel,
        existingEntry: TreeherderInstallHistoryEntry?,
    ): TreeherderInstallHistoryEntry {
        val artifactFileName = artifact.name.substringAfterLast('/')
        return TreeherderInstallHistoryEntry(
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
            historyRecordedTimestamp = existingEntry?.historyRecordedTimestamp ?: currentTimeMillisProvider(),
            lastInstallerLaunchTimestamp = existingEntry?.lastInstallerLaunchTimestamp,
        )
    }

    private data class DownloadedArtifact(
        val push: PushUiModel,
        val job: JobDetailsUiModel,
        val artifact: ArtifactUiModel,
    )
}
