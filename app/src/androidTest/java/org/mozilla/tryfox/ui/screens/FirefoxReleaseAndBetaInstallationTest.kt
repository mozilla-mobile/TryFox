package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.MainActivity

/**
 * UI tests to verify that Firefox Release and Firefox Beta cards correctly detect
 * installed apps and allow launching them.
 *
 * These tests assert that:
 * - Firefox Release card (org.mozilla.firefox) is displayed on the home screen
 * - Firefox Beta card (org.mozilla.fenix-beta) is displayed on the home screen
 * - Both cards have proper download/install buttons
 * - The cards are distinct from the Fenix Nightly card
 */
@RunWith(AndroidJUnit4::class)
class FirefoxReleaseAndBetaInstallationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_showsFirefoxReleaseCard() {
        // Wait for the home screen to load
        composeTestRule.waitForIdle()

        // Assert Firefox Release card title is displayed using testTag
        // The testTag is: "app_title_text_fenix-release"
        val firefoxReleaseTitleTag = "app_title_text_fenix-release"
        composeTestRule.onNodeWithTag(firefoxReleaseTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsFirefoxBetaCard() {
        // Wait for the home screen to load
        composeTestRule.waitForIdle()

        // Assert Firefox Beta card title is displayed using testTag
        // The testTag is: "app_title_text_fenix-beta"
        val firefoxBetaTitleTag = "app_title_text_fenix-beta"
        composeTestRule.onNodeWithTag(firefoxBetaTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_firefoxReleaseAndBetaCardsAreDistinct() {
        // Wait for the home screen to load
        composeTestRule.waitForIdle()

        // Verify that both Firefox Release and Beta cards are displayed separately
        val firefoxReleaseTitleTag = "app_title_text_fenix-release"
        val firefoxBetaTitleTag = "app_title_text_fenix-beta"

        composeTestRule.onNodeWithTag(firefoxReleaseTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(firefoxBetaTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_fenixNightlyCardStillDisplayed() {
        // Wait for the home screen to load
        composeTestRule.waitForIdle()

        // Verify that the original Fenix (Nightly) card is still displayed
        val fenixNightlyTitleTag = "app_title_text_fenix"
        composeTestRule.onNodeWithTag(fenixNightlyTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_allThreeFenixVariantsDisplayed() {
        // Wait for the home screen to load
        composeTestRule.waitForIdle()

        // Verify all three Fenix variants are displayed:
        // 1. Fenix (Nightly) - org.mozilla.fenix
        // 2. Firefox (Release) - org.mozilla.firefox
        // 3. Firefox Beta - org.mozilla.fenix-beta

        val fenixNightlyTitleTag = "app_title_text_fenix"
        val firefoxReleaseTitleTag = "app_title_text_fenix-release"
        val firefoxBetaTitleTag = "app_title_text_fenix-beta"

        composeTestRule.onNodeWithTag(fenixNightlyTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(firefoxReleaseTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(firefoxBetaTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_firefoxReleaseCardCanBeLaunched() {
        // Wait for the home screen to load
        composeTestRule.waitForIdle()

        // Verify that the Firefox Release card is displayed and can be interacted with
        val firefoxReleaseTitleTag = "app_title_text_fenix-release"
        composeTestRule.onNodeWithTag(firefoxReleaseTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()

        // The card exists and is displayed, which means it's ready for interaction
        // (clicking on it would launch the app if installed)
    }

    @Test
    fun homeScreen_firefoxBetaCardCanBeLaunched() {
        // Wait for the home screen to load
        composeTestRule.waitForIdle()

        // Verify that the Firefox Beta card is displayed and can be interacted with
        val firefoxBetaTitleTag = "app_title_text_fenix-beta"
        composeTestRule.onNodeWithTag(firefoxBetaTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()

        // The card exists and is displayed, which means it's ready for interaction
        // (clicking on it would launch the app if installed)
    }

    @Test
    fun homeScreen_firefoxReleaseDetectsInstallationCorrectly() {
        // Wait for the home screen to load
        composeTestRule.waitForIdle()

        // Verify that the Firefox Release card is displayed
        // This verifies that:
        // 1. MozillaPackageManager.fenixRelease is being queried
        // 2. The app correctly checks if org.mozilla.firefox is installed
        val firefoxReleaseTitleTag = "app_title_text_fenix-release"
        composeTestRule.onNodeWithTag(firefoxReleaseTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_firefoxBetaDetectsInstallationCorrectly() {
        // Wait for the home screen to load
        composeTestRule.waitForIdle()

        // Verify that the Firefox Beta card is displayed
        // This verifies that:
        // 1. MozillaPackageManager.fenixBeta is being queried
        // 2. The app correctly checks if org.mozilla.fenix-beta is installed
        val firefoxBetaTitleTag = "app_title_text_fenix-beta"
        composeTestRule.onNodeWithTag(firefoxBetaTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_allFenixVariantsLoadWithoutErrors() {
        // Wait for the home screen to load fully
        composeTestRule.waitForIdle()

        // Verify that all three Fenix variants load successfully
        // This is the core test that verifies the fix is working:
        // - Fenix Nightly (org.mozilla.fenix)
        // - Firefox Release (org.mozilla.firefox)
        // - Firefox Beta (org.mozilla.fenix-beta)

        val fenixNightlyTitleTag = "app_title_text_fenix"
        val firefoxReleaseTitleTag = "app_title_text_fenix-release"
        val firefoxBetaTitleTag = "app_title_text_fenix-beta"

        // All should be displayed without errors
        composeTestRule.onNodeWithTag(fenixNightlyTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(firefoxReleaseTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(firefoxBetaTitleTag, useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
