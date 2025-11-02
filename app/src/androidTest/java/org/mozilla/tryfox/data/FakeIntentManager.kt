package org.mozilla.tryfox.data

import org.mozilla.tryfox.data.managers.IntentManager
import java.io.File

/**
 * A fake implementation of [IntentManager] for use in instrumented tests.
 * This class allows for verifying that the `installApk` method is called with the correct file.
 */
class FakeIntentManager() : IntentManager {

    /**
     * A boolean flag to indicate whether the `installApk` method was called.
     */
    val wasInstallApkCalled: Boolean
        get() = installedFile != null

    /**
     * The file that was passed to the `installApk` method.
     */
    var installedFile: File? = null
        private set

    /**
     * Overrides the `installApk` method to capture the file and set the `wasInstallApkCalled` flag.
     *
     * @param file The file to be "installed".
     */
    override fun installApk(file: File) {
        installedFile = file
    }
}
