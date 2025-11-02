package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.model.AppState

class FakeMozillaPackageManager(
    private val apps: Map<String, AppState> = mapOf(),
) : MozillaPackageManager {

    override val fenix: AppState
        get() = apps["org.mozilla.fenix"] ?: AppState("Fenix", "org.mozilla.fenix", null, null)

    override val focus: AppState
        get() = apps["org.mozilla.focus.nightly"] ?: AppState("Focus", "org.mozilla.focus.nightly", null, null)

    override val referenceBrowser: AppState
        get() = apps["org.mozilla.reference.browser"] ?: AppState("Reference Browser", "org.mozilla.reference.browser", null, null)

    override val tryfox: AppState
        get() = apps["org.mozilla.tryfox"] ?: AppState("TryFox", "org.mozilla.tryfox", null, null)


    override val appStates: Flow<AppState> = emptyFlow()

    override fun launchApp(appName: String) {
        // No-op
    }
}
