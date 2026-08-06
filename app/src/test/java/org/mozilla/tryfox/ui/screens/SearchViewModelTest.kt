package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
import org.mozilla.tryfox.data.managers.FakeUserDataRepository
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.download.model.PersistedDownloadState
import org.mozilla.tryfox.install.ApkInstallCoordinator
import org.mozilla.tryfox.install.TryBuildProvenance
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineRule = MainCoroutineRule()

    @TempDir
    lateinit var cacheDir: File

    @Test
    fun `install uses the project retained by the loaded result`() = runTest {
        val repository = mock<TreeherderRepository>()
        val installCoordinator = mock<ApkInstallCoordinator>()
        whenever(installCoordinator.states).thenReturn(MutableStateFlow(emptyMap()))
        whenever(installCoordinator.successfulInstalls).thenReturn(MutableSharedFlow())
        whenever(repository.getPushByRevision("mozilla-central", "abcdef123456"))
            .thenReturn(NetworkResult.Success(revisionResponse()))
        whenever(repository.getJobsForPush(1)).thenReturn(
            NetworkResult.Success(
                TreeherderJobsResponse(
                    listOf(JobDetails("fenix", "build-android-fenix-apk", "Bsign", "task-id")),
                ),
            ),
        )
        whenever(repository.getArtifactsForTask("task-id")).thenReturn(
            NetworkResult.Success(
                ArtifactsResponse(
                    listOf(Artifact("s3", "public/build/target.arm64-v8a.apk", "", "application/vnd.android.package-archive")),
                ),
            ),
        )
        val historyRepository = FakeHistoryRepository()
        val viewModel = SearchViewModel(
            fenixRepository = repository,
            userDataRepository = FakeUserDataRepository(),
            cacheManager = FakeCacheManager(cacheDir),
            historyRepository = historyRepository,
            downloadCoordinator = FakeDownloadCoordinator(),
            installCoordinator = installCoordinator,
            authorEmail = "abcdef123456",
            project = "mozilla-central",
        )

        viewModel.submitSearch()
        advanceUntilIdle()
        val artifact = viewModel.pushes.value.single().jobs.single().artifacts.single()
        val downloadedFile = File(cacheDir, "fenix-debug.apk")
        viewModel.updateSelectedProject("try")
        viewModel.downloadArtifact(artifact)
        advanceUntilIdle()

        assertEquals("mozilla-central", historyRepository.recordedEntries.single().project)

        artifact.downloadState = DownloadState.Downloaded(downloadedFile)

        viewModel.installArtifact(artifact)

        val provenance = argumentCaptor<TryBuildProvenance>()
        verify(installCoordinator).install(eq(artifact.uniqueKey), eq(downloadedFile), provenance.capture())
        assertEquals("mozilla-central", provenance.firstValue.project)
    }

    @Test
    fun `author pagination uses an inclusive timestamp cursor`() = runTest {
        val repository = mock<TreeherderRepository>()
        val installCoordinator = mock<ApkInstallCoordinator>()
        whenever(installCoordinator.states).thenReturn(MutableStateFlow(emptyMap()))
        whenever(installCoordinator.successfulInstalls).thenReturn(MutableSharedFlow())
        whenever(repository.getPushesByAuthor("try", "developer@example.com", 20, 0, null))
            .thenReturn(NetworkResult.Success(authorResponse((1..20).toList())))
        whenever(repository.getPushesByAuthor("try", "developer@example.com", 21, 0, 20))
            .thenReturn(NetworkResult.Success(authorResponse((20..40).toList())))
        whenever(repository.getJobsForPush(1)).thenReturn(
            NetworkResult.Success(
                TreeherderJobsResponse(
                    listOf(JobDetails("fenix", "build-android-fenix-apk", "Bsign", "task-id")),
                ),
            ),
        )
        whenever(repository.getArtifactsForTask("task-id")).thenReturn(
            NetworkResult.Success(
                ArtifactsResponse(
                    listOf(Artifact("s3", "public/build/target.arm64-v8a.apk", "", "application/vnd.android.package-archive")),
                ),
            ),
        )
        whenever(repository.getJobsForPush(21)).thenReturn(
            NetworkResult.Success(
                TreeherderJobsResponse(
                    listOf(JobDetails("fenix", "build-android-fenix-apk", "Bsign", "task-id")),
                ),
            ),
        )
        for (id in 2..20) {
            whenever(repository.getJobsForPush(id)).thenReturn(NetworkResult.Success(TreeherderJobsResponse(emptyList())))
        }
        for (id in 22..40) {
            whenever(repository.getJobsForPush(id)).thenReturn(NetworkResult.Success(TreeherderJobsResponse(emptyList())))
        }
        val viewModel = SearchViewModel(
            fenixRepository = repository,
            userDataRepository = FakeUserDataRepository(),
            cacheManager = FakeCacheManager(cacheDir),
            historyRepository = FakeHistoryRepository(),
            downloadCoordinator = FakeDownloadCoordinator(),
            installCoordinator = installCoordinator,
            authorEmail = "developer@example.com",
        )

        viewModel.searchByAuthor()
        advanceUntilIdle()
        viewModel.loadMorePushes()
        advanceUntilIdle()

        assertEquals(listOf("revision-1", "revision-21"), viewModel.pushes.value.map { it.revision })
        verify(repository).getPushesByAuthor("try", "developer@example.com", 20, 0, null)
        verify(repository).getPushesByAuthor("try", "developer@example.com", 21, 0, 20)
    }

    @Test
    fun `blank query checks the next page when the newest page has no APK`() = runTest {
        val repository = mock<TreeherderRepository>()
        val installCoordinator = mock<ApkInstallCoordinator>()
        whenever(installCoordinator.states).thenReturn(MutableStateFlow(emptyMap()))
        whenever(installCoordinator.successfulInstalls).thenReturn(MutableSharedFlow())
        whenever(repository.getRecentPushes("try", 20, 0))
            .thenReturn(NetworkResult.Success(recentResponse((1..20).toList())))
        whenever(repository.getRecentPushes("try", 50, 20))
            .thenReturn(NetworkResult.Success(recentResponse((21..70).toList())))
        for (id in 1..20) {
            whenever(repository.getJobsForPush(id)).thenReturn(NetworkResult.Success(noApkJobs()))
        }
        whenever(repository.getJobsForPush(21)).thenReturn(
            NetworkResult.Success(TreeherderJobsResponse(listOf(JobDetails("fenix", "build-android-fenix-apk", "Bsign", "task-id")))),
        )
        whenever(repository.getArtifactsForTask("task-id")).thenReturn(
            NetworkResult.Success(ArtifactsResponse(listOf(Artifact("s3", "public/build/target.arm64-v8a.apk", "", "application/vnd.android.package-archive")))),
        )
        for (id in 22..70) {
            whenever(repository.getJobsForPush(id)).thenReturn(NetworkResult.Success(noApkJobs()))
        }
        val viewModel = SearchViewModel(
            fenixRepository = repository,
            userDataRepository = FakeUserDataRepository(),
            cacheManager = FakeCacheManager(cacheDir),
            historyRepository = FakeHistoryRepository(),
            downloadCoordinator = FakeDownloadCoordinator(),
            installCoordinator = installCoordinator,
            authorEmail = "",
        )

        viewModel.submitSearch()
        advanceUntilIdle()

        assertEquals(listOf("revision-21"), viewModel.pushes.value.map { it.revision })
        verify(repository).getRecentPushes("try", 20, 0)
        verify(repository).getRecentPushes("try", 50, 20)
    }

    @Test
    fun `failed empty-page fallback leaves load more available for a new attempt`() = runTest {
        val repository = mock<TreeherderRepository>()
        val installCoordinator = mock<ApkInstallCoordinator>()
        whenever(installCoordinator.states).thenReturn(MutableStateFlow(emptyMap()))
        whenever(installCoordinator.successfulInstalls).thenReturn(MutableSharedFlow())
        whenever(repository.getRecentPushes("try", 20, 0))
            .thenReturn(NetworkResult.Success(recentResponse((1..20).toList())))
        whenever(repository.getRecentPushes("try", 20, 20))
            .thenReturn(NetworkResult.Success(recentResponse((21..40).toList())))
        whenever(repository.getRecentPushes("try", 50, 40))
            .thenReturn(NetworkResult.Error("fallback failed"))
        whenever(repository.getRecentPushes("try", 20, 40))
            .thenReturn(NetworkResult.Success(recentResponse((41..60).toList())))
        whenever(repository.getRecentPushes("try", 50, 60))
            .thenReturn(NetworkResult.Error("fallback failed again"))
        whenever(repository.getJobsForPush(1)).thenReturn(
            NetworkResult.Success(TreeherderJobsResponse(listOf(JobDetails("fenix", "build-android-fenix-apk", "Bsign", "task-id")))),
        )
        whenever(repository.getArtifactsForTask("task-id")).thenReturn(
            NetworkResult.Success(ArtifactsResponse(listOf(Artifact("s3", "public/build/target.arm64-v8a.apk", "", "application/vnd.android.package-archive")))),
        )
        for (id in 2..60) {
            whenever(repository.getJobsForPush(id)).thenReturn(NetworkResult.Success(noApkJobs()))
        }
        val viewModel = SearchViewModel(
            fenixRepository = repository,
            userDataRepository = FakeUserDataRepository(),
            cacheManager = FakeCacheManager(cacheDir),
            historyRepository = FakeHistoryRepository(),
            downloadCoordinator = FakeDownloadCoordinator(),
            installCoordinator = installCoordinator,
            authorEmail = "",
        )

        viewModel.submitSearch()
        advanceUntilIdle()
        viewModel.loadMorePushes()
        advanceUntilIdle()

        assertTrue(viewModel.canLoadMore.value)
        assertEquals(null, viewModel.loadMoreError.value)
        verify(repository, never()).getRecentPushes("try", 20, 40)

        viewModel.loadMorePushes()
        advanceUntilIdle()

        verify(repository).getRecentPushes("try", 20, 40)
        verify(repository).getRecentPushes("try", 50, 60)
    }

    @Test
    fun `empty job results end pagination and show an expiry warning`() = runTest {
        val repository = mock<TreeherderRepository>()
        val installCoordinator = mock<ApkInstallCoordinator>()
        whenever(installCoordinator.states).thenReturn(MutableStateFlow(emptyMap()))
        whenever(installCoordinator.successfulInstalls).thenReturn(MutableSharedFlow())
        whenever(repository.getRecentPushes("try", 20, 0))
            .thenReturn(NetworkResult.Success(recentResponse((1..20).toList())))
        whenever(repository.getRecentPushes("try", 20, 20))
            .thenReturn(NetworkResult.Success(recentResponse((21..40).toList())))
        whenever(repository.getJobsForPush(1)).thenReturn(
            NetworkResult.Success(TreeherderJobsResponse(listOf(JobDetails("fenix", "build-android-fenix-apk", "Bsign", "available-task")))),
        )
        whenever(repository.getArtifactsForTask("available-task")).thenReturn(
            NetworkResult.Success(ArtifactsResponse(listOf(Artifact("s3", "public/build/target.arm64-v8a.apk", "2027-01-01T00:00:00Z", "application/vnd.android.package-archive")))),
        )
        whenever(repository.getJobsForPush(21)).thenReturn(NetworkResult.Success(TreeherderJobsResponse(emptyList())))
        for (id in 2..20) {
            whenever(repository.getJobsForPush(id)).thenReturn(NetworkResult.Success(TreeherderJobsResponse(emptyList())))
        }
        for (id in 22..40) {
            whenever(repository.getJobsForPush(id)).thenReturn(NetworkResult.Success(TreeherderJobsResponse(emptyList())))
        }
        val viewModel = SearchViewModel(
            fenixRepository = repository,
            userDataRepository = FakeUserDataRepository(),
            cacheManager = FakeCacheManager(cacheDir),
            historyRepository = FakeHistoryRepository(),
            downloadCoordinator = FakeDownloadCoordinator(),
            installCoordinator = installCoordinator,
            authorEmail = "",
        )

        viewModel.submitSearch()
        advanceUntilIdle()
        assertTrue(viewModel.canLoadMore.value)

        viewModel.loadMorePushes()
        advanceUntilIdle()

        assertEquals(listOf("revision-1"), viewModel.pushes.value.map { it.revision })
        assertFalse(viewModel.canLoadMore.value)
        assertEquals("Older pushes' jobs have expired.", viewModel.warningMessage.value)
    }

    private fun revisionResponse() = TreeherderRevisionResponse(
        meta = RevisionMeta(revision = "abcdef123456", count = 1, repository = "mozilla-central"),
        results = listOf(
            RevisionResult(
                id = 1,
                revision = "abcdef123456",
                author = "developer@example.com",
                revisions = listOf(
                    RevisionDetail(1, 1, "abcdef123456", "developer@example.com", "Fix Fenix Debug"),
                ),
                revisionCount = 1,
                pushTimestamp = 1,
                repositoryId = 1,
            ),
        ),
    )

    private fun authorResponse(ids: List<Int>) = TreeherderRevisionResponse(
        meta = RevisionMeta(revision = null, count = ids.size, repository = "try"),
        results = ids.map { id ->
            RevisionResult(
                id = id,
                revision = "revision-$id",
                author = "developer@example.com",
                revisions = listOf(
                    RevisionDetail(id, 1, "revision-$id", "developer@example.com", "Push $id"),
                ),
                revisionCount = 1,
                pushTimestamp = id.toLong(),
                repositoryId = 1,
            )
        },
    )

    private fun recentResponse(ids: List<Int>) = TreeherderRevisionResponse(
        meta = RevisionMeta(revision = null, count = ids.size, repository = "try"),
        results = ids.map { id ->
            RevisionResult(
                id = id,
                revision = "revision-$id",
                author = "developer@example.com",
                revisions = listOf(
                    RevisionDetail(id, 1, "revision-$id", "developer@example.com", "Push $id"),
                ),
                revisionCount = 1,
                pushTimestamp = id.toLong(),
                repositoryId = 1,
            )
        },
    )

    private fun noApkJobs() = TreeherderJobsResponse(
        listOf(JobDetails("fenix", "build-android-fenix", "B", "no-apk-task")),
    )

    private class FakeDownloadCoordinator : ApkDownloadCoordinator {
        override val downloads = MutableStateFlow<Map<String, PersistedDownloadState>>(emptyMap())

        override fun enqueue(request: org.mozilla.tryfox.download.ApkDownloadRequest) = "work-id"

        override fun retry(request: org.mozilla.tryfox.download.ApkDownloadRequest) = "work-id"

        override fun cancel(uniqueKey: String) = Unit

        override fun observe(uniqueKey: String) = emptyFlow<org.mozilla.tryfox.download.model.PersistedDownloadState?>()
    }
}
