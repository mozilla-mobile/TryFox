package org.mozilla.tryfox.ui.composables

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val TAG = "ProgressButton"

@Composable
fun ProgressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    progress: Float? = null,
    loadingText: String = text,
    determinateProgressAnimation: DeterminateProgressAnimation = DeterminateProgressAnimation.Static,
    segmentLengthFraction: Float = 0.18f,
    enabled: Boolean = true,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    trackEndColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    shape: Shape = ButtonDefaults.shape,
    strokeWidth: Dp = 6.dp,
    fullCycleMillis: Int = 1800,
    ignoreClicksDuringClosingAnimation: Boolean = true,
    completionSweepMillis: Float = 500f,
    endingAnimation: EndingAnimation = EndingAnimation.Constant(duration = 1000f),
    semanticsTag: String = PROGRESS_BUTTON_TAG,
) {
    val baseSegmentFraction = segmentLengthFraction.coerceIn(0f, 1f)
    val clampedProgress = progress?.coerceIn(0f, 1f)
    val isActiveLoading = isLoading
    val resolvedContainerColor = if (enabled) containerColor else disabledContainerColor
    val resolvedContentColor = if (enabled) contentColor else disabledContentColor

    val infiniteTransition = rememberInfiniteTransition(label = "borderTransition")
    val animatedFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = fullCycleMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "borderSweep",
    )

    var isCompleting by remember { mutableStateOf(false) }
    val completionSegment = remember { Animatable(initialValue = 0f) }
    val determinateProgress = remember { Animatable(initialValue = 0f) }
    val borderAlpha = remember { Animatable(initialValue = if (isActiveLoading) 1f else 0f) }
    val indicatorColorAnim = remember {
        Animatable(
            initialValue = indicatorColor,
            typeConverter = ColorVectorConverter,
        )
    }
    var previousActiveLoading by remember { mutableStateOf(isActiveLoading) }
    var lastProgressValue by remember { mutableStateOf(clampedProgress) }
    var completionRequest by remember { mutableStateOf<CompletionParams?>(null) }
    var completionWasDeterminate by remember { mutableStateOf(false) }

    LaunchedEffect(clampedProgress) {
        clampedProgress ?: return@LaunchedEffect
        Log.d(
            TAG,
            "[$semanticsTag] progress retarget: ${determinateProgress.value} -> $clampedProgress",
        )
        determinateProgress.animateTo(
            targetValue = clampedProgress,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    LaunchedEffect(isActiveLoading, clampedProgress, indicatorColor, baseSegmentFraction) {
        val wasActive = previousActiveLoading
        val previousProgress = lastProgressValue
        previousActiveLoading = isActiveLoading
        lastProgressValue = clampedProgress
        Log.d(
            TAG,
            "[$semanticsTag] state effect: wasActive=$wasActive isActiveLoading=$isActiveLoading " +
                "previousProgress=$previousProgress clampedProgress=$clampedProgress isCompleting=$isCompleting",
        )

        if (isActiveLoading) {
            completionRequest = null
            if (isCompleting) {
                completionSegment.stop()
                isCompleting = false
            }
            indicatorColorAnim.stop()
            indicatorColorAnim.snapTo(indicatorColor)
            if (!wasActive || borderAlpha.value < 1f) {
                borderAlpha.stop()
                borderAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 180, easing = LinearEasing),
                )
            }
        } else {
            if (wasActive) {
                val initialFraction = if (previousProgress == null) {
                    baseSegmentFraction
                } else {
                    determinateProgress.value
                }.coerceIn(0f, 1f)
                Log.d(
                    TAG,
                    "[$semanticsTag] loading ended -> requesting completion sweep " +
                        "from initialFraction=$initialFraction wasDeterminate=${previousProgress != null}",
                )
                completionRequest = CompletionParams(
                    initialFraction = initialFraction,
                    wasDeterminate = previousProgress != null,
                )
            } else if (!isCompleting) {
                indicatorColorAnim.stop()
                indicatorColorAnim.snapTo(indicatorColor)
                if (borderAlpha.value != 0f) {
                    borderAlpha.stop()
                    borderAlpha.snapTo(0f)
                }
            }
        }
    }

    LaunchedEffect(completionRequest, trackEndColor, completionSweepMillis, indicatorColor, endingAnimation) {
        val request = completionRequest ?: return@LaunchedEffect

        isCompleting = true
        completionWasDeterminate = request.wasDeterminate
        Log.d(
            TAG,
            "[$semanticsTag] completion started: initialFraction=${request.initialFraction} " +
                "wasDeterminate=${request.wasDeterminate}",
        )

        completionSegment.stop()
        completionSegment.snapTo(request.initialFraction)
        borderAlpha.stop()
        borderAlpha.snapTo(1f)

        val sweepDuration = completionSweepMillis.coerceAtLeast(0f).roundToInt().coerceAtLeast(1)

        try {
            if (request.initialFraction < 1f && sweepDuration > 0) {
                completionSegment.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = sweepDuration,
                        easing = LinearEasing,
                    ),
                )
            } else {
                completionSegment.snapTo(1f)
            }
            Log.d(TAG, "[$semanticsTag] completion sweep reached full border")

            if (indicatorColorAnim.value != trackEndColor) {
                indicatorColorAnim.animateTo(
                    targetValue = trackEndColor,
                    animationSpec = tween(durationMillis = 300, easing = LinearEasing),
                )
            }

            when (endingAnimation) {
                EndingAnimation.None -> {
                    borderAlpha.snapTo(0f)
                }
                is EndingAnimation.Constant -> {
                    val delayMillis = endingAnimation.duration.coerceAtLeast(0f).roundToInt()
                    if (delayMillis > 0) {
                        delay(delayMillis.toLong())
                    }
                    borderAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
                    )
                }
                is EndingAnimation.Pulse -> {
                    val beatCount = endingAnimation.beats.coerceAtLeast(0)
                    val beatDuration = endingAnimation.beatDuration.coerceAtLeast(0f)
                    val halfDuration = (beatDuration / 2f).coerceAtLeast(1f)
                    val delayBetween = endingAnimation.delayBetweenBeats.coerceAtLeast(0f)
                    if (delayBetween > 0f) {
                        delay(delayBetween.roundToInt().toLong())
                    }
                    repeat(beatCount) { beatIndex ->
                        borderAlpha.animateTo(
                            targetValue = 0.7f,
                            animationSpec = tween(
                                durationMillis = halfDuration.roundToInt(),
                                easing = LinearEasing,
                            ),
                        )
                        borderAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = halfDuration.roundToInt(),
                                easing = LinearEasing,
                            ),
                        )
                        if (delayBetween > 0f && beatIndex != beatCount - 1) {
                            delay(delayBetween.roundToInt().toLong())
                        }
                    }
                    if (delayBetween > 0f) {
                        delay(delayBetween.roundToInt().toLong())
                    }
                    borderAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
                    )
                }
            }
        } finally {
            completionSegment.snapTo(0f)
            if (!borderAlpha.isRunning) {
                borderAlpha.snapTo(0f)
            }
            indicatorColorAnim.stop()
            indicatorColorAnim.snapTo(indicatorColor)
            completionWasDeterminate = false
            isCompleting = false
            completionRequest = null
            Log.d(TAG, "[$semanticsTag] completion finished, back to idle")
        }
    }

    val showLoadingText = isActiveLoading || isCompleting || borderAlpha.value > 0.01f

    val progressFractionValue = when {
        isCompleting -> completionSegment.value
        clampedProgress != null -> determinateProgress.value
        else -> baseSegmentFraction
    }.coerceIn(0f, 1f)

    val progressRangeInfo = if (clampedProgress != null || isCompleting) {
        ProgressBarRangeInfo(progressFractionValue, 0f..1f)
    } else {
        ProgressBarRangeInfo.Indeterminate
    }

    val textStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val horizontalPadding = remember(layoutDirection) {
        ButtonDefaults.ContentPadding.calculateLeftPadding(layoutDirection) +
            ButtonDefaults.ContentPadding.calculateRightPadding(layoutDirection)
    }
    val idleTextWidthPx = remember(text, textStyle) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = textStyle,
        ).size.width.toFloat()
    }
    val loadingTextWidthPx = remember(loadingText, textStyle) {
        textMeasurer.measure(
            text = AnnotatedString(loadingText),
            style = textStyle,
        ).size.width.toFloat()
    }
    val targetWidth = remember(showLoadingText, idleTextWidthPx, loadingTextWidthPx, horizontalPadding, density) {
        val contentWidthPx = if (showLoadingText) loadingTextWidthPx else idleTextWidthPx
        val contentWidthDp = with(density) { contentWidthPx.toDp() }
        (contentWidthDp + horizontalPadding).coerceAtLeast(ButtonDefaults.MinWidth)
    }
    val animatedWidthPx by animateFloatAsState(
        targetValue = with(density) { targetWidth.toPx() },
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "buttonWidth",
    )
    val animatedWidth = with(density) { animatedWidthPx.toDp() }

    Surface(
        onClick = {
            if (isCompleting && ignoreClicksDuringClosingAnimation) return@Surface
            onClick()
        },
        enabled = enabled,
        shape = shape,
        color = resolvedContainerColor,
        contentColor = resolvedContentColor,
        modifier = modifier
            .width(animatedWidth)
            .semantics {
                testTag = semanticsTag
                progressFractionSemantics = progressFractionValue
                borderAlphaSemantics = borderAlpha.value
                indicatorColorSemantics = indicatorColorAnim.value
                isCompletingSemantics = isCompleting
                progressStartFractionSemantics = progressStartFraction(
                    isCompleting = isCompleting,
                    completionWasDeterminate = completionWasDeterminate,
                    hasDeterminateProgress = clampedProgress != null,
                    determinateProgressAnimation = determinateProgressAnimation,
                    spinnerFraction = animatedFraction,
                )
                progressBarRangeInfo = progressRangeInfo
            }
            .animateContentSize(
                animationSpec = tween(durationMillis = 250, easing = LinearEasing),
                alignment = Alignment.Center,
            ),
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(
                    minWidth = ButtonDefaults.MinWidth,
                    minHeight = ButtonDefaults.MinHeight,
                )
                .drawWithContent {
                drawContent()

                val strokeWidthPx = strokeWidth.toPx()
                if (size.width <= 0f || size.height <= 0f) {
                    return@drawWithContent
                }

                val borderOutline = shape.createOutline(size, layoutDirection, this)
                if (borderOutline !is Outline.Rounded) {
                    return@drawWithContent
                }
                val borderPath = Path().apply {
                    addRoundRect(
                        roundRect = borderOutline.roundRect,
                        direction = Path.Direction.Clockwise,
                    )
                }

                val pathMeasure = PathMeasure().apply { setPath(borderPath, true) }
                val pathLength = pathMeasure.length
                if (pathLength <= 0f) return@drawWithContent

                val alpha = borderAlpha.value
                if (alpha <= 0f) return@drawWithContent

                val shouldRender = isActiveLoading || isCompleting || alpha > 0f
                if (!shouldRender) return@drawWithContent

                val activeSegmentFraction = progressFractionValue
                if (activeSegmentFraction <= 0f) return@drawWithContent

                val normalizedSpinnerFraction = run {
                    val normalized = animatedFraction % 1f
                    if (normalized < 0f) normalized + 1f else normalized
                }
                val normalizedStartFraction = progressStartFraction(
                    isCompleting = isCompleting,
                    completionWasDeterminate = completionWasDeterminate,
                    hasDeterminateProgress = clampedProgress != null,
                    determinateProgressAnimation = determinateProgressAnimation,
                    spinnerFraction = normalizedSpinnerFraction,
                )
                val startDistance = normalizedStartFraction * pathLength
                val endDistance = startDistance + activeSegmentFraction * pathLength

                val currentTrackColor = trackColor
                val currentIndicatorColor = indicatorColorAnim.value

                drawPath(
                    path = borderPath,
                    color = currentTrackColor.copy(alpha = currentTrackColor.alpha * alpha),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt),
                )

                val indicatorPath = Path()
                pathMeasure.getSegment(
                    startDistance,
                    endDistance.coerceAtMost(pathLength),
                    indicatorPath,
                    true,
                )
                if (endDistance > pathLength) {
                    val wrapPath = Path()
                    pathMeasure.getSegment(0f, endDistance - pathLength, wrapPath, true)
                    indicatorPath.addPath(wrapPath)
                }

                drawPath(
                    path = indicatorPath,
                    color = currentIndicatorColor.copy(alpha = currentIndicatorColor.alpha * alpha),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                )
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showLoadingText) loadingText else text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** Defines how a determinate progress arc moves around the button border. */
enum class DeterminateProgressAnimation {
    /** The progress arc begins at the same fixed point on the border. */
    Static,

    /** The progress arc rotates continuously while its length follows the current progress. */
    Rotating,
}

private fun progressStartFraction(
    isCompleting: Boolean,
    completionWasDeterminate: Boolean,
    hasDeterminateProgress: Boolean,
    determinateProgressAnimation: DeterminateProgressAnimation,
    spinnerFraction: Float,
): Float {
    if (
        isCompleting &&
        completionWasDeterminate &&
        determinateProgressAnimation == DeterminateProgressAnimation.Static
    ) {
        return 0f
    }
    if (hasDeterminateProgress && determinateProgressAnimation == DeterminateProgressAnimation.Static) return 0f
    return spinnerFraction % 1f
}

private data class CompletionParams(
    val initialFraction: Float,
    val wasDeterminate: Boolean,
)

private val ColorVectorConverter = TwoWayConverter<Color, AnimationVector4D>(
    convertToVector = { color ->
        AnimationVector4D(color.red, color.green, color.blue, color.alpha)
    },
    convertFromVector = { vector ->
        Color(vector.v1, vector.v2, vector.v3, vector.v4)
    },
)
