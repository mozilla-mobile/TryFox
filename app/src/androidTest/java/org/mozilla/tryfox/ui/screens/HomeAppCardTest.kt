package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.data.InstalledTryBuild
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.ui.theme.TryFoxTheme
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FENIX_DEBUG_PACKAGE

@RunWith(AndroidJUnit4::class)
class HomeAppCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun matchingFenixDebugTryBuildShowsCommitAndOpensItsRevision() {
        val build = InstalledTryBuild(
            packageName = FENIX_DEBUG_PACKAGE,
            project = "mozilla-central",
            revision = "abcdef123456",
            commitMessage = "Fix the Fenix Debug build\n\nIgnored commit description",
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

        composeTestRule.onNodeWithText("Fix the Fenix Debug build").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_try_build_revision").performClick()

        assertEquals("mozilla-central", openedProject)
        assertEquals("abcdef123456", openedRevision)
    }
}
