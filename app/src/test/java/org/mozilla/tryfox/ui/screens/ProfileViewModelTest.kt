package org.mozilla.tryfox.ui.screens

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mozilla.tryfox.data.Artifact
import org.mozilla.tryfox.data.ArtifactsResponse
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.FakeHistoryRepository
import org.mozilla.tryfox.data.JobDetails
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.RevisionDetail
import org.mozilla.tryfox.data.RevisionMeta
import org.mozilla.tryfox.data.RevisionResult
import org.mozilla.tryfox.data.TreeherderJobsResponse
import org.mozilla.tryfox.data.TreeherderRevisionResponse
import org.mozilla.tryfox.data.managers.FakeCacheManager
import org.mozilla.tryfox.data.managers.FakeIntentManager
import org.mozilla.tryfox.data.managers.FakeUserDataRepository
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.download.ApkDownloadRequest
import org.mozilla.tryfox.download.model.DownloadStatus
import org.mozilla.tryfox.download.model.PersistedDownloadState
import org.mozilla.tryfox.util.TREEHERDER
import java.io.File

@ExperimentalCoroutinesApi
class ProfileViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: ProfileViewModel
    private lateinit var cacheManager: FakeCacheManager
    private lateinit var downloadCoordinator: FakeApkDownloadCoordinator

    private lateinit var fenixRepository: FakeTreeherderRepository

    private val userDataRepository = FakeUserDataRepository()
    private val intentManager = FakeIntentManager()
    private val historyRepository = FakeHistoryRepository()

    @TempDir
    lateinit var tempCacheDir: File

    @BeforeEach
    fun setUp() = runTest {
        cacheManager = FakeCacheManager(tempCacheDir)
        fenixRepository = FakeTreeherderRepository()
        downloadCoordinator = FakeApkDownloadCoordinator()
        stubProfileSearch()
        viewModel = ProfileViewModel(
            fenixRepository = fenixRepository,
            userDataRepository = userDataRepository,
            cacheManager = cacheManager,
            intentManager = intentManager,
            historyRepository = historyRepository,
            downloadCoordinator = downloadCoordinator,
            authorEmail = "test@example.com",
        )
        advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        cacheManager.reset()
    }

    @Test
    fun `updateAuthorEmail should update the authorEmail state`() = runTest {
        val viewModel = createViewModel(authorEmail = null)
        val newEmail = "test@example.com"

        viewModel.authorEmail.test {
            assertEquals("", awaitItem())

            viewModel.updateAuthorEmail(newEmail)

            assertEquals(newEmail, awaitItem())
        }
    }

    @Test
    fun `author search forwards selected project and rejects malformed emails`() = runTest {
        val projectViewModel = createViewModel(authorEmail = null, project = "mozilla-central")
        fenixRepository.lastAuthorProject = null
        projectViewModel.updateAuthorEmail("not-an-email@")
        projectViewModel.searchByAuthor()
        assertEquals(null, fenixRepository.lastAuthorProject)

        projectViewModel.updateAuthorEmail("test@example.com")
        projectViewModel.searchByAuthor()
        advanceUntilIdle()
        assertEquals("mozilla-central", fenixRepository.lastAuthorProject)
    }

    @Test
    fun `downloadArtifact should enqueue WorkManager work and reflect persisted progress`() = runTest {
        val artifact = viewModel.pushes.value.single().jobs.single().artifacts.single()
        val outputFile = File(cacheManager.getCacheDir(TREEHERDER), "task-123/${artifact.name.substringAfterLast('/')}")

        viewModel.downloadArtifact(artifact)
        advanceUntilIdle()

        assertEquals(1, downloadCoordinator.enqueuedRequests.size)
        val enqueuedRequest = downloadCoordinator.enqueuedRequests.single()
        assertEquals(artifact.uniqueKey, enqueuedRequest.uniqueKey)
        assertEquals(artifact.downloadUrl, enqueuedRequest.downloadUrl)
        assertEquals(outputFile.absolutePath, enqueuedRequest.outputPath)
        assertEquals(TREEHERDER, enqueuedRequest.appName)

        val inProgressArtifact = viewModel.pushes.value.single().jobs.single().artifacts.single()
        assertTrue(inProgressArtifact.downloadState is DownloadState.InProgress)

        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = artifact.uniqueKey,
                downloadUrl = artifact.downloadUrl,
                outputPath = outputFile.absolutePath,
                appName = TREEHERDER,
                fileName = artifact.name.substringAfterLast('/'),
                cacheRelativePath = "$TREEHERDER/task-123/${artifact.name.substringAfterLast('/')}",
                status = DownloadStatus.RUNNING,
                bytesDownloaded = 25,
                totalBytes = 100,
                workId = "work-1",
            ),
        )
        advanceUntilIdle()

        val runningArtifact = viewModel.pushes.value.single().jobs.single().artifacts.single()
        val runningState = runningArtifact.downloadState as DownloadState.InProgress
        assertEquals(0.25f, runningState.progress)
        assertFalse(runningState.isIndeterminate)

        outputFile.parentFile?.mkdirs()
        outputFile.writeText("fake apk")
        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = artifact.uniqueKey,
                downloadUrl = artifact.downloadUrl,
                outputPath = outputFile.absolutePath,
                appName = TREEHERDER,
                fileName = artifact.name.substringAfterLast('/'),
                cacheRelativePath = "$TREEHERDER/task-123/${artifact.name.substringAfterLast('/')}",
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = 100,
                totalBytes = 100,
                workId = "work-1",
            ),
        )
        advanceUntilIdle()

        val downloadedArtifact = viewModel.pushes.value.single().jobs.single().artifacts.single()
        val downloadedState = downloadedArtifact.downloadState as DownloadState.Downloaded
        assertEquals(outputFile.absolutePath, downloadedState.file.absolutePath)
        assertTrue(intentManager.wasInstallApkCalled)
    }

    @Test
    fun `downloadArtifact should map persisted failures to download failed state`() = runTest {
        val artifact = viewModel.pushes.value.single().jobs.single().artifacts.single()
        val failureMessage = "network failed"

        viewModel.downloadArtifact(artifact)
        advanceUntilIdle()

        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = artifact.uniqueKey,
                downloadUrl = artifact.downloadUrl,
                outputPath = File(
                    cacheManager.getCacheDir(TREEHERDER),
                    "task-123/${artifact.name.substringAfterLast('/')}",
                ).absolutePath,
                appName = TREEHERDER,
                fileName = artifact.name.substringAfterLast('/'),
                cacheRelativePath = "$TREEHERDER/task-123/${artifact.name.substringAfterLast('/')}",
                status = DownloadStatus.FAILED,
                errorMessage = failureMessage,
                workId = "work-1",
            ),
        )
        advanceUntilIdle()

        val failedArtifact = viewModel.pushes.value.single().jobs.single().artifacts.single()
        val failedState = failedArtifact.downloadState as DownloadState.DownloadFailed
        assertEquals(failureMessage, failedState.message)
    }

    private fun createViewModel(authorEmail: String?, project: String = "try"): ProfileViewModel =
        ProfileViewModel(
            fenixRepository = fenixRepository,
            userDataRepository = userDataRepository,
            cacheManager = cacheManager,
            intentManager = intentManager,
            historyRepository = historyRepository,
            downloadCoordinator = downloadCoordinator,
            authorEmail = authorEmail,
            project = project,
        )

    private fun stubProfileSearch() {
        val email = "test@example.com"
        fenixRepository.pushesByAuthorResult =
            NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(revision = null, count = 1, repository = "try"),
                    results = listOf(
                        RevisionResult(
                            id = 1,
                            revision = "abc123",
                            author = email,
                            revisions = listOf(
                                RevisionDetail(
                                    resultSetId = 1,
                                    repositoryId = 1,
                                    revision = "abc123",
                                    author = email,
                                    comments = "Bug 123",
                                ),
                            ),
                            revisionCount = 1,
                            pushTimestamp = 1_700_000_000,
                            repositoryId = 1,
                        ),
                    ),
                ),
            )
        fenixRepository.jobsForPushResult =
            NetworkResult.Success(
                TreeherderJobsResponse(
                    results = listOf(
                        JobDetails(
                            appName = "Fenix Nightly",
                            jobName = "Fenix Android APK",
                            jobSymbol = "Bs",
                            taskId = "task-123",
                        ),
                    ),
                ),
            )
        fenixRepository.artifactsForTaskResult =
            NetworkResult.Success(
                ArtifactsResponse(
                    artifacts = listOf(
                        Artifact(
                            storageType = "s3",
                            name = "public/target.apk",
                            expires = "2099-01-01T00:00:00Z",
                            contentType = "application/vnd.android.package-archive",
                        ),
                    ),
                ),
            )
    }

    private class FakeTreeherderRepository : TreeherderRepository {
        var lastAuthorProject: String? = null
        var pushesByAuthorResult: NetworkResult<TreeherderRevisionResponse> =
            NetworkResult.Error("Not stubbed")
        var jobsForPushResult: NetworkResult<TreeherderJobsResponse> =
            NetworkResult.Error("Not stubbed")
        var artifactsForTaskResult: NetworkResult<ArtifactsResponse> =
            NetworkResult.Error("Not stubbed")

        override suspend fun getPushByRevision(
            project: String,
            revision: String,
        ): NetworkResult<TreeherderRevisionResponse> = pushesByAuthorResult

        override suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse> =
            pushesByAuthorResult

        override suspend fun getPushesByAuthor(
            project: String,
            author: String,
        ): NetworkResult<TreeherderRevisionResponse> {
            lastAuthorProject = project
            return pushesByAuthorResult
        }

        override suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse> =
            jobsForPushResult

        override suspend fun getJobsForPushPage(
            pushId: Int,
            page: Int,
            count: Int,
        ): NetworkResult<TreeherderJobsResponse> = jobsForPushResult

        override suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse> =
            artifactsForTaskResult
    }

    private class FakeApkDownloadCoordinator : ApkDownloadCoordinator {
        private val _downloads = MutableStateFlow<Map<String, PersistedDownloadState>>(emptyMap())
        val enqueuedRequests = mutableListOf<ApkDownloadRequest>()

        override val downloads = _downloads.asStateFlow()

        override fun enqueue(request: ApkDownloadRequest): String {
            enqueuedRequests += request
            _downloads.value = _downloads.value + (
                request.uniqueKey to PersistedDownloadState(
                    uniqueKey = request.uniqueKey,
                    downloadUrl = request.downloadUrl,
                    outputPath = request.outputPath,
                    appName = request.appName,
                    fileName = request.fileName,
                    cacheRelativePath = request.cacheRelativePath,
                    status = DownloadStatus.QUEUED,
                    workId = request.uniqueKey,
                )
                )
            return request.uniqueKey
        }

        override fun retry(request: ApkDownloadRequest): String = enqueue(request)

        override fun cancel(uniqueKey: String) {
            _downloads.value[uniqueKey]?.let { current ->
                _downloads.value = _downloads.value + (
                    uniqueKey to current.copy(
                        status = DownloadStatus.CANCELED,
                        updatedAt = System.currentTimeMillis(),
                    )
                    )
            }
        }

        override fun observe(uniqueKey: String) = downloads.map { it[uniqueKey] }

        fun emit(state: PersistedDownloadState) {
            _downloads.value = _downloads.value + (state.uniqueKey to state)
        }
    }
}
