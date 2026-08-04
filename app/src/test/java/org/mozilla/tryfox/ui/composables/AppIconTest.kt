package org.mozilla.tryfox.ui.composables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.R
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_DEBUG
import org.mozilla.tryfox.util.FOCUS_NIGHTLY
import org.mozilla.tryfox.util.FOCUS_RELEASE

class AppIconTest {

    @Test
    fun `uses the Fenix Debug icon for the Fenix Debug flavor`() {
        val (icon, description) = appIconResources(
            appName = FENIX_DEBUG,
            useSearchResultVariant = false,
        )

        assertEquals(R.drawable.ic_fenix_debug_foreground, icon)
        assertEquals(R.string.app_icon_firefox_description, description)
    }

    @Test
    fun `uses matching Home icons for Focus flavors`() {
        val expectedIcons = mapOf(
            FOCUS_RELEASE to R.drawable.ic_focus,
            FOCUS_BETA to R.drawable.ic_focus_beta_foreground,
            FOCUS to R.drawable.ic_focus_nightly_foreground,
            FOCUS_NIGHTLY to R.drawable.ic_focus_nightly_foreground,
            FOCUS_DEBUG to R.drawable.ic_focus_debug_foreground_v2,
        )

        expectedIcons.forEach { (appName, expectedIcon) ->
            val (icon, description) = appIconResources(appName, useSearchResultVariant = false)

            assertEquals(expectedIcon, icon, "Unexpected icon for $appName")
            assertEquals(R.string.app_icon_focus_description, description)
        }
    }

    @Test
    fun `uses the unknown app icon for an unrecognized app`() {
        val (icon, description) = appIconResources(
            appName = "roam",
            useSearchResultVariant = true,
        )

        assertEquals(R.drawable.unknown_app, icon)
        assertEquals(R.string.app_icon_generic_description, description)
    }
}
