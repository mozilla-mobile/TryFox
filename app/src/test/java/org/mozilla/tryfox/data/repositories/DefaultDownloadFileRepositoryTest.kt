package org.mozilla.tryfox.data.repositories

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.network.DownloadApiService
import java.io.File

class DefaultDownloadFileRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `successful download moves complete partial file into final path`() = runTest {
        val outputFile = File(tempDir, "target.apk")
        val repository = DefaultDownloadFileRepository(
            downloadApiService = FakeDownloadApiService("complete apk".toResponseBody()),
        )

        val result = repository.downloadFile(
            downloadUrl = "https://example.com/target.apk",
            outputFile = outputFile,
            onProgress = { _, _ -> },
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals("complete apk", outputFile.readText())
        assertFalse(File(tempDir, "target.apk.part").exists())
    }

    @Test
    fun `incomplete download removes partial file and does not create final path`() = runTest {
        val outputFile = File(tempDir, "target.apk")
        val repository = DefaultDownloadFileRepository(
            downloadApiService = FakeDownloadApiService(
                IncompleteResponseBody(
                    declaredLength = 10L,
                    bytes = "short".toByteArray(),
                ),
            ),
        )

        val result = repository.downloadFile(
            downloadUrl = "https://example.com/target.apk",
            outputFile = outputFile,
            onProgress = { _, _ -> },
        )

        assertTrue(result is NetworkResult.Error)
        assertFalse(outputFile.exists())
        assertFalse(File(tempDir, "target.apk.part").exists())
    }

    @Test
    fun `successful download replaces existing final file and removes backup`() = runTest {
        val outputFile = File(tempDir, "target.apk")
        outputFile.writeText("old apk")
        val repository = DefaultDownloadFileRepository(
            downloadApiService = FakeDownloadApiService("new apk".toResponseBody()),
        )

        val result = repository.downloadFile(
            downloadUrl = "https://example.com/target.apk",
            outputFile = outputFile,
            onProgress = { _, _ -> },
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals("new apk", outputFile.readText())
        assertFalse(File(tempDir, "target.apk.part").exists())
        assertFalse(File(tempDir, "target.apk.bak").exists())
    }

    @Test
    fun `missing final file is restored from backup before retrying download`() = runTest {
        val outputFile = File(tempDir, "target.apk")
        val backupFile = File(tempDir, "target.apk.bak")
        backupFile.writeText("old apk")
        val repository = DefaultDownloadFileRepository(
            downloadApiService = FakeDownloadApiService(
                IncompleteResponseBody(
                    declaredLength = 10L,
                    bytes = "short".toByteArray(),
                ),
            ),
        )

        val result = repository.downloadFile(
            downloadUrl = "https://example.com/target.apk",
            outputFile = outputFile,
            onProgress = { _, _ -> },
        )

        assertTrue(result is NetworkResult.Error)
        assertEquals("old apk", outputFile.readText())
        assertFalse(File(tempDir, "target.apk.part").exists())
        assertFalse(backupFile.exists())
    }

    @Test
    fun `missing final file is restored from latest backup when alternate backups exist`() = runTest {
        val outputFile = File(tempDir, "target.apk")
        val staleBackupFile = File(tempDir, "target.apk.bak")
        val latestBackupFile = File(tempDir, "target.apk.bak.1")
        staleBackupFile.writeText("stale apk")
        latestBackupFile.writeText("latest apk")
        staleBackupFile.setLastModified(1_000L)
        latestBackupFile.setLastModified(2_000L)
        val repository = DefaultDownloadFileRepository(
            downloadApiService = FakeDownloadApiService(
                IncompleteResponseBody(
                    declaredLength = 10L,
                    bytes = "short".toByteArray(),
                ),
            ),
        )

        val result = repository.downloadFile(
            downloadUrl = "https://example.com/target.apk",
            outputFile = outputFile,
            onProgress = { _, _ -> },
        )

        assertTrue(result is NetworkResult.Error)
        assertEquals("latest apk", outputFile.readText())
        assertFalse(staleBackupFile.exists())
        assertFalse(latestBackupFile.exists())
    }

    @Test
    fun `missing final file prefers numbered backup over newer plain stale backup`() = runTest {
        val outputFile = File(tempDir, "target.apk")
        val staleBackupFile = File(tempDir, "target.apk.bak")
        val latestBackupFile = File(tempDir, "target.apk.bak.1")
        staleBackupFile.writeText("stale apk")
        latestBackupFile.writeText("latest apk")
        staleBackupFile.setLastModified(2_000L)
        latestBackupFile.setLastModified(1_000L)
        val repository = DefaultDownloadFileRepository(
            downloadApiService = FakeDownloadApiService(
                IncompleteResponseBody(
                    declaredLength = 10L,
                    bytes = "short".toByteArray(),
                ),
            ),
        )

        val result = repository.downloadFile(
            downloadUrl = "https://example.com/target.apk",
            outputFile = outputFile,
            onProgress = { _, _ -> },
        )

        assertTrue(result is NetworkResult.Error)
        assertEquals("latest apk", outputFile.readText())
        assertFalse(staleBackupFile.exists())
        assertFalse(latestBackupFile.exists())
    }

    @Test
    fun `missing final file ignores non managed backup suffixes`() = runTest {
        val outputFile = File(tempDir, "target.apk")
        val nonManagedBackupFile = File(tempDir, "target.apk.bak.tmp")
        nonManagedBackupFile.writeText("not managed")
        val repository = DefaultDownloadFileRepository(
            downloadApiService = FakeDownloadApiService(
                IncompleteResponseBody(
                    declaredLength = 10L,
                    bytes = "short".toByteArray(),
                ),
            ),
        )

        val result = repository.downloadFile(
            downloadUrl = "https://example.com/target.apk",
            outputFile = outputFile,
            onProgress = { _, _ -> },
        )

        assertTrue(result is NetworkResult.Error)
        assertFalse(outputFile.exists())
        assertTrue(nonManagedBackupFile.exists())
    }

    @Test
    fun `successful download tolerates stale backup when final file exists`() = runTest {
        val outputFile = File(tempDir, "target.apk")
        val backupFile = File(tempDir, "target.apk.bak")
        outputFile.writeText("current apk")
        backupFile.writeText("stale apk")
        val repository = DefaultDownloadFileRepository(
            downloadApiService = FakeDownloadApiService("new apk".toResponseBody()),
        )

        val result = repository.downloadFile(
            downloadUrl = "https://example.com/target.apk",
            outputFile = outputFile,
            onProgress = { _, _ -> },
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals("new apk", outputFile.readText())
        assertFalse(File(tempDir, "target.apk.part").exists())
        assertFalse(backupFile.exists())
    }

    private class FakeDownloadApiService(
        private val responseBody: ResponseBody,
    ) : DownloadApiService {
        override suspend fun downloadFile(downloadUrl: String): ResponseBody = responseBody
    }

    private class IncompleteResponseBody(
        private val declaredLength: Long,
        private val bytes: ByteArray,
    ) : ResponseBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = declaredLength
        override fun source() = bytes.inputStream().source().buffer()
    }
}
