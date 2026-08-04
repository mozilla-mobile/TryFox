package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.data.JobDetails

class AndroidApkCandidateTest {

    @Test
    fun `includes unsigned Roam APK build jobs`() {
        val job = JobDetails(
            appName = "roam",
            jobName = "build-apk-roam-debug",
            jobSymbol = "B",
            taskId = "roam-task",
        )

        assertTrue(isAndroidApkCandidate(job))
    }
}
