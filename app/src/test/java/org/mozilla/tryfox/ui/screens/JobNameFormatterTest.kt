package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JobNameFormatterTest {

    @Test
    fun `formats every signing APK app and channel`() {
        val cases = mapOf(
            "signing-apk-fenix-debug" to "Fenix debug",
            "signing-apk-fenix-nightly" to "Fenix nightly",
            "signing-apk-fenix-beta" to "Fenix beta",
            "signing-apk-fenix-release" to "Fenix release",
            "signing-apk-focus-debug" to "Focus debug",
            "signing-apk-focus-nightly" to "Focus nightly",
            "signing-apk-focus-beta" to "Focus beta",
            "signing-apk-focus-release" to "Focus release",
        )

        cases.forEach { (jobName, expectedDisplayName) ->
            assertEquals(expectedDisplayName, formatJobNameForDisplay(jobName))
        }
    }

    @Test
    fun `formats Firebase signing APK jobs`() {
        assertEquals("Fenix nightly (firebase)", formatJobNameForDisplay("signing-apk-fenix-nightly-firebase"))
        assertEquals("Focus beta (firebase)", formatJobNameForDisplay("signing-apk-focus-beta-firebase"))
    }

    @Test
    fun `preserves job names outside the signing APK naming convention`() {
        assertEquals("Build Fenix for arm64-v8a", formatJobNameForDisplay("Build Fenix for arm64-v8a"))
        assertEquals("signing-apk-fenix-esr", formatJobNameForDisplay("signing-apk-fenix-esr"))
        assertEquals("signing-apk-fenix-nightly-firebase-extra", formatJobNameForDisplay("signing-apk-fenix-nightly-firebase-extra"))
    }

    @Test
    fun `formats signing APK job names without case sensitivity`() {
        assertEquals("Focus release", formatJobNameForDisplay("SIGNING-APK-FOCUS-RELEASE"))
    }
}
