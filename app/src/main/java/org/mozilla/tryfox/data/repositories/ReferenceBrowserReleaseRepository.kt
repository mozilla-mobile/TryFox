package org.mozilla.tryfox.data.repositories

import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.REFERENCE_BROWSER

/**
 * A [ReleaseRepository] for Reference Browser builds.
 */
class ReferenceBrowserReleaseRepository : ReleaseRepository {

    companion object {
        private const val REFERENCE_BROWSER_TASK_BASE_URL = "https://firefox-ci-tc.services.mozilla.com/api/index/v1/task/mobile.v2.reference-browser.nightly.latest."
        private val REFERENCE_BROWSER_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }

    override val appName: String = REFERENCE_BROWSER

    override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
        return try {
            val parsedApks = REFERENCE_BROWSER_ABIS.map { abi ->
                val fullUrl = "${REFERENCE_BROWSER_TASK_BASE_URL}$abi/artifacts/public/target.$abi.apk"
                val fileName = "target.$abi.apk"
                MozillaArchiveApk(
                    originalString = "reference-browser-latest-android-$abi/",
                    rawDateString = null,
                    appName = "reference-browser",
                    version = "",
                    abiName = abi,
                    fullUrl = fullUrl,
                    fileName = fileName,
                )
            }
            NetworkResult.Success(parsedApks)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to construct Reference Browser builds: ${e.message}", e)
        }
    }
}
