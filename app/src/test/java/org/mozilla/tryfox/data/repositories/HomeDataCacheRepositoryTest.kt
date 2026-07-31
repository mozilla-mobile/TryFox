package org.mozilla.tryfox.data.repositories

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

class HomeDataCacheRepositoryTest {
    @TempDir
    lateinit var filesDir: File

    private fun repository() = DefaultHomeDataCacheRepository(
        context = mock<Context> { on { this.filesDir } doReturn filesDir },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun snapshot(version: String = "1.0") = HomeDataSnapshot(
        version = HomeDataSnapshot.CURRENT_VERSION,
        apps = listOf(
            CachedHomeApp(
                appName = "fenix",
                apks = listOf(
                    CachedHomeApk(
                        originalString = "build",
                        rawDateString = "2026-07-31-10-00-00",
                        appName = "fenix",
                        version = version,
                        abiName = "arm64-v8a",
                        fullUrl = "https://example.test/fenix.apk",
                        fileName = "fenix.apk",
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `read returns null when no snapshot exists`() = runTest {
        assertNull(repository().read())
    }

    @Test
    fun `write then read round trips a snapshot`() = runTest {
        val repository = repository()
        val snapshot = snapshot()

        repository.write(snapshot)

        assertEquals(snapshot, repository.read())
    }

    @Test
    fun `write replaces the prior snapshot`() = runTest {
        val repository = repository()
        repository.write(snapshot("1.0"))
        val replacement = snapshot("2.0")

        repository.write(replacement)

        assertEquals(replacement, repository.read())
    }

    @Test
    fun `read ignores corrupt snapshots`() = runTest {
        File(filesDir, "home-data-cache-v1.json").writeText("not json")

        assertNull(repository().read())
    }

    @Test
    fun `read ignores snapshots without a schema version`() = runTest {
        File(filesDir, "home-data-cache-v1.json").writeText("{\"apps\":[]}")

        assertNull(repository().read())
    }
}
