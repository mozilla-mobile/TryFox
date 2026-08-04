package org.mozilla.tryfox.ui.composables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.R
import org.mozilla.tryfox.util.FENIX_DEBUG

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
    fun `uses the unknown app icon for an unrecognized app`() {
        val (icon, description) = appIconResources(
            appName = "roam",
            useSearchResultVariant = true,
        )

        assertEquals(R.drawable.unknown_app, icon)
        assertEquals(R.string.app_icon_generic_description, description)
    }
}
