package org.mozilla.tryfox.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.TryFoxViewModel
import org.mozilla.tryfox.data.Artifact
import org.mozilla.tryfox.data.ArtifactsResponse
import org.mozilla.tryfox.data.FakeCacheManager
import org.mozilla.tryfox.data.FakeDownloadFileRepository
import org.mozilla.tryfox.data.FakeHistoryRepository
import org.mozilla.tryfox.data.FakeIntentManager
import org.mozilla.tryfox.data.JobDetails
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.RevisionDetail
import org.mozilla.tryfox.data.RevisionMeta
import org.mozilla.tryfox.data.RevisionResult
import org.mozilla.tryfox.data.SearchHistoryEntry
import org.mozilla.tryfox.data.SearchHistoryQueryType
import org.mozilla.tryfox.data.TreeherderJobsResponse
import org.mozilla.tryfox.data.TreeherderRevisionResponse
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import org.mozilla.tryfox.ui.theme.TryFoxTheme

@RunWith(AndroidJUnit4::class)
class TreeherderApksScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun treeherderScreen_showsLoaderUntilSearchCompletes_thenDisplaysResults() {
        val targetJobName = "signing-apk-focus-nightly"
        val targetDisplayName = "Focus nightly"
        val viewModel = TryFoxViewModel(
            fenixRepository = DelayedTreeherderRepository(targetJobName = targetJobName),
            downloadFileRepository = FakeDownloadFileRepository(),
            cacheManager = FakeCacheManager(),
            intentManager = FakeIntentManager(),
            historyRepository = FakeHistoryRepository(),
            project = "mozilla-central",
            revision = "ed209aa2136b241686ff20489c5cb622348e2ecf",
            supportedAbis = listOf("arm64-v8a"),
            infoLogger = { _, _ -> 0 },
        )

        composeTestRule.setContent {
            TryFoxTheme {
                TryFoxMainScreen(
                    tryFoxViewModel = viewModel,
                    deepLinkProject = null,
                    deepLinkRevision = null,
                    onNavigateUp = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag(TREEHERDER_LOADING_STATE_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag(TREEHERDER_LOADING_STATE_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText(targetDisplayName, substring = false, useUnmergedTree = true)
            .assertCountEquals(0)

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("revision_search_push_ed209aa2136b241686ff20489c5cb622348e2ecf", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onAllNodesWithTag(TREEHERDER_LOADING_STATE_TAG, useUnmergedTree = true)
            .assertCountEquals(0)
        composeTestRule.onNodeWithText(targetDisplayName, substring = false, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("revision_search_push_ed209aa2136b241686ff20489c5cb622348e2ecf", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun searchHistory_filtersSuggestions_andSelectsAnEntry() {
        val emailEntry = SearchHistoryEntry(
            project = "mozilla-central",
            query = "person@mozilla.org",
            queryType = SearchHistoryQueryType.EMAIL,
            searchedAt = 2L,
        )
        var query by mutableStateOf("")
        var selectedEntry: SearchHistoryEntry? = null

        composeTestRule.setContent {
            TryFoxTheme {
                SearchSection(
                    selectedProject = "try",
                    onProjectSelected = {},
                    revision = query,
                    onRevisionChange = { query = it },
                    onSearchClick = {},
                    isLoading = false,
                    searchHistory = listOf(
                        emailEntry,
                        SearchHistoryEntry("try", "abc123", SearchHistoryQueryType.REVISION, 1L),
                    ),
                    onHistoryItemSelected = { selectedEntry = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(TREEHERDER_SEARCH_HISTORY_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Recent searches").assertIsDisplayed()
        composeTestRule.onNodeWithText("central").assertIsDisplayed()

        composeTestRule.onNodeWithText("person@mozilla.org").performClick()
        composeTestRule.runOnIdle {
            assertEquals(emailEntry, selectedEntry)
        }

        composeTestRule.onNodeWithTag("profile_email_input").performTextInput("no-match")
        composeTestRule.onNodeWithTag("profile_email_clear_button").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TREEHERDER_SEARCH_HISTORY_TAG).assertCountEquals(0)
    }

    @Test
    fun selectingHistoryEntry_hidesHistoryBeforeShowingSearchResults() {
        val viewModel = TryFoxViewModel(
            fenixRepository = DelayedTreeherderRepository(targetJobName = "signing-apk-focus-nightly"),
            downloadFileRepository = FakeDownloadFileRepository(),
            cacheManager = FakeCacheManager(),
            intentManager = FakeIntentManager(),
            historyRepository = FakeHistoryRepository(),
            project = null,
            revision = null,
            supportedAbis = listOf("arm64-v8a"),
            infoLogger = { _, _ -> 0 },
        )

        composeTestRule.setContent {
            TryFoxTheme {
                TryFoxMainScreen(
                    tryFoxViewModel = viewModel,
                    deepLinkProject = null,
                    deepLinkRevision = null,
                    onNavigateUp = {},
                    searchHistory = listOf(
                        SearchHistoryEntry("try", "abc123", SearchHistoryQueryType.REVISION, 1L),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag(TREEHERDER_SEARCH_HISTORY_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag("treeherder_search_history_0").performClick()
        composeTestRule.onAllNodesWithTag(TREEHERDER_SEARCH_HISTORY_TAG).assertCountEquals(0)
    }

    private class DelayedTreeherderRepository(
        private val targetJobName: String,
    ) : TreeherderRepository {

        override suspend fun getPushByRevision(
            project: String,
            revision: String,
        ): NetworkResult<TreeherderRevisionResponse> {
            return NetworkResult.Success(
                TreeherderRevisionResponse(
                    meta = RevisionMeta(
                        revision = revision,
                        count = 1,
                        repository = project,
                    ),
                    results = listOf(
                        RevisionResult(
                            id = 1858186,
                            revision = revision,
                            author = "author@mozilla.com",
                            revisions = listOf(
                                RevisionDetail(
                                    resultSetId = 1858186,
                                    repositoryId = 1,
                                    revision = revision,
                                    author = "author@mozilla.com",
                                    comments = "Bug 2001527: test patch",
                                ),
                            ),
                            revisionCount = 1,
                            pushTimestamp = 0L,
                            repositoryId = 1,
                        ),
                    ),
                ),
            )
        }

        override suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse> {
            throw UnsupportedOperationException("Not needed in this test")
        }

        override suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse> {
            return getJobsForPushPage(pushId = pushId, page = 1, count = 2000)
        }

        override suspend fun getJobsForPushPage(
            pushId: Int,
            page: Int,
            count: Int,
        ): NetworkResult<TreeherderJobsResponse> {
            return if (page == 1) {
                NetworkResult.Success(
                    TreeherderJobsResponse(
                        results = listOf(
                            JobDetails(
                                appName = "focus",
                                jobName = targetJobName,
                                jobSymbol = "Bs",
                                taskId = "preferred-task",
                            ),
                        ),
                    ),
                )
            } else {
                NetworkResult.Success(TreeherderJobsResponse(results = emptyList()))
            }
        }

        override suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse> {
            delay(750)
            return NetworkResult.Success(
                ArtifactsResponse(
                    artifacts = listOf(
                        Artifact(
                            storageType = "taskcluster",
                            name = "public/build/target.arm64-v8a.apk",
                            expires = "2027-03-16T09:44:23.244Z",
                            contentType = "application/vnd.android.package-archive",
                        ),
                    ),
                ),
            )
        }
    }
}
