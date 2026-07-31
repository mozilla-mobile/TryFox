package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.ui.models.JobDetailsUiModel

class UnsignedApkFilterTest {

    @Test
    fun `hides an unsigned build when its signed equivalent is available`() {
        val signed = job("signing-apk-fenix-nightly", signed = true)
        val unsigned = job("build-apk-fenix-nightly", signed = false)

        assertEquals(listOf(signed), filterRedundantUnsignedApkJobs(listOf(signed, unsigned)))
    }

    @Test
    fun `keeps an unsigned build when its signed equivalent is unavailable`() {
        val unsigned = job("build-apk-fenix-nightly", signed = false)

        assertEquals(listOf(unsigned), filterRedundantUnsignedApkJobs(listOf(unsigned)))
    }

    @Test
    fun `keeps builds with a different signing variant`() {
        val signedBeta = job("signing-apk-fenix-beta", signed = true)
        val unsignedNightly = job("build-apk-fenix-nightly", signed = false)

        assertEquals(
            listOf(signedBeta, unsignedNightly),
            filterRedundantUnsignedApkJobs(listOf(signedBeta, unsignedNightly)),
        )
    }

    @Test
    fun `does not treat an unsigned signing-named job as an available signed equivalent`() {
        val signingNamedButUnsigned = job("signing-apk-fenix-nightly", signed = false)
        val unsigned = job("build-apk-fenix-nightly", signed = false)

        assertEquals(
            listOf(signingNamedButUnsigned, unsigned),
            filterRedundantUnsignedApkJobs(listOf(signingNamedButUnsigned, unsigned)),
        )
    }

    @Test
    fun `matches job names ignoring case and surrounding whitespace`() {
        val signed = job(" signing-apk-fenix-nightly ", signed = true)
        val unsigned = job("BUILD-APK-FENIX-NIGHTLY", signed = false)

        assertEquals(listOf(signed), filterRedundantUnsignedApkJobs(listOf(signed, unsigned)))
    }

    private fun job(jobName: String, signed: Boolean) = JobDetailsUiModel(
        appName = "fenix",
        jobName = jobName,
        jobSymbol = "B",
        taskId = jobName,
        isSignedBuild = signed,
        isTest = false,
    )
}
