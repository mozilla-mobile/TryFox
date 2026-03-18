package org.mozilla.tryfox

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mozilla.tryfox.data.Artifact
import org.mozilla.tryfox.data.ArtifactsResponse
import org.mozilla.tryfox.data.JobDetails
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.RevisionDetail
import org.mozilla.tryfox.data.RevisionMeta
import org.mozilla.tryfox.data.RevisionResult
import org.mozilla.tryfox.data.TreeherderJobsResponse
import org.mozilla.tryfox.data.TreeherderRevisionResponse
import org.mozilla.tryfox.data.managers.FakeCacheManager
import org.mozilla.tryfox.data.managers.FakeIntentManager
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import org.mozilla.tryfox.ui.screens.MainCoroutineRule
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TryFoxViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineRule = MainCoroutineRule()

    @TempDir
    lateinit var tempCacheDir: File

    private lateinit var cacheManager: FakeCacheManager

    @BeforeEach
    fun setUp() {
        cacheManager = FakeCacheManager(tempCacheDir)
    }

    @AfterEach
    fun tearDown() {
        cacheManager.reset()
    }

    @Test
    fun `searchJobsAndArtifacts keeps preferred signing apk jobs and skips fallback artifact fetches when preferred jobs produce APKs`() = runTest {
        val preferredJob = signedJob(taskId = "preferred-task", jobName = "signing-apk-focus-nightly", appName = "focus")
        val fallbackJob = signedJob(taskId = "fallback-task", jobName = "build-apk-focus-nightly", appName = "focus")
        val repository = FakeTestTreeherderRepository(
            pages = mapOf(
                1 to listOf(preferredJob, fallbackJob),
            ),
            artifactsByTaskId = mapOf(
                "preferred-task" to listOf(apkArtifact("public/build/target.arm64-v8a.apk")),
            ),
        )

        val viewModel = createViewModel(repository)

        viewModel.updateRevision("ed209aa2136b241686ff20489c5cb622348e2ecf")
        viewModel.searchJobsAndArtifacts()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
        assertEquals(listOf(1), repository.requestedPages)
        assertEquals(listOf("preferred-task"), repository.artifactRequests)
        assertEquals(1, viewModel.selectedJobs.size)
        assertEquals("preferred-task", viewModel.selectedJobs.single().taskId)
        assertEquals(1, viewModel.selectedJobs.single().artifacts.size)
        assertTrue(viewModel.isLoadingJobArtifacts.isEmpty())
    }

    @Test
    fun `searchJobsAndArtifacts falls back to broader signed android apk jobs when preferred jobs have no APKs`() = runTest {
        val preferredJob = signedJob(taskId = "preferred-task", jobName = "signing-apk-focus-nightly", appName = "focus")
        val fallbackJob = signedJob(taskId = "fallback-task", jobName = "build-apk-focus-nightly", appName = "focus")
        val repository = FakeTestTreeherderRepository(
            pages = mapOf(
                1 to listOf(preferredJob, fallbackJob),
            ),
            artifactsByTaskId = mapOf(
                "preferred-task" to emptyList(),
                "fallback-task" to listOf(apkArtifact("public/build/target.arm64-v8a.apk")),
            ),
        )

        val viewModel = createViewModel(repository)

        viewModel.updateRevision("ed209aa2136b241686ff20489c5cb622348e2ecf")
        viewModel.searchJobsAndArtifacts()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
        assertIterableEquals(listOf("preferred-task", "fallback-task"), repository.artifactRequests)
        assertEquals(1, viewModel.selectedJobs.size)
        assertEquals("fallback-task", viewModel.selectedJobs.single().taskId)
        assertEquals(1, viewModel.selectedJobs.single().artifacts.size)
        assertTrue(viewModel.isLoadingJobArtifacts.isEmpty())
    }

    @Test
    fun `searchJobsAndArtifacts continues across paged job results until it finds preferred APK jobs`() = runTest {
        val pageOneIgnoredJobs = List(2000) { index ->
            signedJob(
                taskId = "desktop-task-$index",
                jobName = "build-win64-nightly-$index",
                appName = "desktop",
            )
        }
        val pageTwoPreferredJob = signedJob(taskId = "page-two-task", jobName = "signing-apk-fenix-nightly", appName = "fenix")
        val repository = FakeTestTreeherderRepository(
            pages = mapOf(
                1 to pageOneIgnoredJobs,
                2 to listOf(pageTwoPreferredJob),
            ),
            artifactsByTaskId = mapOf(
                "page-two-task" to listOf(apkArtifact("public/build/target.arm64-v8a.apk")),
            ),
        )

        val viewModel = createViewModel(repository)

        viewModel.updateRevision("ed209aa2136b241686ff20489c5cb622348e2ecf")
        viewModel.searchJobsAndArtifacts()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
        assertEquals(listOf(1, 2), repository.requestedPages)
        assertEquals(listOf("page-two-task"), repository.artifactRequests)
        assertEquals(1, viewModel.selectedJobs.size)
        assertEquals("page-two-task", viewModel.selectedJobs.single().taskId)
    }

    @Test
    fun `searchJobsAndArtifacts deduplicates jobs by task id across pages`() = runTest {
        val duplicatedJob = signedJob(taskId = "shared-task", jobName = "signing-apk-focus-nightly", appName = "focus")
        val repository = FakeTestTreeherderRepository(
            pages = mapOf(
                1 to List(2000) { index ->
                    signedJob(
                        taskId = "desktop-task-$index",
                        jobName = "build-win64-nightly-$index",
                        appName = "desktop",
                    )
                },
                2 to listOf(duplicatedJob, duplicatedJob),
            ),
            artifactsByTaskId = mapOf(
                "shared-task" to listOf(apkArtifact("public/build/target.arm64-v8a.apk")),
            ),
        )

        val viewModel = createViewModel(repository)

        viewModel.updateRevision("ed209aa2136b241686ff20489c5cb622348e2ecf")
        viewModel.searchJobsAndArtifacts()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
        assertEquals(listOf("shared-task"), repository.artifactRequests)
        assertEquals(1, viewModel.selectedJobs.size)
        assertEquals("shared-task", viewModel.selectedJobs.single().taskId)
    }

    @Test
    fun `searchJobsAndArtifacts keeps already found jobs when a later page fails`() = runTest {
        val firstPageJob = signedJob(taskId = "first-page-task", jobName = "signing-apk-focus-nightly", appName = "focus")
        val repository = FakeTestTreeherderRepository(
            pages = mapOf(
                1 to List(2000) { index ->
                    if (index == 0) {
                        firstPageJob
                    } else {
                        signedJob(
                            taskId = "desktop-task-$index",
                            jobName = "build-win64-nightly-$index",
                            appName = "desktop",
                        )
                    }
                },
            ),
            artifactsByTaskId = mapOf(
                "first-page-task" to listOf(apkArtifact("public/build/target.arm64-v8a.apk")),
            ),
            pageErrors = mapOf(
                2 to "page 2 failed",
            ),
        )

        val viewModel = createViewModel(repository)

        viewModel.updateRevision("ed209aa2136b241686ff20489c5cb622348e2ecf")
        viewModel.searchJobsAndArtifacts()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading)
        assertNotNull(viewModel.errorMessage)
        assertTrue(viewModel.errorMessage!!.contains("Some jobs could not be loaded"))
        assertEquals(1, viewModel.selectedJobs.size)
        assertEquals("first-page-task", viewModel.selectedJobs.single().taskId)
    }

    @Test
    fun `searchJobsAndArtifacts ignores non apk artifacts and marks ABI compatibility`() = runTest {
        val preferredJob = signedJob(taskId = "preferred-task", jobName = "signing-apk-focus-nightly", appName = "focus")
        val repository = FakeTestTreeherderRepository(
            pages = mapOf(
                1 to listOf(preferredJob),
            ),
            artifactsByTaskId = mapOf(
                "preferred-task" to listOf(
                    genericArtifact("public/build/target.zip"),
                    apkArtifact("public/build/target.arm64-v8a.apk"),
                    apkArtifact("public/build/target.x86_64.apk"),
                ),
            ),
        )

        val viewModel = createViewModel(repository)

        viewModel.updateRevision("ed209aa2136b241686ff20489c5cb622348e2ecf")
        viewModel.searchJobsAndArtifacts()
        advanceUntilIdle()

        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
        val artifacts = viewModel.selectedJobs.single().artifacts
        assertEquals(2, artifacts.size)
        assertTrue(artifacts.single { it.abi.name == "arm64-v8a" }.abi.isSupported)
        assertFalse(artifacts.single { it.abi.name == "x86_64" }.abi.isSupported)
    }

    private fun createViewModel(
        repository: TreeherderRepository,
    ): TryFoxViewModel = TryFoxViewModel(
        fenixRepository = repository,
        downloadFileRepository = org.mozilla.tryfox.data.FakeDownloadFileRepository(),
        cacheManager = cacheManager,
        intentManager = FakeIntentManager(),
        project = "mozilla-central",
        revision = null,
        supportedAbis = listOf("arm64-v8a"),
        elapsedRealtimeProvider = { 0L },
        infoLogger = { _, _ -> 0 },
        ioDispatcher = mainCoroutineRule.testDispatcher,
        mainDispatcher = mainCoroutineRule.testDispatcher,
    )

    private fun signedJob(
        taskId: String,
        jobName: String,
        appName: String,
        jobSymbol: String = "Bs",
    ) = JobDetails(
        appName = appName,
        jobName = jobName,
        jobSymbol = jobSymbol,
        taskId = taskId,
    )

    private fun apkArtifact(name: String) = Artifact(
        storageType = "s3",
        name = name,
        expires = "2027-03-16T09:44:23.244Z",
        contentType = "application/vnd.android.package-archive",
    )

    private fun genericArtifact(name: String) = Artifact(
        storageType = "s3",
        name = name,
        expires = "2027-03-16T09:44:23.244Z",
        contentType = "application/zip",
    )

    private class FakeTestTreeherderRepository(
        private val pages: Map<Int, List<JobDetails>>,
        private val artifactsByTaskId: Map<String, List<Artifact>>,
        private val pageErrors: Map<Int, String> = emptyMap(),
    ) : TreeherderRepository {

        val requestedPages = mutableListOf<Int>()
        val artifactRequests = mutableListOf<String>()

        override suspend fun getPushByRevision(
            project: String,
            revision: String,
        ): NetworkResult<TreeherderRevisionResponse> = NetworkResult.Success(
            TreeherderRevisionResponse(
                meta = RevisionMeta(
                    revision = revision,
                    count = 1,
                    repository = project,
                ),
                results = listOf(
                    RevisionResult(
                        id = 1858186,
                        revision = revision,
                        author = "author@mozilla.com",
                        revisions = listOf(
                            RevisionDetail(
                                resultSetId = 1858186,
                                repositoryId = 1,
                                revision = revision,
                                author = "author@mozilla.com",
                                comments = "Bug 2001527: test patch",
                            ),
                        ),
                        revisionCount = 1,
                        pushTimestamp = 0L,
                        repositoryId = 1,
                    ),
                ),
            ),
        )

        override suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse> {
            throw UnsupportedOperationException("Not needed in this test")
        }

        override suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse> {
            val results = pages.toSortedMap().values.flatten()
            return NetworkResult.Success(TreeherderJobsResponse(results = results))
        }

        override suspend fun getJobsForPushPage(
            pushId: Int,
            page: Int,
            count: Int,
        ): NetworkResult<TreeherderJobsResponse> {
            requestedPages += page
            pageErrors[page]?.let { message ->
                return NetworkResult.Error(message)
            }
            return NetworkResult.Success(
                TreeherderJobsResponse(results = pages[page].orEmpty()),
            )
        }

        override suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse> {
            artifactRequests += taskId
            return NetworkResult.Success(
                ArtifactsResponse(artifacts = artifactsByTaskId[taskId].orEmpty()),
            )
        }
    }
}
