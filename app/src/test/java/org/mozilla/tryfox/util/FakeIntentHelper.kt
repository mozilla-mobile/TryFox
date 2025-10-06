package org.mozilla.tryfox.util

class FakeIntentHelper(
    private val onLaunchAppCalled: () -> Unit = { },
) : IntentHelper {
    override fun launchApp(appName: String) {
        onLaunchAppCalled
    }
}
