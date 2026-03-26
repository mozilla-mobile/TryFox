package org.mozilla.tryfox.ui.composables

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import org.mozilla.tryfox.util.FOCUS
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
                    selectedReleaseMajor = null,
                    availableReleaseMajors = emptyList(),
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
                    selectedReleaseMajor = 147,
                    availableReleaseMajors = listOf(147, 146),
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
