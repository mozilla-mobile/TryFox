package org.mozilla.tryfox.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tryfox.data.FakeLanMessageHistoryRepository
import org.mozilla.tryfox.lan.LanReceivedMessage
import org.mozilla.tryfox.ui.theme.TryFoxTheme

@RunWith(AndroidJUnit4::class)
class ReceiveMessageHistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyHistoryShowsEmptyState() {
        val viewModel = ReceiveMessageHistoryViewModel(
            messageHistoryRepository = FakeLanMessageHistoryRepository(),
            ioDispatcher = Dispatchers.Unconfined,
        )

        composeTestRule.setContent {
            TryFoxTheme {
                ReceiveMessageHistoryScreen(
                    onNavigateUp = {},
                    onOpenDeepLink = {},
                    receiveMessageHistoryViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("No messages yet").assertIsDisplayed()
    }

    @Test
    fun clickingAcceptedMessageOpensDeepLink() {
        val historyRepository = FakeLanMessageHistoryRepository().apply {
            setHistory(
                listOf(
                    LanReceivedMessage(
                        id = 1L,
                        receivedAt = 1_760_000_000_000L,
                        accepted = true,
                        messageId = "message-1",
                        tryfoxDeepLink = "tryfox://jobs?repo=try&revision=abcdef123456",
                        title = "Nightly run",
                        revision = "abcdef123456",
                        author = "dev@mozilla.com",
                        pushComment = "Bug 123 - Fix tests",
                    ),
                ),
            )
        }
        val viewModel = ReceiveMessageHistoryViewModel(
            messageHistoryRepository = historyRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
        var openedDeepLink: String? = null

        composeTestRule.setContent {
            TryFoxTheme {
                ReceiveMessageHistoryScreen(
                    onNavigateUp = {},
                    onOpenDeepLink = { openedDeepLink = it },
                    receiveMessageHistoryViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Bug 123 - Fix tests").assertIsDisplayed()
        composeTestRule.onNodeWithText("dev@mozilla.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nightly run").performClick()

        assertEquals("tryfox://jobs?repo=try&revision=abcdef123456", openedDeepLink)
    }

    @Test
    fun rejectedMessageShowsRejectionDetails() {
        val historyRepository = FakeLanMessageHistoryRepository().apply {
            setHistory(
                listOf(
                    LanReceivedMessage(
                        id = 1L,
                        receivedAt = 1_760_000_000_000L,
                        accepted = false,
                        error = "push-not-found",
                        messageId = "message-1",
                        sourceUrl = "https://treeherder.mozilla.org/jobs?repo=try&revision=abcdef123456",
                        revision = "abcdef123456",
                    ),
                ),
            )
        }
        val viewModel = ReceiveMessageHistoryViewModel(
            messageHistoryRepository = historyRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )

        composeTestRule.setContent {
            TryFoxTheme {
                ReceiveMessageHistoryScreen(
                    onNavigateUp = {},
                    onOpenDeepLink = {},
                    receiveMessageHistoryViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Rejected: push-not-found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Revision: abcdef123456").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("No comment").assertCountEquals(0)
    }

    @Test
    fun swipingMessageDeletesItFromHistory() {
        val historyRepository = FakeLanMessageHistoryRepository().apply {
            setHistory(
                listOf(
                    LanReceivedMessage(
                        id = 1L,
                        receivedAt = 1_760_000_000_000L,
                        accepted = true,
                        messageId = "message-1",
                        title = "Nightly run",
                        revision = "abcdef123456",
                        pushComment = "Bug 123 - Fix tests",
                    ),
                ),
            )
        }
        val viewModel = ReceiveMessageHistoryViewModel(
            messageHistoryRepository = historyRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )

        composeTestRule.setContent {
            TryFoxTheme {
                ReceiveMessageHistoryScreen(
                    onNavigateUp = {},
                    onOpenDeepLink = {},
                    receiveMessageHistoryViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Nightly run").performTouchInput { swipeLeft() }
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("Nightly run").assertCountEquals(0)
        assertEquals(emptyList<LanReceivedMessage>(), historyRepository.history.value)
    }
}
