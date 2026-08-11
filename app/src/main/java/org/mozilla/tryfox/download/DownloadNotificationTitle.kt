package org.mozilla.tryfox.download

import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_DEBUG
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.REFERENCE_BROWSER

fun homeDownloadNotificationTitle(appName: String, version: String): String =
    "${appNameForNotification(appName)} $version"

private fun appNameForNotification(appName: String): String = when (appName) {
    FENIX -> "Fenix Nightly"
    FENIX_RELEASE -> "Fenix Release"
    FENIX_BETA -> "Fenix Beta"
    FENIX_DEBUG -> "Fenix debug"
    FOCUS -> "Focus Nightly"
    FOCUS_RELEASE -> "Focus Release"
    FOCUS_BETA -> "Focus Beta"
    FOCUS_DEBUG -> "Focus debug"
    REFERENCE_BROWSER -> "Reference Browser"
    else -> appName
}
