package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.ui.models.JobDetailsUiModel

class ApkJobOrderingTest {

    @Test
    fun `orders regular jobs before firebase and perftest jobs`() {
        val fenixRegular = job(appName = "fenix", jobName = "signing-apk-fenix-nightly")
        val focusRegular = job(appName = "focus", jobName = "signing-apk-focus-nightly")
        val fenixFirebase = job(appName = "fenix", jobName = "signing-apk-fenix-nightly-firebase")
        val focusFirebase = job(appName = "focus", jobName = "signing-apk-focus-nightly-firebase")
        val fenixPerftest = job(appName = "fenix", jobName = "signing-apk-fenix-nightly-simulation")
        val focusPerftest = job(appName = "focus", jobName = "signing-apk-focus-nightly-perftest")

        assertEquals(
            listOf(fenixRegular, focusRegular, fenixFirebase, focusFirebase, fenixPerftest, focusPerftest),
            orderApkJobs(
                listOf(
                    focusPerftest,
                    focusFirebase,
                    fenixRegular,
                    focusRegular,
                    fenixPerftest,
                    fenixFirebase,
                ),
            ),
        )
    }

    @Test
    fun `orders jobs alphabetically by app then job name within each variant group`() {
        val fenixBeta = job(appName = "Fenix", jobName = "signing-apk-fenix-beta")
        val fenixNightly = job(appName = "fenix", jobName = "signing-apk-fenix-nightly")
        val focusNightly = job(appName = "focus", jobName = "signing-apk-focus-nightly")

        assertEquals(
            listOf(fenixBeta, fenixNightly, focusNightly),
            orderApkJobs(listOf(focusNightly, fenixNightly, fenixBeta)),
        )
    }

    @Test
    fun `treats a firebase perftest as a perftest`() {
        val firebase = job(appName = "fenix", jobName = "signing-apk-fenix-nightly-firebase")
        val firebasePerftest = job(appName = "fenix", jobName = "signing-apk-fenix-nightly-firebase-perftest")

        assertEquals(
            listOf(firebase, firebasePerftest),
            orderApkJobs(listOf(firebasePerftest, firebase)),
        )
    }

    private fun job(appName: String, jobName: String) = JobDetailsUiModel(
        appName = appName,
        jobName = jobName,
        jobSymbol = "B",
        taskId = jobName,
        isSignedBuild = true,
        isTest = false,
    )
}
