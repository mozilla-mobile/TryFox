package org.mozilla.tryfox.ui.composables

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.theme.TryFoxTheme
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_RELEASE
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArchiveGroupCardHeaderLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nightlyCard_placesDateSelectorBelowHeaderRow() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                ArchiveGroupCard(
                    apks = listOf(
                        createApkUiModel(
                            appName = FOCUS,
                            version = "126.0a1",
                            date = "2026-03-26 10:00",
                        ),
                    ),
                    onDownloadClick = {},
                    onInstallClick = {},
                    onOpenAppClick = {},
                    onUninstallClick = {},
                    appState = null,
                    onDateSelected = {},
                    userPickedDate = LocalDate(2026, 3, 25),
                    selectedReleaseVersion = null,
                    availableReleaseVersions = emptyList(),
                    appName = FOCUS,
                    errorMessage = null,
                    isLoading = false,
                    dateValidator = { true },
                    onClearDate = {},
                    onReleaseVersionSelected = {},
                )
            }
        }

        val headerBounds = composeTestRule
            .onNodeWithTag("app_header_row_focus", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val dateChipBounds = composeTestRule
            .onNodeWithTag("app_date_chip_focus", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected date chip to appear below the header row, but top=${dateChipBounds.top} bottom=${headerBounds.bottom}",
            dateChipBounds.top > headerBounds.bottom,
        )
    }

    @Test
    fun releaseCard_showsVersionChipAndNoDateChip() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                ArchiveGroupCard(
                    apks = listOf(
                        createApkUiModel(
                            appName = FOCUS_RELEASE,
                            version = "147.0.1",
                            date = "",
                        ),
                    ),
                    onDownloadClick = {},
                    onInstallClick = {},
                    onOpenAppClick = {},
                    onUninstallClick = {},
                    appState = null,
                    onDateSelected = {},
                    userPickedDate = null,
                    selectedReleaseVersion = "147.0.1",
                    availableReleaseVersions = listOf("147.0.1", "147.0", "146.0.1"),
                    appName = FOCUS_RELEASE,
                    errorMessage = null,
                    isLoading = false,
                    dateValidator = { true },
                    onClearDate = {},
                    onReleaseVersionSelected = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("release_version_chip_focus-release", useUnmergedTree = true)
            .assertIsDisplayed()

        assertTrue(
            "Release card should not render a date chip below the header",
            composeTestRule
                .onAllNodesWithTag("app_date_chip_focus-release", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun releaseVersionSelector_opensAtSelectedMajorAndConfirmsChosenVariant() {
        var confirmedVersion: String? = null
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                ArchiveGroupCard(
                    apks = listOf(createApkUiModel(FOCUS_RELEASE, "151.0.1", "")),
                    onDownloadClick = {}, onInstallClick = {}, onOpenAppClick = {}, onUninstallClick = {},
                    appState = null, onDateSelected = {}, userPickedDate = null,
                    selectedReleaseVersion = "151.0.1",
                    availableReleaseVersions = listOf("151.0.1", "151.0.0", "150.0.1"),
                    appName = FOCUS_RELEASE, errorMessage = null, isLoading = false,
                    dateValidator = { true }, onClearDate = {},
                    onReleaseVersionSelected = { confirmedVersion = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_focus-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_selector_sheet_focus-release", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_version_variant_151_0_1", useUnmergedTree = true).assertIsSelected()

        composeTestRule.onNodeWithTag("release_version_major_picker_focus-release", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_version_variant_151_0_0", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText("Select version").performClick()

        assertTrue(confirmedVersion == "151.0.0")
    }

    @Test
    fun betaVersionSelector_filtersVariantsAndCancelKeepsSelection() {
        var confirmedVersion: String? = null
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                ArchiveGroupCard(
                    apks = listOf(createApkUiModel(FOCUS_BETA, "151.0b2", "")),
                    onDownloadClick = {}, onInstallClick = {}, onOpenAppClick = {}, onUninstallClick = {},
                    appState = null, onDateSelected = {}, userPickedDate = null,
                    selectedReleaseVersion = "151.0b2",
                    availableReleaseVersions = listOf("151.0b2", "151.0b1", "150.0b3"),
                    appName = FOCUS_BETA, errorMessage = null, isLoading = false,
                    dateValidator = { true }, onClearDate = {},
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
    fun versionSelector_keepsOutOfRangeCurrentMajorUnavailableUntilAnotherMajorIsChosen() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                ArchiveGroupCard(
                    apks = listOf(createApkUiModel(FOCUS_RELEASE, "155.0.1", "")),
                    onDownloadClick = {}, onInstallClick = {}, onOpenAppClick = {}, onUninstallClick = {},
                    appState = null, onDateSelected = {}, userPickedDate = null,
                    selectedReleaseVersion = "155.0.1",
                    availableReleaseVersions = listOf("155.0.1", "154.0.2"),
                    appName = FOCUS_RELEASE, errorMessage = null, isLoading = false,
                    dateValidator = { true }, onClearDate = {}, onReleaseVersionSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_focus-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText("The current major version (155) is outside the selectable range. Choose a version from 117 to 154.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Select version").assertIsNotEnabled()
    }

    @Test
    fun fenixReleaseSelector_showsCandidateVariantsAndConfirmsTheSelectedRc() {
        var confirmedVersion: String? = null
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                ArchiveGroupCard(
                    apks = listOf(createApkUiModel(FENIX_RELEASE, "153.0.4", "")),
                    onDownloadClick = {}, onInstallClick = {}, onOpenAppClick = {}, onUninstallClick = {},
                    appState = null, onDateSelected = {}, userPickedDate = null,
                    selectedReleaseVersion = "153.0.4",
                    availableReleaseVersions = listOf("153.0.4", "153.0.4-RC2", "153.0.4-RC1"),
                    appName = FENIX_RELEASE, errorMessage = null, isLoading = false,
                    dateValidator = { true }, onClearDate = {},
                    onReleaseVersionSelected = { confirmedVersion = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_fenix-release", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_variant_153_0_4-RC2", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText("Select version").performClick()

        assertTrue(confirmedVersion == "153.0.4-RC2")
    }

    @Test
    fun fenixBetaSelector_showsCandidateVariants() {
        composeTestRule.setContent {
            TryFoxTheme(dynamicColor = false) {
                ArchiveGroupCard(
                    apks = listOf(createApkUiModel(FENIX_BETA, "153.0b5", "")),
                    onDownloadClick = {}, onInstallClick = {}, onOpenAppClick = {}, onUninstallClick = {},
                    appState = null, onDateSelected = {}, userPickedDate = null,
                    selectedReleaseVersion = "153.0b5",
                    availableReleaseVersions = listOf("153.0b5", "153.0b5-RC2", "153.0b5-RC1"),
                    appName = FENIX_BETA, errorMessage = null, isLoading = false,
                    dateValidator = { true }, onClearDate = {}, onReleaseVersionSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("release_version_chip_fenix-beta", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("release_version_variant_153_0b5-RC2", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun createApkUiModel(
        appName: String,
        version: String,
        date: String,
    ): ApkUiModel {
        return ApkUiModel(
            originalString = "$appName-$version",
            date = date,
            appName = appName,
            version = version,
            abi = AbiUiModel(name = "arm64-v8a", isSupported = true),
            url = "https://example.invalid/$appName/$version.apk",
            fileName = "$appName-$version.apk",
            downloadState = DownloadState.NotDownloaded,
            uniqueKey = "$appName/$version",
            apkDir = File("/tmp"),
        )
    }
}
