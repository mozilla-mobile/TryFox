package org.mozilla.tryfox.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppStateTest {
    @Test
    fun `identifies installed apps from unknown sources as sideloaded`() {
        val sideloadedApp = appState(installer = "com.google.android.packageinstaller")

        assertTrue(sideloadedApp.isSideloaded)
    }

    @Test
    fun `does not classify Play Store or TryFox installs as sideloaded`() {
        assertFalse(appState(AppState.PLAY_STORE_PACKAGE).isSideloaded)
        assertFalse(appState(AppState.TRYFOX_PACKAGE).isSideloaded)
    }

    private fun appState(installer: String?) = AppState(
        name = "Firefox",
        packageName = "org.mozilla.firefox",
        version = "1.0",
        installDateMillis = 0L,
        installingPackageName = installer,
    )
}
