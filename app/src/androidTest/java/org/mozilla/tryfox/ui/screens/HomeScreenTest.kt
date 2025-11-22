package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
    fun homeScreen_showsFenixNightlyCard() {
        // --- Fenix (Nightly) Card ---
        val fenixTitleTag = "app_title_text_fenix"

        // Assert Fenix text (found by tag) is displayed
        composeTestRule.onNodeWithTag(fenixTitleTag, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsFenixBetaCard() {
        // --- Fenix Beta Card ---
        val betaTitleTag = "app_title_text_fenix-beta"

        // Assert Beta text (found by tag) is displayed
        composeTestRule.onNodeWithTag(betaTitleTag, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsFenixReleaseCard() {
        // --- Fenix Release Card ---
        val releaseTitleTag = "app_title_text_fenix-release"

        // Assert Release text (found by tag) is displayed
        composeTestRule.onNodeWithTag(releaseTitleTag, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsAllThreeFenixVariants() {
        // --- Fenix (Nightly) Card ---
        val fenixTitleTag = "app_title_text_fenix"
        composeTestRule.onNodeWithTag(fenixTitleTag, useUnmergedTree = true).assertIsDisplayed()

        // --- Fenix Beta Card ---
        val betaTitleTag = "app_title_text_fenix-beta"
        composeTestRule.onNodeWithTag(betaTitleTag, useUnmergedTree = true).assertIsDisplayed()

        // --- Fenix Release Card ---
        val releaseTitleTag = "app_title_text_fenix-release"
        composeTestRule.onNodeWithTag(releaseTitleTag, useUnmergedTree = true).assertIsDisplayed()
    }
}
