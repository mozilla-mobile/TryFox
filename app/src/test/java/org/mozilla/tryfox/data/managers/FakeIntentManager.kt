package org.mozilla.tryfox.data.managers

/**
 * A fake implementation of [IntentManager] for use in unit tests.
 */
class FakeIntentManager() : IntentManager {
    var wasUninstallApkCalled: Boolean = false
        private set

    override fun uninstallApk(packageName: String) {
        wasUninstallApkCalled = true
    }
}
