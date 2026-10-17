package com.javes.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.javes.app.ui.theme.JarvisBlue
import com.javes.app.ui.theme.JarvisCyan
import com.javes.app.ui.theme.JarvisElectric
import com.javes.app.ui.theme.JarvisError
import com.javes.app.ui.theme.JarvisGlow
import com.javes.app.ui.theme.JarvisWarning
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Núcleo holográfico central: anéis concêntricos, ticks, arcos rotativos
 * e núcleo luminoso. Intensidade e velocidade reagem ao [JarvisState] real.
 */
@Composable
fun JarvisCore(
    state: JarvisState,
    micLevel: Float,
    modifier: Modifier = Modifier
) {
    val t by rememberAnimationTime()

    val intensity = when (state) {
        JarvisState.IDLE -> 0.35f
        JarvisState.LISTENING -> 0.55f + 0.45f * micLevel.coerceIn(0f, 1f)
        JarvisState.PROCESSING -> 0.75f
        JarvisState.SPEAKING -> 0.65f
        JarvisState.MUSIC -> 0.70f
        JarvisState.TIME -> 0.50f
        JarvisState.LOCATION -> 0.55f
        JarvisState.ERROR -> 0.40f
        JarvisState.OFFLINE -> 0.25f
    }

    val speed = when (state) {
        JarvisState.IDLE -> 0.35f
        JarvisState.LISTENING -> 0.9f + micLevel * 0.6f
        JarvisState.PROCESSING -> 1.6f
        JarvisState.SPEAKING -> 1.1f
        JarvisState.MUSIC -> 1.2f
        JarvisState.TIME -> 0.4f
        JarvisState.LOCATION -> 0.7f
        JarvisState.ERROR, JarvisState.OFFLINE -> 0.2f
    }

    val accent = when (state) {
        JarvisState.ERROR -> JarvisError
        JarvisState.OFFLINE -> JarvisWarning
        JarvisState.MUSIC -> JarvisGlow
        JarvisState.TIME -> JarvisElectric
        JarvisState.LOCATION -> JarvisCyan
        else -> JarvisCyan
    }

    val ripple = when (state) {
        JarvisState.LISTENING, JarvisState.SPEAKING, JarvisState.MUSIC -> 1f
        JarvisState.PROCESSING -> 0.6f
        else -> 0.15f
    }

    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = minOf(size.width, size.height) * 0.42f
        val a = t * 60f * speed

        // Halo externo suave
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = 0.06f + 0.18f * intensity),
                    Color.Transparent
                ),
                center = c,
                radius = base * 1.35f
            ),
            radius = base * 1.35f,
            center = c
        )

        // Grid radial decorativo (linhas cibernéticas leves)
        for (i in 0 until 12) {
            val rad = Math.toRadians((a * 0.15f + i * 30f).toDouble())
            val cosR = cos(rad).toFloat()
            val sinR = sin(rad).toFloat()
            drawLine(
                color = accent.copy(alpha = 0.08f + 0.05f * intensity),
                start = Offset(c.x + cosR * base * 0.22f, c.y + sinR * base * 0.22f),
                end = Offset(c.x + cosR * base * 0.95f, c.y + sinR * base * 0.95f),
                strokeWidth = 1f
            )
        }

        // Anéis concêntricos estáticos
        for (i in 1..5) {
            val r = base * (0.35f + i * 0.12f)
            drawCircle(
                color = accent.copy(alpha = 0.12f + 0.08f * intensity * (1f - i / 6f)),
                radius = r,
                center = c,
                style = Stroke(width = 1.2f)
            )
        }

        // Ticks na borda
        for (i in 0 until 72) {
            val rad = Math.toRadians((a * 0.25f + i * 5f).toDouble())
            val major = i % 6 == 0
            val outer = base * if (major) 1.00f else 0.97f
            val inner = base * if (major) 0.90f else 0.93f
            drawLine(
                color = accent.copy(alpha = if (major) 0.75f else 0.28f),
                start = Offset(c.x + cos(rad).toFloat() * inner, c.y + sin(rad).toFloat() * inner),
                end = Offset(c.x + cos(rad).toFloat() * outer, c.y + sin(rad).toFloat() * outer),
                strokeWidth = if (major) 2.5f else 1.2f
            )
        }

        // Arcos rotativos (3 camadas)
        data class Ring(val radiusFactor: Float, val speedFactor: Float, val sweep: Float, val width: Float, val color: Color)
        val rings = listOf(
            Ring(0.55f, 1.0f, 48f, 3.5f, accent),
            Ring(0.72f, -0.7f, 36f, 2.5f, JarvisBlue),
            Ring(0.88f, 0.45f, 28f, 2f, JarvisElectric)
        )
        rings.forEach { ring ->
            val r = base * ring.radiusFactor
            for (k in 0 until 3) {
                drawArc(
                    color = ring.color.copy(alpha = 0.30f + 0.50f * intensity),
                    startAngle = a * ring.speedFactor + k * 120f,
                    sweepAngle = ring.sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = ring.width, cap = StrokeCap.Round)
                )
            }
        }

        // Ondas de expansão (escuta / fala / música)
        val pulse = 1f + 0.04f * sin(t * (2f + 5f * intensity)).toFloat()
        val coreRadius = base * 0.28f * pulse
        if (ripple > 0.05f) {
            for (i in 0 until 4) {
                val phase = ((t * 0.55f + i / 4f) % 1f)
                val r = coreRadius + (base * 0.55f - coreRadius) * phase
                drawCircle(
                    color = accent.copy(alpha = (1f - phase) * 0.50f * ripple),
                    radius = r,
                    center = c,
                    style = Stroke(width = 2.5f)
                )
            }
        }

        // Anel fino interno girando no sentido contrário
        rotate(degrees = -a * 1.4f, pivot = c) {
            drawArc(
                color = JarvisGlow.copy(alpha = 0.55f * intensity),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(c.x - coreRadius * 1.35f, c.y - coreRadius * 1.35f),
                size = Size(coreRadius * 2.7f, coreRadius * 2.7f),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )
        }

        // Núcleo holográfico
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    accent,
                    JarvisBlue.copy(alpha = 0.8f),
                    Color.Transparent
                ),
                center = c,
                radius = coreRadius * 1.4f
            ),
            radius = coreRadius,
            center = c
        )

        // Ponto central
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = coreRadius * 0.18f,
            center = c
        )
    }
}
