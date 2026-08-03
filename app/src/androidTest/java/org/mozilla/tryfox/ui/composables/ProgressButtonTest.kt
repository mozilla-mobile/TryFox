package org.mozilla.tryfox.ui.composables

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ProgressButtonTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsIdleTextWhenNotLoading() {
        composeRule.setContent {
            ProgressButton(
                text = "Create file",
                loadingText = "Loading",
                isLoading = false,
                onClick = {},
                modifier = Modifier,
            )
        }

        composeRule.onNodeWithText("Create file").assertIsDisplayed()
        composeRule.onAllNodesWithText("Loading").assertCountEquals(0)
    }

    @Test
    fun showsLoadingTextWhenLoading() {
        composeRule.setContent {
            ProgressButton(
                text = "Create file",
                loadingText = "Loading",
                isLoading = true,
                onClick = {},
                modifier = Modifier,
            )
        }

        composeRule.onNodeWithText("Loading").assertIsDisplayed()
        composeRule.onAllNodesWithText("Create file").assertCountEquals(0)
    }

    @Test
    fun buttonDisabledWhenNotEnabled() {
        composeRule.setContent {
            ProgressButton(
                text = "Create file",
                loadingText = "Loading",
                isLoading = false,
                enabled = false,
                onClick = {},
                modifier = Modifier,
            )
        }

        composeRule.onNodeWithText("Create file").assertIsNotEnabled()
    }

    @Test
    fun buttonClickInvokesCallback() {
        var clicks = 0

        composeRule.setContent {
            ProgressButton(
                text = "Create file",
                loadingText = "Loading",
                isLoading = false,
                onClick = { clicks++ },
                modifier = Modifier,
            )
        }

        composeRule.onNode(hasClickAction()).performClick()
        composeRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    @Test
    fun determinateProgressStillShowsLoadingText() {
        composeRule.setContent {
            ProgressButton(
                text = "Create file",
                loadingText = "Loading",
                isLoading = true,
                progress = 0.5f,
                onClick = {},
                modifier = Modifier,
            )
        }

        composeRule.onNodeWithText("Loading").assertIsDisplayed()
    }

    @Test
    fun indicatorColorTransitionsOnlyAfterCompletionSweep() {
        composeRule.mainClock.autoAdvance = false
        val loadingState = mutableStateOf(true)
        val indicatorColor = Color.Blue
        val trackEndColor = Color.Green

        composeRule.setContent {
            ProgressButton(
                text = "Upload",
                loadingText = "Working…",
                isLoading = loadingState.value,
                indicatorColor = indicatorColor,
                trackEndColor = trackEndColor,
                completionSweepMillis = 200f,
                endingAnimation = EndingAnimation.None,
                onClick = {},
                modifier = Modifier,
            )
        }

        val button = composeRule.onNodeWithTag(PROGRESS_BUTTON_TAG)
        composeRule.waitForIdle()
        assertColorClose(indicatorColor, button.indicatorColor())

        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.waitForIdle()
        assertColorClose(indicatorColor, button.indicatorColor(), tolerance = 0.1f)

        composeRule.runOnIdle { loadingState.value = false }

        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        assertColorClose(indicatorColor, button.indicatorColor(), tolerance = 0.3f)

        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.waitForIdle()
        assertColorClose(trackEndColor, button.indicatorColor(), tolerance = 0.1f)

        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun borderAlphaHoldsDuringConstantDelayThenFades() {
        composeRule.mainClock.autoAdvance = false
        val loadingState = mutableStateOf(true)

        composeRule.setContent {
            ProgressButton(
                text = "Upload",
                loadingText = "Working…",
                isLoading = loadingState.value,
                indicatorColor = Color.Blue,
                trackEndColor = Color.Red,
                completionSweepMillis = 200f,
                endingAnimation = EndingAnimation.Constant(duration = 400f),
                onClick = {},
                modifier = Modifier,
            )
        }

        val button = composeRule.onNodeWithTag(PROGRESS_BUTTON_TAG)
        composeRule.waitForIdle()

        composeRule.runOnIdle { loadingState.value = false }

        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        assertFloatEquals(1f, button.borderAlpha())

        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.waitForIdle()
        assertFloatEquals(1f, button.borderAlpha())

        var finalAlpha = 1f
        repeat(15) {
            composeRule.mainClock.advanceTimeBy(100L)
            composeRule.waitForIdle()
            finalAlpha = button.borderAlpha()
            if (finalAlpha <= 0.1f) return@repeat
        }
        assertFloatEquals(0f, finalAlpha, tolerance = 0.1f)

        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun noneEndingShowsIdleTextImmediatelyAfterCompletionSweep() {
        composeRule.mainClock.autoAdvance = false
        val loadingState = mutableStateOf(true)

        composeRule.setContent {
            ProgressButton(
                text = "Install",
                loadingText = "Download",
                isLoading = loadingState.value,
                progress = 0.6f,
                indicatorColor = Color.Green,
                trackEndColor = Color.Green,
                completionSweepMillis = 200f,
                endingAnimation = EndingAnimation.None,
                onClick = {},
            )
        }

        composeRule.onNodeWithText("Download").assertIsDisplayed()
        composeRule.runOnIdle { loadingState.value = false }
        composeRule.mainClock.advanceTimeBy(250L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Install").assertIsDisplayed()
        composeRule.onAllNodesWithText("Download").assertCountEquals(0)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun progressFractionMatchesDeterminateProgress() {
        val progressState = 0.65f
        composeRule.setContent {
            ProgressButton(
                text = "Upload",
                loadingText = "Working…",
                isLoading = true,
                progress = progressState,
                onClick = {},
                modifier = Modifier,
            )
        }

        val button = composeRule.onNodeWithTag(PROGRESS_BUTTON_TAG)
        composeRule.waitForIdle()
        assertFloatEquals(progressState, button.progressFraction())
        assertEquals(
            ProgressBarRangeInfo(progressState, 0f..1f),
            button.progressRangeInfo(),
        )
    }

    @Test
    fun determinateProgressLargeIncreaseAnimatesSmoothly() {
        composeRule.mainClock.autoAdvance = false
        val progressState = mutableStateOf(0.1f)

        composeRule.setContent {
            ProgressButton(
                text = "Upload",
                isLoading = true,
                progress = progressState.value,
                onClick = {},
            )
        }

        val button = composeRule.onNodeWithTag(PROGRESS_BUTTON_TAG)
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.waitForIdle()
        assertFloatEquals(0.1f, button.progressFraction())

        composeRule.runOnIdle { progressState.value = 0.9f }
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.waitForIdle()
        val intermediateProgress = button.progressFraction()
        assertTrue(
            "Expected an intermediate value, but was $intermediateProgress",
            intermediateProgress > 0.1f && intermediateProgress < 0.9f,
        )

        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.waitForIdle()
        assertFloatEquals(0.9f, button.progressFraction())
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun rotatingDeterminateProgressMovesWhileItsArcGrows() {
        composeRule.mainClock.autoAdvance = false
        val progressState = mutableStateOf(0.2f)
        val loadingState = mutableStateOf(true)

        composeRule.setContent {
            ProgressButton(
                text = "Upload",
                isLoading = loadingState.value,
                progress = progressState.value,
                determinateProgressAnimation = DeterminateProgressAnimation.Rotating,
                onClick = {},
            )
        }

        val button = composeRule.onNodeWithTag(PROGRESS_BUTTON_TAG)
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.waitForIdle()
        val initialStart = button.progressStartFraction()
        assertFloatEquals(0.2f, button.progressFraction())

        composeRule.runOnIdle { progressState.value = 0.8f }
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()

        assertTrue("Expected the arc to rotate", button.progressStartFraction() != initialStart)
        assertTrue("Expected the arc to grow smoothly", button.progressFraction() in 0.2f..0.8f)

        val startBeforeCompletion = button.progressStartFraction()
        composeRule.runOnIdle { loadingState.value = false }
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.waitForIdle()
        assertTrue(
            "Expected the leading edge to keep rotating while completing",
            button.progressStartFraction() != startBeforeCompletion,
        )
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun indeterminateProgressSemanticsExposed() {
        composeRule.setContent {
            ProgressButton(
                text = "Upload",
                loadingText = "Working…",
                isLoading = true,
                progress = null,
                onClick = {},
                modifier = Modifier,
            )
        }

        val button = composeRule.onNodeWithTag(PROGRESS_BUTTON_TAG)
        composeRule.waitForIdle()
        assertEquals(ProgressBarRangeInfo.Indeterminate, button.progressRangeInfo())
    }

    @Test
    fun pulseEndingProducesBeatPattern() {
        composeRule.mainClock.autoAdvance = false
        val loadingState = mutableStateOf(true)

        composeRule.setContent {
            ProgressButton(
                text = "Upload",
                loadingText = "Working…",
                isLoading = loadingState.value,
                indicatorColor = Color.Blue,
                trackEndColor = Color.Magenta,
                completionSweepMillis = 200f,
                endingAnimation = EndingAnimation.Pulse(
                    beatDuration = 200f,
                    beats = 2,
                    delayBetweenBeats = 100f,
                ),
                onClick = {},
                modifier = Modifier,
            )
        }

        val button = composeRule.onNodeWithTag(PROGRESS_BUTTON_TAG)
        composeRule.waitForIdle()

        composeRule.runOnIdle { loadingState.value = false }

        composeRule.mainClock.advanceTimeBy(200L) // sweep
        composeRule.mainClock.advanceTimeBy(300L) // indicator color animation
        composeRule.mainClock.advanceTimeBy(100L) // initial delay

        var dropDetected = false
        repeat(6) {
            composeRule.mainClock.advanceTimeBy(50L)
            composeRule.waitForIdle()
            val alpha = button.borderAlpha()
            if (alpha < 0.92f) {
                dropDetected = true
                return@repeat
            }
        }
        assertTrue("Expected drop during pulse", dropDetected)

        var restoredToFull = false
        repeat(4) {
            composeRule.mainClock.advanceTimeBy(50L)
            composeRule.waitForIdle()
            val alpha = button.borderAlpha()
            if (alpha >= 0.95f) {
                restoredToFull = true
                return@repeat
            }
        }
        assertTrue("Expected alpha to return to full", restoredToFull)

        composeRule.mainClock.advanceTimeBy(100L) // delay between beats

        var secondDropDetected = false
        repeat(6) {
            composeRule.mainClock.advanceTimeBy(50L)
            composeRule.waitForIdle()
            val alpha = button.borderAlpha()
            if (alpha < 0.92f) {
                secondDropDetected = true
                return@repeat
            }
        }
        assertTrue("Expected drop on second beat", secondDropDetected)

        composeRule.mainClock.autoAdvance = true
    }

    private fun SemanticsNodeInteraction.indicatorColor(): Color =
        fetchSemanticsNode().config[IndicatorColorKey]

    private fun SemanticsNodeInteraction.borderAlpha(): Float =
        fetchSemanticsNode().config[BorderAlphaKey]

    private fun SemanticsNodeInteraction.progressFraction(): Float =
        fetchSemanticsNode().config[ProgressFractionKey]

    private fun SemanticsNodeInteraction.progressStartFraction(): Float =
        fetchSemanticsNode().config[ProgressStartFractionKey]

    private fun SemanticsNodeInteraction.progressRangeInfo(): ProgressBarRangeInfo =
        fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]

    private fun assertColorClose(expected: Color, actual: Color, tolerance: Float = 0.1f) {
        val distance = abs(expected.red - actual.red) +
            abs(expected.green - actual.green) +
            abs(expected.blue - actual.blue) +
            abs(expected.alpha - actual.alpha)
        assertTrue(
            "Color mismatch. Expected $expected, got $actual (distance $distance)",
            distance <= tolerance,
        )
    }

    private fun assertFloatEquals(expected: Float, actual: Float, tolerance: Float = 0.001f) {
        assertTrue(
            "Expected $expected, got $actual (tolerance $tolerance)",
            abs(expected - actual) <= tolerance,
        )
    }
}
