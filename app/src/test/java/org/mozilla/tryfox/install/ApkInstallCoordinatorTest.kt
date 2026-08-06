package org.mozilla.tryfox.install

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mozilla.tryfox.data.InstalledTryBuild
import org.mozilla.tryfox.data.repositories.InstalledTryBuildRepository
import org.mozilla.tryfox.util.FENIX_DEBUG_PACKAGE
import java.io.ByteArrayOutputStream
import java.io.File

class ApkInstallCoordinatorTest {

    @Test
    fun `session factory creates and commits a PackageInstaller session owned by TryFox`() {
        val packageInstaller = mock<PackageInstaller>()
        val session = mock<PackageInstaller.Session>()
        val statusReceiver = mock<IntentSender>()
        val apk = File.createTempFile("tryfox-install", ".apk").apply { writeText("apk") }
        val apkSize = apk.length()
        whenever(packageInstaller.createSession(any())).thenReturn(42)
        whenever(packageInstaller.openSession(42)).thenReturn(session)
        whenever(session.openWrite(any(), any(), any())).thenReturn(ByteArrayOutputStream())

        val paramsConstruction = mockConstruction(PackageInstaller.SessionParams::class.java) { _, context ->
            assertEquals(PackageInstaller.SessionParams.MODE_FULL_INSTALL, context.arguments().single())
        }
        try {
            PackageInstallerSessionFactory(packageInstaller).commit(apk, "org.mozilla.fenix", statusReceiver)

            val params = paramsConstruction.constructed().single()
            verify(params).setAppPackageName("org.mozilla.fenix")
            verify(params).setSize(apkSize)
        } finally {
            paramsConstruction.close()
            apk.delete()
        }

        verify(packageInstaller).createSession(any())
        verify(session).openWrite("base.apk", 0L, apkSize)
        verify(session).fsync(any())
        verify(session).commit(statusReceiver)
    }

    @Test
    fun `successful result after process recreation persists Try build provenance`() = runTest {
        val packageInstaller = mock<PackageInstaller>()
        val packageManager = mock<PackageManager>()
        val context = mock<Context>()
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(packageManager.packageInstaller).thenReturn(packageInstaller)
        val repository = RecordingInstalledTryBuildRepository()
        val coordinator = ApkInstallCoordinator(context, repository)
        val resultIntent = mock<Intent>()
        val extras = mapOf(
            "org.mozilla.tryfox.install.ARTIFACT_KEY" to "artifact-key",
            "org.mozilla.tryfox.install.PACKAGE_NAME" to FENIX_DEBUG_PACKAGE,
            "org.mozilla.tryfox.install.VERSION_NAME" to "128.0a1",
            "org.mozilla.tryfox.install.PROJECT" to "mobile",
            "org.mozilla.tryfox.install.REVISION" to "abc123",
            "org.mozilla.tryfox.install.COMMIT_MESSAGE" to "Fix the Fenix Debug build",
        )
        whenever(resultIntent.getStringExtra(any())).thenAnswer { invocation ->
            extras[invocation.getArgument(0)]
        }
        whenever(resultIntent.getLongExtra(any(), any())).thenAnswer { invocation ->
            if (invocation.getArgument<String>(0) == "org.mozilla.tryfox.install.VERSION_CODE") 123L
            else invocation.getArgument(1)
        }
        whenever(resultIntent.getIntExtra(any(), any())).thenAnswer { invocation ->
            if (invocation.getArgument<String>(0) == PackageInstaller.EXTRA_STATUS) PackageInstaller.STATUS_SUCCESS
            else invocation.getArgument(1)
        }

        coordinator.onInstallResult(resultIntent)

        assertEquals(
            InstalledTryBuild(
                packageName = FENIX_DEBUG_PACKAGE,
                project = "mobile",
                revision = "abc123",
                commitMessage = "Fix the Fenix Debug build",
                versionName = "128.0a1",
                versionCode = 123L,
            ),
            repository.savedBuild,
        )
    }

    private class RecordingInstalledTryBuildRepository : InstalledTryBuildRepository {
        override val installedTryBuild: Flow<InstalledTryBuild?> = flowOf(null)
        var savedBuild: InstalledTryBuild? = null

        override suspend fun save(build: InstalledTryBuild) {
            savedBuild = build
        }
    }
}
