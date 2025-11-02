package org.mozilla.tryfox.data.managers

import java.io.File

/**
 * A fake implementation of [IntentManager] for use in unit tests.
 * This class allows for verifying that the `installApk` method is called.
 */
class FakeIntentManager() : IntentManager {

    /**
     * A boolean flag to indicate whether the `installApk` method was called.
     */
    var wasInstallApkCalled: Boolean = false
        private set

    var wasUninstallApkCalled: Boolean = false
        private set

    /**
     * Overrides the `installApk` method to set the `wasInstallApkCalled` flag to true.
     *
     * @param file The file to be "installed".
     */
    override fun installApk(file: File) {
        wasInstallApkCalled = true
    }

    override fun uninstallApk(packageName: String) {
        wasUninstallApkCalled = true
    }
}
