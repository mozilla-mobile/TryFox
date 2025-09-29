package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.MainActivity
import org.mozilla.tryfox.R

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_showsFenixAndFocusCards() {
        val activity = composeTestRule.activity

        // --- Fenix (Nightly) Card ---
        val fenixIconDesc = activity.getString(R.string.app_icon_firefox_nightly_description)
        val fenixIconMatcher = hasContentDescription(fenixIconDesc)
        val fenixTitleTag = "app_title_text_fenix"

        // Assert Fenix icon is displayed
        composeTestRule.onNode(fenixIconMatcher, useUnmergedTree = true).assertIsDisplayed()

        // Assert Fenix text (found by tag) is displayed
        composeTestRule.onNodeWithTag(fenixTitleTag, useUnmergedTree = true).assertIsDisplayed()

        // --- Focus Card ---
        val focusIconDesc = activity.getString(R.string.app_icon_focus_description)
        val focusIconMatcher = hasContentDescription(focusIconDesc)
        val focusTitleTag = "app_title_text_focus"

        // Assert Focus icon is displayed
        composeTestRule.onNode(focusIconMatcher, useUnmergedTree = true).assertIsDisplayed()

        // Assert Focus text (found by tag) is displayed
        composeTestRule.onNodeWithTag(focusTitleTag, useUnmergedTree = true).assertIsDisplayed()
    }
}
