package org.mozilla.tryfox.data.managers

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DefaultCacheManagerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `initialization migrates legacy cache directories to managed cache root`() {
        val legacyRoot = File(tempDir, "legacy-cache")
        val managedRoot = File(tempDir, "download-cache")
        val legacyApk = File(legacyRoot, "treeherder/task-id/target.apk")
        val unrelatedCacheFile = File(legacyRoot, "image_cache/cached-image")
        legacyApk.parentFile?.mkdirs()
        legacyApk.writeText("cached apk")
        unrelatedCacheFile.parentFile?.mkdirs()
        unrelatedCacheFile.writeText("unrelated cache")

        DefaultCacheManager(
            cacheDir = managedRoot,
            legacyCacheDir = legacyRoot,
        )

        val migratedApk = File(managedRoot, "treeherder/task-id/target.apk")
        assertTrue(migratedApk.exists())
        assertEquals("cached apk", migratedApk.readText())
        assertFalse(legacyApk.exists())
        assertTrue(unrelatedCacheFile.exists())
        assertFalse(File(managedRoot, "image_cache/cached-image").exists())
    }

    @Test
    fun `cache status reports recursive size and clearing resets it`() = runTest {
        val managedRoot = File(tempDir, "download-cache")
        File(managedRoot, "fenix/nested/first.apk").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(15))
        }
        File(managedRoot, "treeherder/second.apk").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(9))
        }
        val manager = DefaultCacheManager(managedRoot, StandardTestDispatcher(testScheduler))

        manager.checkCacheStatus()

        assertEquals(24L, manager.cacheSizeBytes.value)
        assertEquals(org.mozilla.tryfox.model.CacheManagementState.IdleNonEmpty, manager.cacheState.value)

        manager.clearCache()

        assertEquals(0L, manager.cacheSizeBytes.value)
        assertEquals(org.mozilla.tryfox.model.CacheManagementState.IdleEmpty, manager.cacheState.value)
    }

    @Test
    fun `zero byte cache files are still clearable`() = runTest {
        val managedRoot = File(tempDir, "download-cache")
        File(managedRoot, "fenix/empty.apk").apply {
            parentFile?.mkdirs()
            createNewFile()
        }
        val manager = DefaultCacheManager(managedRoot, StandardTestDispatcher(testScheduler))

        manager.checkCacheStatus()

        assertEquals(0L, manager.cacheSizeBytes.value)
        assertEquals(org.mozilla.tryfox.model.CacheManagementState.IdleNonEmpty, manager.cacheState.value)

        manager.clearCache()

        assertFalse(File(managedRoot, "fenix/empty.apk").exists())
    }
}
