package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.data.InstalledTryBuild
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.ui.theme.TryFoxTheme
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FENIX_DEBUG_PACKAGE
import org.mozilla.tryfox.util.FENIX_RELEASE
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeAppCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun flavorSelector_wrapsLastFlavorOntoNextLineWhenCardIsNarrow() {
        val appsByName = listOf(FENIX_RELEASE, FENIX_BETA, FENIX, FENIX_DEBUG)
            .associateWith { appName ->
                AppUiModel(
                    name = appName,
                    packageName = "org.mozilla.tryfox.$appName",
                    installedVersion = null,
                    installedDate = null,
                    apks = ApksResult.Loading,
                )
            }

        composeTestRule.setContent {
            TryFoxTheme {
                HomeAppCard(
                    card = HomeAppCardUiModel(
                        family = HomeAppFamily.Fenix,
                        selectedAppName = FENIX,
                        appsByName = appsByName,
                    ),
                    installStates = emptyMap(),
                    onFlavorSelected = {},
                    onDownloadClick = {},
                    onInstallClick = {},
                    onOpenInstalledApp = {},
                    onOpenTryBuild = { _, _ -> },
                    onDateSelected = { _, _ -> },
                    dateValidator = { true },
                    onReleaseVersionSelected = { _, _ -> },
                    onBuildSelected = { _, _ -> },
                    onDismissBuildPicker = {},
                    modifier = Modifier.width(350.dp),
                )
            }
        }

        val flavorTagPrefix = "home_flavor_fenix_"
        val firstRowBottom = listOf(FENIX_RELEASE, FENIX_BETA, FENIX)
            .map { appName ->
                composeTestRule
                    .onNodeWithTag("$flavorTagPrefix$appName", useUnmergedTree = true)
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .bottom
            }
            .maxOrNull()
            ?: error("Expected first-row flavor chips")
        val debugBounds = composeTestRule
            .onNodeWithTag("$flavorTagPrefix$FENIX_DEBUG", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected Debug flavor to wrap at or below the first row, but top=${debugBounds.top} bottom=$firstRowBottom",
            debugBounds.top >= firstRowBottom,
        )
    }

    @Test
    fun matchingFenixDebugTryBuildShowsCommitAndOpensItsRevision() {
        val build = InstalledTryBuild(
            packageName = FENIX_DEBUG_PACKAGE,
            project = "mozilla-central",
            revision = "abcdef123456",
            commitMessage = "Bug 123456: Fix the Fenix Debug build\n\nIgnored commit description",
            versionName = "145.0a1",
            versionCode = 42L,
        )
        var openedProject: String? = null
        var openedRevision: String? = null

        composeTestRule.setContent {
            TryFoxTheme {
                HomeAppCard(
                    card = HomeAppCardUiModel(
                        family = HomeAppFamily.Fenix,
                        selectedAppName = FENIX_DEBUG,
                        appsByName = mapOf(
                            FENIX_DEBUG to AppUiModel(
                                name = FENIX_DEBUG,
                                packageName = FENIX_DEBUG_PACKAGE,
                                installedVersion = "145.0a1",
                                installedVersionCode = 42L,
                                installedDate = "2026-08-05",
                                installedTryBuild = build,
                                apks = ApksResult.Loading,
                            ),
                        ),
                    ),
                    installStates = emptyMap(),
                    onFlavorSelected = {},
                    onDownloadClick = {},
                    onInstallClick = {},
                    onOpenInstalledApp = {},
                    onOpenTryBuild = { project, revision ->
                        openedProject = project
                        openedRevision = revision
                    },
                    onDateSelected = { _, _ -> },
                    dateValidator = { true },
                    onReleaseVersionSelected = { _, _ -> },
                    onBuildSelected = { _, _ -> },
                    onDismissBuildPicker = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Bug 123456: Fix the Fenix Debug build").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_try_build_revision").performClick()

        assertEquals("mozilla-central", openedProject)
        assertEquals("abcdef123456", openedRevision)
    }

    @Test
    fun nightlyCalendarUsesStoredBuildDateInsteadOfRelativeCardLabel() {
        val buildDate = LocalDate(2024, 12, 31)
        var selectedDate: LocalDate? = null

        composeTestRule.setContent {
            TryFoxTheme {
                HomeAppCard(
                    card = HomeAppCardUiModel(
                        family = HomeAppFamily.Fenix,
                        selectedAppName = FENIX,
                        appsByName = mapOf(
                            FENIX to AppUiModel(
                                name = FENIX,
                                packageName = "org.mozilla.fenix",
                                installedVersion = null,
                                installedDate = null,
                                apks = ApksResult.Success(
                                    listOf(
                                        ApkUiModel(
                                            originalString = "",
                                            date = "Today 09:17",
                                            buildDate = buildDate,
                                            appName = FENIX,
                                            version = "145.0a1",
                                            abi = AbiUiModel("arm64-v8a", true),
                                            url = "https://example.invalid/fenix.apk",
                                            fileName = "fenix.apk",
                                            uniqueKey = "fenix/2024-12-31-09-17-32/fenix.apk",
                                            apkDir = File("/tmp/fenix"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    installStates = emptyMap(),
                    onFlavorSelected = {},
                    onDownloadClick = {},
                    onInstallClick = {},
                    onOpenInstalledApp = {},
                    onOpenTryBuild = { _, _ -> },
                    onDateSelected = { _, date -> selectedDate = date },
                    dateValidator = { true },
                    onReleaseVersionSelected = { _, _ -> },
                    onBuildSelected = { _, _ -> },
                    onDismissBuildPicker = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("home_nightly_date_$FENIX").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(buildDate, selectedDate)
    }
}
