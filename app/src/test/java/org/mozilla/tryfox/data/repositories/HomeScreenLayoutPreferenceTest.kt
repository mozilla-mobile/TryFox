package org.mozilla.tryfox.data.repositories

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.model.HomeScreenLayout

class HomeScreenLayoutPreferenceTest {
    @Test
    fun `missing or invalid layout defaults to one card per app`() {
        assertEquals(HomeScreenLayout.OneCardPerApp, homeScreenLayoutFromStoredValue(null))
        assertEquals(HomeScreenLayout.OneCardPerApp, homeScreenLayoutFromStoredValue("unknown"))
    }

    @Test
    fun `stored layout is restored`() {
        assertEquals(
            HomeScreenLayout.OneCardPerFlavor,
            homeScreenLayoutFromStoredValue(HomeScreenLayout.OneCardPerFlavor.name),
        )
    }
}
