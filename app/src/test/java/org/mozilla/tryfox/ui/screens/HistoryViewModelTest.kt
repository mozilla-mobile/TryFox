package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.FakeHistoryRepository
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry
import org.mozilla.tryfox.data.managers.FakeCacheManager
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.download.ApkDownloadRequest
import org.mozilla.tryfox.download.model.DownloadStatus
import org.mozilla.tryfox.download.model.PersistedDownloadState
import org.mozilla.tryfox.install.ApkInstallCoordinator
import org.mozilla.tryfox.install.TryBuildProvenance
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineRule = MainCoroutineRule()

    @TempDir
    lateinit var tempCacheDir: File

    private class FakeApkDownloadCoordinator : ApkDownloadCoordinator {
        private val _downloads = MutableStateFlow<Map<String, PersistedDownloadState>>(emptyMap())
        val enqueuedRequests = mutableListOf<ApkDownloadRequest>()
        val canceledKeys = mutableSetOf<String>()
        private val currentWorkIds = mutableMapOf<String, String>()
        private val canceledWorkIds = mutableSetOf<String>()
        private var workIdSequence = 0

        override val downloads = _downloads.asStateFlow()

        override fun enqueue(request: ApkDownloadRequest): String {
            enqueuedRequests += request
            val workId = "${request.uniqueKey}#${++workIdSequence}"
            currentWorkIds[request.uniqueKey] = workId
            updateState(
                request.uniqueKey,
                request.toPersistedState(DownloadStatus.QUEUED, workId),
            )
            return workId
        }

        override fun retry(request: ApkDownloadRequest): String = enqueue(request)

        override fun cancel(uniqueKey: String) {
            canceledKeys += uniqueKey
            currentWorkIds[uniqueKey]?.let { canceledWorkIds += it }
            _downloads.value[uniqueKey]?.let { current ->
                updateState(
                    uniqueKey,
                    current.copy(status = DownloadStatus.CANCELED, updatedAt = System.currentTimeMillis()),
                )
            }
        }

        override fun observe(uniqueKey: String) = downloads.map { it[uniqueKey] }

        fun emit(state: PersistedDownloadState) {
            val currentWorkId = currentWorkIds[state.uniqueKey] ?: return
            if (state.workId != currentWorkId || state.workId in canceledWorkIds) {
                return
            }
            updateState(state.uniqueKey, state)
        }

        private fun updateState(uniqueKey: String, state: PersistedDownloadState) {
            _downloads.value = _downloads.value + (uniqueKey to state)
        }

        private fun ApkDownloadRequest.toPersistedState(
            status: DownloadStatus,
            workId: String? = null,
        ): PersistedDownloadState =
            PersistedDownloadState(
                uniqueKey = uniqueKey,
                downloadUrl = downloadUrl,
                outputPath = outputPath,
                appName = appName,
                fileName = fileName,
                cacheRelativePath = cacheRelativePath,
                status = status,
                workId = workId,
            )
    }

    @Test
    fun `history item uses downloaded state when apk exists in cache`() = runTest {
        val entry = historyEntry()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val cachedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("cached apk")

        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) },
        )
        advanceUntilIdle()

        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
    }

    @Test
    fun `refreshing cache state marks history item downloaded when apk was downloaded elsewhere`() = runTest {
        val entry = historyEntry()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) },
        )
        advanceUntilIdle()
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.NotDownloaded)

        val cachedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("cached apk")
        viewModel.refreshCachedDownloadStates()
        advanceUntilIdle()

        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
    }

    @Test
    fun `download uses stored url and writes apk to treeherder cache`() = runTest {
        val entry = historyEntry(downloadUrl = "https://example.com/artifact.apk")
        val cacheManager = FakeCacheManager(tempCacheDir)
        val downloadCoordinator = FakeApkDownloadCoordinator()
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) },
            downloadCoordinator = downloadCoordinator,
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()

        assertEquals(1, downloadCoordinator.enqueuedRequests.size)
        val enqueuedRequest = downloadCoordinator.enqueuedRequests.single()
        val workId = downloadCoordinator.downloads.value[entry.uniqueKey]?.workId
        assertEquals(entry.downloadUrl, enqueuedRequest.downloadUrl)
        assertEquals("Fenix nightly", enqueuedRequest.notificationTitle)
        assertNotNull(workId)

        val downloadedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        downloadedFile.parentFile?.mkdirs()
        downloadedFile.writeText("downloaded apk from ${entry.downloadUrl}")
        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputPath = downloadedFile.absolutePath,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                cacheRelativePath = entry.cacheRelativePath,
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = downloadedFile.length(),
                totalBytes = downloadedFile.length(),
                workId = workId,
            ),
        )
        advanceUntilIdle()

        assertTrue(downloadedFile.exists())
        assertTrue(downloadedFile.readText().contains(entry.downloadUrl))
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
    }

    @Test
    fun `download retries when rendered item is not downloaded but remembered downloaded file is missing`() = runTest {
        val entry = historyEntry(downloadUrl = "https://example.com/artifact.apk")
        val cacheManager = FakeCacheManager(tempCacheDir)
        val downloadCoordinator = FakeApkDownloadCoordinator()
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) },
            downloadCoordinator = downloadCoordinator,
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()
        assertEquals(1, downloadCoordinator.enqueuedRequests.size)
        val downloadedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        val firstWorkId = downloadCoordinator.downloads.value[entry.uniqueKey]?.workId
        assertNotNull(firstWorkId)
        downloadedFile.parentFile?.mkdirs()
        downloadedFile.writeText("downloaded apk")
        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputPath = downloadedFile.absolutePath,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                cacheRelativePath = entry.cacheRelativePath,
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = downloadedFile.length(),
                totalBytes = downloadedFile.length(),
                workId = firstWorkId,
            ),
        )
        advanceUntilIdle()
        assertTrue(downloadedFile.delete())
        viewModel.refreshCachedDownloadStates()
        advanceUntilIdle()
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.NotDownloaded)

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()

        assertEquals(2, downloadCoordinator.enqueuedRequests.size)
        val secondWorkId = downloadCoordinator.downloads.value[entry.uniqueKey]?.workId
        assertNotNull(secondWorkId)
        downloadedFile.writeText("downloaded apk again")
        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputPath = downloadedFile.absolutePath,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                cacheRelativePath = entry.cacheRelativePath,
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = downloadedFile.length(),
                totalBytes = downloadedFile.length(),
                workId = secondWorkId,
            ),
        )
        advanceUntilIdle()

        assertTrue(downloadedFile.exists())
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
    }

    @Test
    fun `in progress download state is kept even if output file already exists`() = runTest {
        val entry = historyEntry()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val downloadCoordinator = FakeApkDownloadCoordinator()
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) },
            downloadCoordinator = downloadCoordinator,
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()

        assertEquals(1, downloadCoordinator.enqueuedRequests.size)
        val enqueuedRequest = downloadCoordinator.enqueuedRequests.single()
        val workId = downloadCoordinator.downloads.value[entry.uniqueKey]?.workId
        assertNotNull(workId)
        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputPath = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}").absolutePath,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                cacheRelativePath = entry.cacheRelativePath,
                status = DownloadStatus.RUNNING,
                bytesDownloaded = 1L,
                totalBytes = 10L,
                workId = workId,
            ),
        )

        val cachedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("partial")
        assertTrue(cachedFile.exists())
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.InProgress)

        cachedFile.writeText("complete")
        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputPath = cachedFile.absolutePath,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                cacheRelativePath = entry.cacheRelativePath,
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = cachedFile.length(),
                totalBytes = cachedFile.length(),
                workId = workId,
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
    }

    @Test
    fun `install records a fresh installer launch timestamp before launching installer`() = runTest {
        val entry = historyEntry(lastInstallerLaunchTimestamp = 1L)
        val historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) }
        val installCoordinator = installCoordinator()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val cachedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("cached apk")
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = historyRepository,
            installCoordinator = installCoordinator,
            currentTimeMillisProvider = { 123L },
        )
        advanceUntilIdle()

        viewModel.install(viewModel.historyItems.value.single(), cachedFile)
        advanceUntilIdle()

        assertEquals(123L, historyRepository.recordedEntries.single().lastInstallerLaunchTimestamp)
        val provenance = argumentCaptor<TryBuildProvenance>()
        verify(installCoordinator).install(eq(entry.uniqueKey), eq(cachedFile), provenance.capture())
        assertEquals(entry.project, provenance.firstValue.project)
    }

    @Test
    fun `install still launches installer when history recording fails`() = runTest {
        val entry = historyEntry()
        val historyRepository = FakeHistoryRepository().apply {
            setEntries(listOf(entry))
            failUpsertHistoryEntry = true
        }
        val installCoordinator = installCoordinator()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val cachedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("cached apk")
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = historyRepository,
            installCoordinator = installCoordinator,
        )
        advanceUntilIdle()

        viewModel.install(viewModel.historyItems.value.single(), cachedFile)
        advanceUntilIdle()

        verify(installCoordinator).install(eq(entry.uniqueKey), eq(cachedFile), org.mockito.kotlin.any())
    }

    @Test
    fun `delete removes history item from repository and rendered state`() = runTest {
        val firstEntry = historyEntry()
        val secondEntry = historyEntry(downloadUrl = "https://example.com/second.apk")
            .copy(taskId = "second-task", artifactFileName = "second.apk", artifactName = "public/build/second.apk")
        val historyRepository = FakeHistoryRepository().apply {
            setEntries(listOf(firstEntry, secondEntry))
        }
        val viewModel = createViewModel(
            cacheManager = FakeCacheManager(tempCacheDir),
            historyRepository = historyRepository,
        )
        advanceUntilIdle()

        viewModel.delete(viewModel.historyItems.value.first { it.entry.uniqueKey == firstEntry.uniqueKey })
        advanceUntilIdle()

        assertEquals(listOf(secondEntry.uniqueKey), historyRepository.recordedEntries.map { it.uniqueKey })
        assertEquals(listOf(secondEntry.uniqueKey), viewModel.historyItems.value.map { it.entry.uniqueKey })
    }

    @Test
    fun `delete cancels active download removes files and ignores late download callbacks`() = runTest {
        val entry = historyEntry()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) }
        val downloadCoordinator = FakeApkDownloadCoordinator()
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = historyRepository,
            downloadCoordinator = downloadCoordinator,
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()
        assertEquals(1, downloadCoordinator.enqueuedRequests.size)
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.InProgress)
        val downloadedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        val partialFile = File(downloadedFile.parentFile, "${downloadedFile.name}.part")
        val managedBackupFile = File(downloadedFile.parentFile, "${downloadedFile.name}.bak.1")
        val unmanagedBackupLikeFile = File(downloadedFile.parentFile, "${downloadedFile.name}.bak.tmp")
        downloadedFile.parentFile?.mkdirs()
        partialFile.writeText("partial")
        managedBackupFile.writeText("backup")
        unmanagedBackupLikeFile.writeText("not managed by downloader")

        viewModel.delete(viewModel.historyItems.value.single())
        advanceUntilIdle()

        assertTrue(downloadCoordinator.canceledKeys.contains(entry.uniqueKey))
        assertEquals(emptyList<TreeherderInstallHistoryEntry>(), historyRepository.recordedEntries)
        assertTrue(viewModel.historyItems.value.isEmpty())
        assertFalse(downloadedFile.exists())
        assertFalse(partialFile.exists())
        assertFalse(managedBackupFile.exists())
        assertTrue(unmanagedBackupLikeFile.exists())

        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputPath = downloadedFile.absolutePath,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                cacheRelativePath = entry.cacheRelativePath,
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = 10L,
                totalBytes = 10L,
                workId = downloadCoordinator.downloads.value[entry.uniqueKey]?.workId,
            ),
        )
        historyRepository.setEntries(listOf(entry))
        advanceUntilIdle()

        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.NotDownloaded)
    }

    @Test
    fun `same key download waits until canceled download finishes`() = runTest {
        val entry = historyEntry()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) }
        val downloadCoordinator = FakeApkDownloadCoordinator()
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = historyRepository,
            downloadCoordinator = downloadCoordinator,
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()
        assertEquals(1, downloadCoordinator.enqueuedRequests.size)
        val firstRequest = downloadCoordinator.enqueuedRequests.single()
        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputPath = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}").absolutePath,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                cacheRelativePath = entry.cacheRelativePath,
                status = DownloadStatus.RUNNING,
                bytesDownloaded = 1L,
                totalBytes = 10L,
                workId = downloadCoordinator.downloads.value[entry.uniqueKey]?.workId,
            ),
        )
        viewModel.delete(viewModel.historyItems.value.single())
        advanceUntilIdle()

        historyRepository.setEntries(listOf(entry))
        advanceUntilIdle()
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.NotDownloaded)

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()

        val downloadedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        assertEquals(2, downloadCoordinator.enqueuedRequests.size)
        val secondRequest = downloadCoordinator.enqueuedRequests.last()
        val secondWorkId = downloadCoordinator.downloads.value[entry.uniqueKey]?.workId
        assertNotNull(secondWorkId)
        downloadedFile.parentFile?.mkdirs()
        downloadedFile.writeText("second complete")
        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputPath = downloadedFile.absolutePath,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                cacheRelativePath = entry.cacheRelativePath,
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = downloadedFile.length(),
                totalBytes = downloadedFile.length(),
                workId = secondWorkId,
            ),
        )
        advanceUntilIdle()
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)

        downloadCoordinator.emit(
            PersistedDownloadState(
                uniqueKey = entry.uniqueKey,
                downloadUrl = entry.downloadUrl,
                outputPath = downloadedFile.absolutePath,
                appName = entry.appName,
                fileName = entry.artifactFileName,
                cacheRelativePath = entry.cacheRelativePath,
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = downloadedFile.length(),
                totalBytes = downloadedFile.length(),
                workId = secondWorkId,
            ),
        )
        advanceUntilIdle()

        assertTrue(downloadedFile.exists())
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
    }

    private fun createViewModel(
        cacheManager: FakeCacheManager,
        historyRepository: FakeHistoryRepository,
        downloadCoordinator: ApkDownloadCoordinator = FakeApkDownloadCoordinator(),
        installCoordinator: ApkInstallCoordinator = installCoordinator(),
        currentTimeMillisProvider: () -> Long = { 0L },
    ): HistoryViewModel =
        HistoryViewModel(
            historyRepository = historyRepository,
            downloadCoordinator = downloadCoordinator,
            cacheManager = cacheManager,
            installCoordinator = installCoordinator,
            ioDispatcher = mainCoroutineRule.testDispatcher,
            currentTimeMillisProvider = currentTimeMillisProvider,
        )

    private fun installCoordinator(): ApkInstallCoordinator = mock<ApkInstallCoordinator>().also { coordinator ->
        whenever(coordinator.states).thenReturn(MutableStateFlow(emptyMap()))
        whenever(coordinator.successfulInstalls).thenReturn(MutableSharedFlow())
    }

    private fun historyEntry(
        downloadUrl: String = "https://example.com/task/artifact",
        historyRecordedTimestamp: Long = 10L,
        lastInstallerLaunchTimestamp: Long? = 10L,
    ): TreeherderInstallHistoryEntry =
        TreeherderInstallHistoryEntry(
            project = "try",
            revision = "abcdef123456",
            commitMessage = "Bug 123 - Test history",
            author = "author@mozilla.com",
            pushTimestamp = 1_716_460_800L,
            appName = "fenix",
            jobName = "signing-apk-fenix-nightly",
            jobSymbol = "B",
            taskId = "task-id",
            artifactName = "public/build/target.arm64-v8a.apk",
            artifactFileName = "target.arm64-v8a.apk",
            downloadUrl = downloadUrl,
            abiName = "arm64-v8a",
            abiSupported = true,
            expires = "2026-01-01T00:00:00.000Z",
            cacheRelativePath = "treeherder/task-id/target.arm64-v8a.apk",
            historyRecordedTimestamp = historyRecordedTimestamp,
            lastInstallerLaunchTimestamp = lastInstallerLaunchTimestamp,
        )
}
