package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.MainActivity

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_showsThreeGroupedAppCards() {
        composeTestRule.onNodeWithTag("home_app_card_fenix", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_app_card_focus", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_app_card_referencebrowser", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun homeScreen_qrScannerButtonNavigatesToScannerScreen() {
        composeTestRule.onNodeWithContentDescription("Scan QR code").performClick()

        composeTestRule.onNodeWithText("Scan QR code").assertIsDisplayed()
    }
}
