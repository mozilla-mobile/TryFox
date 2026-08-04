package org.mozilla.tryfox.ui.composables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.R

class AppIconTest {

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
