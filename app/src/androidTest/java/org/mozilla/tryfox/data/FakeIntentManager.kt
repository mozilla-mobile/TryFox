package org.mozilla.tryfox.data

import org.mozilla.tryfox.data.managers.IntentManager
/**
 * A fake implementation of [IntentManager] for use in instrumented tests.
 */
class FakeIntentManager() : IntentManager {

    var wasUninstallApkCalled: Boolean = false
        private set

    override fun uninstallApk(packageName: String) {
        wasUninstallApkCalled = true
    }
}
