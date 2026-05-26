package org.mozilla.tryfox.data.managers

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
}
