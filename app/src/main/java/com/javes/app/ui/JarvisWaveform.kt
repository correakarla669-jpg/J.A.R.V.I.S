package com.javes.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import com.javes.app.ui.theme.JarvisCyan
import com.javes.app.ui.theme.JarvisError
import com.javes.app.ui.theme.JarvisGlow
import com.javes.app.ui.theme.JarvisWarning
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Ondas sonoras reativas ao estado e ao nível real do microfone.
 * [level] (0..1) só influencia LISTENING; em SPEAKING/MUSIC usa animação suave.
 */
@Composable
fun JarvisWaveform(
    state: JarvisState,
    level: Float,
    modifier: Modifier = Modifier
) {
    val t by rememberAnimationTime()
    val targetAmp = when (state) {
        JarvisState.LISTENING -> 0.25f + 0.75f * level.coerceIn(0f, 1f)
        JarvisState.SPEAKING -> 0.45f
        JarvisState.MUSIC -> 0.55f
        JarvisState.PROCESSING -> 0.20f
        else -> 0.08f
    }
    val amp by animateFloatAsState(
        targetValue = targetAmp,
        animationSpec = tween(120),
        label = "waveAmp"
    )
    val color = when (state) {
        JarvisState.ERROR -> JarvisError
        JarvisState.OFFLINE -> JarvisWarning
        JarvisState.MUSIC -> JarvisGlow
        else -> JarvisCyan
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val bars = 48
        val gap = w / bars
        for (i in 0 until bars) {
            val x = i * gap + gap / 2f
            val phase = t * (2.2f + amp) + i * 0.35f
            val envelope = sin(i * PI.toFloat() / bars).let { abs(it) }
            val barH = (sin(phase) * 0.5f + 0.5f) * amp * mid * 0.95f * (0.35f + 0.65f * envelope)
            drawLine(
                color = color.copy(alpha = 0.35f + 0.55f * amp),
                start = Offset(x, mid - barH),
                end = Offset(x, mid + barH),
                strokeWidth = (gap * 0.45f).coerceAtLeast(2f),
                cap = StrokeCap.Round
            )
        }
    }
}
