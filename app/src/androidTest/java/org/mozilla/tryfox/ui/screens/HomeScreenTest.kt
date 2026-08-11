package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.MainActivity

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule(order = 0)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
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

    @Test
    fun homeScreen_settingsButtonNavigatesToSettingsScreen() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cache").assertIsDisplayed()
        composeTestRule.onNodeWithText("Home screen layout").assertIsDisplayed()
        composeTestRule.onNodeWithText("One card per app").assertIsDisplayed()
        composeTestRule.onNodeWithText("One card per flavor of each app").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsNotificationPreference() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        composeTestRule.onNodeWithText("Notifications").assertIsDisplayed()
        val description = "Allow notifications to keep you updated on APK download progress, " +
            "including downloads that continue in the background."
        composeTestRule.onNodeWithText(description).assertIsDisplayed()
    }
}
