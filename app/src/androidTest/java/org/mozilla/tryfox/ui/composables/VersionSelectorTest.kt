package org.mozilla.tryfox.ui.composables

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.ui.theme.TryFoxTheme
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_RELEASE

@RunWith(AndroidJUnit4::class)
class VersionSelectorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun releaseVersionSelector_opensAtSelectedMajorAndConfirmsChosenVariant() {
        var confirmedVersion: String? = null
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                VersionSelector(
                    appName = FOCUS_RELEASE,
                    selectedReleaseVersion = "151.0.1",
                    availableReleaseVersions = listOf("151.0.1", "151.0.0", "150.0.1"),
                    onReleaseVersionSelected = { confirmedVersion = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_focus-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_selector_sheet_focus-release", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Version").assertIsDisplayed()
        composeTestRule.onNodeWithText("Builds:").assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_version_variant_151_0_1", useUnmergedTree = true).assertIsSelected()

        composeTestRule.onNodeWithTag("release_version_major_picker_focus-release", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_version_variant_151_0_0", useUnmergedTree = true).performClick()

        assertTrue(confirmedVersion == "151.0.0")
        assertTrue(
            composeTestRule
                .onAllNodesWithTag("release_version_selector_sheet_focus-release", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun betaVersionSelector_filtersVariantsAndCancelKeepsSelection() {
        var confirmedVersion: String? = null
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                VersionSelector(
                    appName = FOCUS_BETA,
                    selectedReleaseVersion = "151.0b2",
                    availableReleaseVersions = listOf("151.0b2", "151.0b1", "150.0b3"),
                    onReleaseVersionSelected = { confirmedVersion = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_focus-beta", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_variant_151_0b2", useUnmergedTree = true).assertIsSelected()
        assertTrue(
            composeTestRule
                .onAllNodesWithTag("release_version_variant_150_0b3", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )

        composeTestRule.onNodeWithTag("release_version_major_picker_focus-beta", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue(confirmedVersion == null)
    }

    @Test
    fun versionSelector_supportsTheHighestMajorProvidedByTheArchive() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                VersionSelector(
                    appName = FOCUS_RELEASE,
                    selectedReleaseVersion = "155.0.1",
                    availableReleaseVersions = listOf("155.0.1", "154.0.2"),
                    onReleaseVersionSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_focus-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_variant_155_0_1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fenixReleaseSelector_showsCandidateVariantsAndConfirmsTheSelectedRc() {
        var confirmedVersion: String? = null
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                VersionSelector(
                    appName = FENIX_RELEASE,
                    selectedReleaseVersion = "153.0.4",
                    availableReleaseVersions = listOf("153.0.4", "153.0.4-RC2", "153.0.4-RC1"),
                    onReleaseVersionSelected = { confirmedVersion = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_fenix-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_show_candidates_fenix-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_variant_153_0_4-RC2", useUnmergedTree = true).performClick()

        assertTrue(confirmedVersion == "153.0.4-RC2")
    }

    @Test
    fun fenixBetaSelector_showsCandidateVariants() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                VersionSelector(
                    appName = FENIX_BETA,
                    selectedReleaseVersion = "153.0b5",
                    availableReleaseVersions = listOf("153.0b5", "153.0b5-RC2", "153.0b5-RC1"),
                    onReleaseVersionSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_fenix-beta", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_show_candidates_fenix-beta", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_variant_153_0b5-RC2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun releaseCandidates_areHiddenUntilShown() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                VersionSelector(
                    appName = FENIX_RELEASE,
                    selectedReleaseVersion = "153.0.4",
                    availableReleaseVersions = listOf("153.0.4", "153.0.4-RC2", "153.0.4-RC1"),
                    onReleaseVersionSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_fenix-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_show_candidates_fenix-release", useUnmergedTree = true).assertIsOff()
        composeTestRule.onNodeWithTag("release_version_variant_153_0_4", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeTestRule
                .onAllNodesWithTag("release_version_variant_153_0_4-RC2", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun releaseCandidates_areShownWhenAnRcIsSelected() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                VersionSelector(
                    appName = FENIX_RELEASE,
                    selectedReleaseVersion = "153.0.4-RC2",
                    availableReleaseVersions = listOf("153.0.4", "153.0.4-RC2", "153.0.4-RC1"),
                    onReleaseVersionSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_fenix-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_show_candidates_fenix-release", useUnmergedTree = true).assertIsOn()
        composeTestRule.onNodeWithTag("release_version_variant_153_0_4-RC2", useUnmergedTree = true).assertIsSelected()
    }

    @Test
    fun releaseCandidates_areShownWhenOnlyRcBuildsExist() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                VersionSelector(
                    appName = FENIX_RELEASE,
                    selectedReleaseVersion = null,
                    availableReleaseVersions = listOf("153.0.4-RC2", "153.0.4-RC1"),
                    onReleaseVersionSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_fenix-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_show_candidates_fenix-release", useUnmergedTree = true).assertIsOn()
        composeTestRule.onNodeWithTag("release_version_variant_153_0_4-RC2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun focusSelector_hasNoReleaseCandidateCheckbox() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                VersionSelector(
                    appName = FOCUS_RELEASE,
                    selectedReleaseVersion = "155.0.1",
                    availableReleaseVersions = listOf("155.0.1", "154.0.2"),
                    onReleaseVersionSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_focus-release", useUnmergedTree = true).performClick()
        assertTrue(
            composeTestRule
                .onAllNodesWithTag("release_version_show_candidates_focus-release", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }
}
