package org.mozilla.tryfox.ui.screens

import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.REFERENCE_BROWSER

/** The three product cards shown on Home. */
enum class HomeAppFamily(val appNames: List<String>, val defaultAppName: String) {
    Fenix(listOf(FENIX_RELEASE, FENIX_BETA, FENIX), FENIX),
    Focus(listOf(FOCUS_RELEASE, FOCUS_BETA, FOCUS), FOCUS_BETA),
    ReferenceBrowser(listOf(REFERENCE_BROWSER), REFERENCE_BROWSER),
}

data class HomeAppCardUiModel(
    val family: HomeAppFamily,
    val selectedAppName: String,
    val appsByName: Map<String, AppUiModel>,
) {
    val selectedApp: AppUiModel get() = appsByName.getValue(selectedAppName)
}

internal fun homeAppCards(
    apps: Map<String, AppUiModel>,
    selectedAppNames: Map<HomeAppFamily, String>,
): List<HomeAppCardUiModel> = HomeAppFamily.entries.mapNotNull { family ->
    val familyApps = family.appNames.mapNotNull { name -> apps[name]?.let { name to it } }.toMap()
    if (familyApps.isEmpty()) return@mapNotNull null
    val selected = selectedAppNames[family].takeIf { it in familyApps } ?: family.defaultAppName
    HomeAppCardUiModel(family, selected, familyApps)
}
