package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.model.HomeScreenLayout
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_DEBUG
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.REFERENCE_BROWSER

class HomeAppCardModelTest {
    @Test
    fun `groups all home flavors into three cards with mockup defaults`() {
        val apps = listOf(
            FENIX, FENIX_BETA, FENIX_RELEASE, FOCUS, FOCUS_BETA, FOCUS_RELEASE, REFERENCE_BROWSER,
        ).associateWith(::app)

        val cards = homeAppCards(apps, emptyMap())

        assertEquals(3, cards.size)
        assertEquals(FENIX, cards.first { it.family == HomeAppFamily.Fenix }.selectedAppName)
        assertEquals(FOCUS_BETA, cards.first { it.family == HomeAppFamily.Focus }.selectedAppName)
        assertEquals(REFERENCE_BROWSER, cards.first { it.family == HomeAppFamily.ReferenceBrowser }.selectedAppName)
    }

    @Test
    fun `retains an explicitly selected flavor`() {
        val apps = listOf(FENIX, FENIX_BETA, FENIX_RELEASE).associateWith(::app)

        val card = homeAppCards(apps, mapOf(HomeAppFamily.Fenix to FENIX_RELEASE)).single()

        assertEquals(FENIX_RELEASE, card.selectedAppName)
    }

    @Test
    fun `only shows Debug flavors when Firefox Debug is installed`() {
        val apps = listOf(FENIX, FENIX_BETA, FENIX_RELEASE, FENIX_DEBUG, FOCUS, FOCUS_BETA, FOCUS_RELEASE, FOCUS_DEBUG)
            .associateWith(::app)

        val cardsWithoutDebug = homeAppCards(apps, emptyMap())
        assertEquals(false, FENIX_DEBUG in cardsWithoutDebug.first { it.family == HomeAppFamily.Fenix }.appsByName)
        assertEquals(false, FOCUS_DEBUG in cardsWithoutDebug.first { it.family == HomeAppFamily.Focus }.appsByName)

        val appsWithDebug = apps + (FENIX_DEBUG to app(FENIX_DEBUG, installedVersion = "1.0")) +
            (FOCUS_DEBUG to app(FOCUS_DEBUG, installedVersion = "1.0"))
        val cardsWithDebug = homeAppCards(appsWithDebug, emptyMap())
        assertEquals(true, FENIX_DEBUG in cardsWithDebug.first { it.family == HomeAppFamily.Fenix }.appsByName)
        assertEquals(true, FOCUS_DEBUG in cardsWithDebug.first { it.family == HomeAppFamily.Focus }.appsByName)
    }

    @Test
    fun `creates one standalone card for each available flavor`() {
        val apps = listOf(
            FENIX, FENIX_BETA, FENIX_RELEASE, FENIX_DEBUG,
            FOCUS, FOCUS_BETA, FOCUS_RELEASE, FOCUS_DEBUG,
            REFERENCE_BROWSER,
        ).associateWith(::app) + mapOf(
            FENIX_DEBUG to app(FENIX_DEBUG, installedVersion = "1.0"),
            FOCUS_DEBUG to app(FOCUS_DEBUG, installedVersion = "1.0"),
        )

        val cards = homeAppCards(apps, emptyMap(), HomeScreenLayout.OneCardPerFlavor)

        assertEquals(
            listOf(
                FENIX_RELEASE, FENIX_BETA, FENIX, FENIX_DEBUG,
                FOCUS_RELEASE, FOCUS_BETA, FOCUS, FOCUS_DEBUG,
                REFERENCE_BROWSER,
            ),
            cards.map { it.selectedAppName },
        )
        assertEquals(true, cards.all { !it.showFlavorSelector })
    }

    private fun app(name: String, installedVersion: String? = null) = AppUiModel(
        name = name,
        packageName = name,
        installedVersion = installedVersion,
        installedDate = null,
        apks = ApksResult.Loading,
    )
}
