package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_NIGHTLY
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_NIGHTLY

class JobIconNameTest {

    @Test
    fun `selects the requested app icon from the job name`() {
        assertEquals(FOCUS, appIconNameForJob("Focus x86_64 build", "unknown"))
        assertEquals(FENIX, appIconNameForJob("fenix-debug arm64-v8a", "unknown"))
        assertEquals(FENIX_NIGHTLY, appIconNameForJob("fenix-nightly arm64-v8a", "unknown"))
        assertEquals(FENIX_RELEASE, appIconNameForJob("fenix-release arm64-v8a", "unknown"))
        assertEquals(FENIX_BETA, appIconNameForJob("fenix-beta arm64-v8a", "unknown"))
        assertEquals(FOCUS, appIconNameForJob("focus-debug arm64-v8a", "unknown"))
        assertEquals(FOCUS_NIGHTLY, appIconNameForJob("focus-nightly arm64-v8a", "unknown"))
        assertEquals(FOCUS_BETA, appIconNameForJob("focus-beta arm64-v8a", "unknown"))
    }

    @Test
    fun `uses the job app name when no requested marker is present`() {
        assertEquals("fenix", appIconNameForJob("Build Fenix for arm64-v8a", "fenix"))
    }
}
