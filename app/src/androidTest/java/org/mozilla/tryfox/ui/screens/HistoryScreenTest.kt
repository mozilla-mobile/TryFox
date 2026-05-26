package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.data.FakeCacheManager
import org.mozilla.tryfox.data.FakeDownloadFileRepository
import org.mozilla.tryfox.data.FakeHistoryRepository
import org.mozilla.tryfox.data.FakeIntentManager
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry
import org.mozilla.tryfox.ui.theme.TryFoxTheme

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickingRevisionInvokesTreeherderRevisionNavigation() {
        val revision = "abcdef1234567890"
        val historyRepository = FakeHistoryRepository().apply {
            setEntries(listOf(historyEntry(project = "mozilla-central", revision = revision)))
        }
        val historyViewModel = HistoryViewModel(
            historyRepository = historyRepository,
            downloadFileRepository = FakeDownloadFileRepository(),
            cacheManager = FakeCacheManager(),
            intentManager = FakeIntentManager(),
        )
        var selectedProject: String? = null
        var selectedRevision: String? = null

        composeTestRule.setContent {
            TryFoxTheme {
                HistoryScreen(
                    onNavigateUp = {},
                    onNavigateToTreeherderRevision = { project, clickedRevision ->
                        selectedProject = project
                        selectedRevision = clickedRevision
                    },
                    historyViewModel = historyViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Revision: ${revision.take(12)}").performClick()

        assertEquals("mozilla-central", selectedProject)
        assertEquals(revision, selectedRevision)
    }

    private fun historyEntry(
        project: String,
        revision: String,
    ): TreeherderInstallHistoryEntry =
        TreeherderInstallHistoryEntry(
            project = project,
            revision = revision,
            commitMessage = "Bug 123 - Test history",
            author = "author@mozilla.com",
            pushTimestamp = 1_716_460_800L,
            appName = "fenix",
            jobName = "signing-apk-fenix-nightly",
            jobSymbol = "Bfs",
            taskId = "task-id",
            artifactName = "public/build/target.arm64-v8a.apk",
            artifactFileName = "target.arm64-v8a.apk",
            downloadUrl = "https://example.com/task/artifact",
            abiName = "arm64-v8a",
            abiSupported = true,
            expires = "2026-01-01T00:00:00.000Z",
            cacheRelativePath = "treeherder/task-id/target.arm64-v8a.apk",
            lastInstallerLaunchTimestamp = 123L,
        )
}
