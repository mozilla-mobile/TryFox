package org.mozilla.tryfox.ui.composables

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

const val PROGRESS_BUTTON_TAG = "IndeterminateProgressButton"

val ProgressFractionKey = SemanticsPropertyKey<Float>("ProgressFraction")
var SemanticsPropertyReceiver.progressFractionSemantics by ProgressFractionKey

val BorderAlphaKey = SemanticsPropertyKey<Float>("BorderAlpha")
var SemanticsPropertyReceiver.borderAlphaSemantics by BorderAlphaKey

val IndicatorColorKey = SemanticsPropertyKey<Color>("IndicatorColor")
var SemanticsPropertyReceiver.indicatorColorSemantics by IndicatorColorKey

val IsCompletingKey = SemanticsPropertyKey<Boolean>("IsCompleting")
var SemanticsPropertyReceiver.isCompletingSemantics by IsCompletingKey

val ProgressStartFractionKey = SemanticsPropertyKey<Float>("ProgressStartFraction")
var SemanticsPropertyReceiver.progressStartFractionSemantics by ProgressStartFractionKey
