package org.mozilla.tryfox.ui.screens

import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.model.HomeScreenLayout
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_DEBUG
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.REFERENCE_BROWSER

/** The three product cards shown on Home. */
enum class HomeAppFamily(val appNames: List<String>, val defaultAppName: String) {
    Fenix(listOf(FENIX_RELEASE, FENIX_BETA, FENIX, FENIX_DEBUG), FENIX),
    Focus(listOf(FOCUS_RELEASE, FOCUS_BETA, FOCUS, FOCUS_DEBUG), FOCUS_BETA),
    ReferenceBrowser(listOf(REFERENCE_BROWSER), REFERENCE_BROWSER),
}

data class HomeAppCardUiModel(
    val family: HomeAppFamily,
    val selectedAppName: String,
    val appsByName: Map<String, AppUiModel>,
    val showFlavorSelector: Boolean = true,
) {
    val selectedApp: AppUiModel get() = appsByName.getValue(selectedAppName)
    val stableKey: String get() = if (showFlavorSelector) family.name else selectedAppName
}

internal fun homeAppCards(
    apps: Map<String, AppUiModel>,
    selectedAppNames: Map<HomeAppFamily, String>,
    layout: HomeScreenLayout = HomeScreenLayout.OneCardPerApp,
): List<HomeAppCardUiModel> = when (layout) {
    HomeScreenLayout.OneCardPerApp -> groupedHomeAppCards(apps, selectedAppNames)
    HomeScreenLayout.OneCardPerFlavor -> flavorHomeAppCards(apps)
}

private fun groupedHomeAppCards(
    apps: Map<String, AppUiModel>,
    selectedAppNames: Map<HomeAppFamily, String>,
): List<HomeAppCardUiModel> = HomeAppFamily.entries.mapNotNull { family ->
    val familyApps = family.appNames.mapNotNull { name ->
        apps[name]?.takeUnless { name.isDebugFlavor && it.installedVersion == null }?.let { name to it }
    }.toMap()
    if (familyApps.isEmpty()) return@mapNotNull null
    val selected = selectedAppNames[family].takeIf { it in familyApps } ?: family.defaultAppName
    HomeAppCardUiModel(family, selected, familyApps)
}

private fun flavorHomeAppCards(apps: Map<String, AppUiModel>): List<HomeAppCardUiModel> =
    HomeAppFamily.entries.flatMap { family ->
        family.appNames.mapNotNull { appName ->
            apps[appName]
                ?.takeUnless { appName.isDebugFlavor && it.installedVersion == null }
                ?.let { app ->
                    HomeAppCardUiModel(
                        family = family,
                        selectedAppName = appName,
                        appsByName = mapOf(appName to app),
                        showFlavorSelector = false,
                    )
                }
        }
    }

private val String.isDebugFlavor: Boolean
    get() = this == FENIX_DEBUG || this == FOCUS_DEBUG
