package org.mozilla.tryfox.ui.composables

sealed class EndingAnimation {
    data object None : EndingAnimation()

    data class Pulse(
        val beatDuration: Float,
        val beats: Int,
        val delayBetweenBeats: Float,
    ) : EndingAnimation()

    data class Constant(
        val duration: Float,
    ) : EndingAnimation()
}
