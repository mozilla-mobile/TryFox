package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
            failUpsertHistoryEntry = true
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
        val downloadFileRepository = CancellationIgnoringFailingDownloadFileRepository()
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = historyRepository,
            downloadFileRepository = downloadFileRepository,
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.InProgress)
        val downloadedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        val partialFile = File(downloadedFile.parentFile, "${downloadedFile.name}.part")
        val managedBackupFile = File(downloadedFile.parentFile, "${downloadedFile.name}.bak.1")
        val unmanagedBackupLikeFile = File(downloadedFile.parentFile, "${downloadedFile.name}.bak.tmp")
        managedBackupFile.writeText("backup")
        unmanagedBackupLikeFile.writeText("not managed by downloader")

        viewModel.delete(viewModel.historyItems.value.single())
        advanceUntilIdle()

        assertTrue(downloadFileRepository.wasCanceled)
        assertEquals(emptyList<TreeherderInstallHistoryEntry>(), historyRepository.recordedEntries)
        assertTrue(viewModel.historyItems.value.isEmpty())
        assertFalse(downloadedFile.exists())
        assertFalse(partialFile.exists())
        assertFalse(managedBackupFile.exists())
        assertTrue(unmanagedBackupLikeFile.exists())

        historyRepository.setEntries(listOf(entry))
        advanceUntilIdle()

        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.NotDownloaded)
    }

    @Test
    fun `same key download waits until canceled download finishes`() = runTest {
        val entry = historyEntry()
        val cacheManager = FakeCacheManager(tempCacheDir)
        val historyRepository = FakeHistoryRepository().apply { setEntries(listOf(entry)) }
        val downloadFileRepository = DelayedCanceledThenBlockingDownloadFileRepository()
        val viewModel = createViewModel(
            cacheManager = cacheManager,
            historyRepository = historyRepository,
            downloadFileRepository = downloadFileRepository,
        )
        advanceUntilIdle()

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()
        viewModel.delete(viewModel.historyItems.value.single())
        advanceUntilIdle()

        historyRepository.setEntries(listOf(entry))
        advanceUntilIdle()
        assertTrue(
            (viewModel.historyItems.value.single().downloadState as DownloadState.InProgress)
                .isIndeterminate,
        )

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()

        val downloadedFile = File(cacheManager.getCacheDir("treeherder"), "${entry.taskId}/${entry.artifactFileName}")
        val partialFile = File(downloadedFile.parentFile, "${downloadedFile.name}.part")
        assertEquals(1, downloadFileRepository.startedDownloads)
        assertFalse(partialFile.exists())

        downloadFileRepository.completeCanceledDownload()
        advanceUntilIdle()
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.NotDownloaded)

        viewModel.download(viewModel.historyItems.value.single())
        advanceUntilIdle()

        assertTrue(partialFile.exists())
        assertEquals(2, downloadFileRepository.startedDownloads)
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.InProgress)

        downloadFileRepository.completeSecondDownload()
        advanceUntilIdle()

        assertTrue(downloadedFile.exists())
        assertTrue(viewModel.historyItems.value.single().downloadState is DownloadState.Downloaded)
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

    private class CancellationIgnoringFailingDownloadFileRepository : org.mozilla.tryfox.data.repositories.DownloadFileRepository {
        var wasCanceled = false
            private set

        override suspend fun downloadFile(
            downloadUrl: String,
            outputFile: File,
            onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        ): org.mozilla.tryfox.data.NetworkResult<File> {
            outputFile.parentFile?.mkdirs()
            outputFile.writeText("partial")
            File(outputFile.parentFile, "${outputFile.name}.part").writeText("partial")
            onProgress(1L, 10L)

            try {
                kotlinx.coroutines.awaitCancellation()
            } catch (_: kotlinx.coroutines.CancellationException) {
                wasCanceled = true
            }

            outputFile.writeText("late complete")
            File(outputFile.parentFile, "${outputFile.name}.part").writeText("late partial")
            onProgress(10L, 10L)
            return org.mozilla.tryfox.data.NetworkResult.Error("late failure after cancellation", null)
        }
    }

    private class DelayedCanceledThenBlockingDownloadFileRepository : org.mozilla.tryfox.data.repositories.DownloadFileRepository {
        private val canceledDownloadCanComplete = kotlinx.coroutines.CompletableDeferred<Unit>()
        private val secondDownloadCanComplete = kotlinx.coroutines.CompletableDeferred<Unit>()

        var startedDownloads = 0
            private set

        override suspend fun downloadFile(
            downloadUrl: String,
            outputFile: File,
            onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        ): org.mozilla.tryfox.data.NetworkResult<File> {
            startedDownloads += 1
            outputFile.parentFile?.mkdirs()
            val partialFile = File(outputFile.parentFile, "${outputFile.name}.part")

            return if (startedDownloads == 1) {
                partialFile.writeText("first partial")
                onProgress(1L, 10L)
                try {
                    kotlinx.coroutines.awaitCancellation()
                } catch (_: kotlinx.coroutines.CancellationException) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        canceledDownloadCanComplete.await()
                    }
                }
                org.mozilla.tryfox.data.NetworkResult.Error("first download canceled", null)
            } else {
                partialFile.writeText("second partial")
                onProgress(1L, 10L)
                secondDownloadCanComplete.await()
                partialFile.delete()
                outputFile.writeText("second complete")
                onProgress(10L, 10L)
                org.mozilla.tryfox.data.NetworkResult.Success(outputFile)
            }
        }

        fun completeCanceledDownload() {
            canceledDownloadCanComplete.complete(Unit)
        }

        fun completeSecondDownload() {
            secondDownloadCanComplete.complete(Unit)
        }
    }
}
