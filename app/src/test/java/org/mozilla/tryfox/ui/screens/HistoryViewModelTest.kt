package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.FakeDownloadFileRepository
import org.mozilla.tryfox.data.FakeHistoryRepository
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry
import org.mozilla.tryfox.data.managers.FakeCacheManager
import org.mozilla.tryfox.data.managers.FakeIntentManager
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @JvmField
    @RegisterExtension
    val mainCoroutineRule = MainCoroutineRule()

    @TempDir
    lateinit var tempCacheDir: File

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
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) },
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()

        val downloadedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        assertTrue(downloadedFile.exists())
        assertTrue(downloadedFile.readText().contains(entry.downloadUrl))
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
    }

    @Test
    fun `download retries when rendered item is not downloaded but remembered downloaded file is missing`() = runTest {
        val entry = historyEntry(downloadUrl = "https://example.com/artifact.apk")
        val cacheManager = FakeCacheManager(tempCacheDir)
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) },
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()
        val downloadedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        assertTrue(downloadedFile.delete())
        viewModel.refreshCachedDownloadStates()
        advanceUntilIdle()
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.NotDownloaded)

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()

        assertTrue(downloadedFile.exists())
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
    }

    @Test
    fun `in progress download state is kept even if output file already exists`() = runTest {
        val entry = historyEntry()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val blockingDownloadRepository = BlockingDownloadFileRepository()
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) },
            downloadFileRepository = blockingDownloadRepository,
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()

        val cachedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        assertTrue(cachedFile.exists())
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.InProgress)

        blockingDownloadRepository.complete()
        advanceUntilIdle()

        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
    }

    @Test
    fun `install records a fresh installer launch timestamp before launching installer`() = runTest {
        val entry = historyEntry(lastInstallerLaunchTimestamp = 1L)
        val historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) }
        val intentManager = FakeIntentManager()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val cachedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("cached apk")
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = historyRepository,
            intentManager = intentManager,
            currentTimeMillisProvider = { 123L },
        )
        advanceUntilIdle()

        viewModel.install(viewModel.historyItems.value.single(), cachedFile)
        advanceUntilIdle()

        assertEquals(123L, historyRepository.recordedEntries.single().lastInstallerLaunchTimestamp)
        assertTrue(intentManager.wasInstallApkCalled)
    }

    @Test
    fun `install still launches installer when history recording fails`() = runTest {
        val entry = historyEntry()
        val historyRepository = FakeHistoryRepository().apply {
            setEntries(listOf(entry))
            failRecordInstallerLaunch = true
        }
        val intentManager = FakeIntentManager()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val cachedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("cached apk")
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = historyRepository,
            intentManager = intentManager,
        )
        advanceUntilIdle()

        viewModel.install(viewModel.historyItems.value.single(), cachedFile)
        advanceUntilIdle()

        assertTrue(intentManager.wasInstallApkCalled)
    }

    private fun createViewModel(
        cacheManager: FakeCacheManager,
        historyRepository: FakeHistoryRepository,
        downloadFileRepository: org.mozilla.tryfox.data.repositories.DownloadFileRepository =
            FakeDownloadFileRepository(downloadProgressDelayMillis = 0),
        intentManager: FakeIntentManager = FakeIntentManager(),
        currentTimeMillisProvider: () -> Long = { 0L },
    ): HistoryViewModel =
        HistoryViewModel(
            historyRepository = historyRepository,
            downloadFileRepository = downloadFileRepository,
            cacheManager = cacheManager,
            intentManager = intentManager,
            ioDispatcher = mainCoroutineRule.testDispatcher,
            currentTimeMillisProvider = currentTimeMillisProvider,
        )

    private fun historyEntry(
        downloadUrl: String = "https://example.com/task/artifact",
        lastInstallerLaunchTimestamp: Long = 10L,
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
            lastInstallerLaunchTimestamp = lastInstallerLaunchTimestamp,
        )

    private class BlockingDownloadFileRepository : org.mozilla.tryfox.data.repositories.DownloadFileRepository {
        private val completion = kotlinx.coroutines.CompletableDeferred<Unit>()

        override suspend fun downloadFile(
            downloadUrl: String,
            outputFile: File,
            onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        ): org.mozilla.tryfox.data.NetworkResult<File> {
            outputFile.parentFile?.mkdirs()
            outputFile.writeText("partial")
            onProgress(1L, 10L)
            completion.await()
            outputFile.writeText("complete")
            onProgress(10L, 10L)
            return org.mozilla.tryfox.data.NetworkResult.Success(outputFile)
        }

        fun complete() {
            completion.complete(Unit)
        }
    }
}
