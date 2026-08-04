package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
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

    private fun app(name: String) = AppUiModel(
        name = name,
        packageName = name,
        installedVersion = null,
        installedDate = null,
        apks = ApksResult.Loading,
    )
}
