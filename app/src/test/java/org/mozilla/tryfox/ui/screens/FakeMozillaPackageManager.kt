package org.mozilla.tryfox.ui.screens

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.util.FENIX_BETA_PACKAGE
import org.mozilla.tryfox.util.FENIX_NIGHTLY_PACKAGE
import org.mozilla.tryfox.util.FENIX_RELEASE_PACKAGE
import org.mozilla.tryfox.util.FOCUS_NIGHTLY_PACKAGE
import org.mozilla.tryfox.util.REFERENCE_BROWSER_PACKAGE
import org.mozilla.tryfox.util.TRYFOX_PACKAGE

class FakeMozillaPackageManager(
    private val apps: Map<String, AppState> = emptyMap(),
) : MozillaPackageManager {

    override val fenix: AppState
        get() = apps[FENIX_NIGHTLY_PACKAGE] ?: AppState("Fenix", FENIX_NIGHTLY_PACKAGE, null, null)

    override val fenixRelease: AppState
        get() = apps[FENIX_RELEASE_PACKAGE] ?: AppState("Firefox", FENIX_RELEASE_PACKAGE, null, null)

    override val fenixBeta: AppState
        get() = apps[FENIX_BETA_PACKAGE] ?: AppState("Firefox Beta", FENIX_BETA_PACKAGE, null, null)

    override val focus: AppState
        get() = apps[FOCUS_NIGHTLY_PACKAGE] ?: AppState("Focus", FOCUS_NIGHTLY_PACKAGE, null, null)

    override val referenceBrowser: AppState
        get() = apps[REFERENCE_BROWSER_PACKAGE] ?: AppState("Reference Browser", REFERENCE_BROWSER_PACKAGE, null, null)

    override val tryfox: AppState
        get() = apps[TRYFOX_PACKAGE] ?: AppState("TryFox", TRYFOX_PACKAGE, null, null)

    override val appStates: Flow<AppState> = emptyFlow()

    override fun launchApp(appName: String) {
        // No-op
    }
}
