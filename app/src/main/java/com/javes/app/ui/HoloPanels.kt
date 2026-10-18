package com.javes.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javes.app.ui.theme.JarvisCyan
import com.javes.app.ui.theme.JarvisElectric
import com.javes.app.ui.theme.JarvisGlow
import com.javes.app.ui.theme.JarvisPanel
import com.javes.app.ui.theme.JarvisWarning
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Painel translúcido holográfico reutilizável. */
@Composable
fun HoloPanel(
    visible: Boolean,
    modifier: Modifier = Modifier,
    borderColor: Color = JarvisCyan,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 4 },
        exit = fadeOut() + slideOutVertically { it / 4 },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                JarvisPanel.copy(alpha = 0.85f),
                                JarvisPanel.copy(alpha = 0.55f)
                            )
                        ),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                }
                .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            content()
        }
    }
}

@Composable
fun TimeHoloPanel(visible: Boolean) {
    var now by remember { mutableStateOf(LocalTime.now(ZoneId.systemDefault())) }
    var date by remember { mutableStateOf(LocalDate.now(ZoneId.systemDefault())) }
    LaunchedEffect(visible) {
        while (visible) {
            now = LocalTime.now(ZoneId.systemDefault())
            date = LocalDate.now(ZoneId.systemDefault())
            delay(1000)
        }
    }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR")) }

    HoloPanel(visible = visible, borderColor = JarvisElectric) {
        Text(
            text = "SISTEMA · TEMPO",
            color = JarvisElectric.copy(alpha = 0.8f),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = now.format(timeFmt),
            color = JarvisCyan,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light
        )
        Text(
            text = date.format(dateFmt).replaceFirstChar { it.uppercase() },
            color = JarvisCyan.copy(alpha = 0.75f),
            fontSize = 13.sp
        )
    }
}

@Composable
fun MusicHoloPanel(visible: Boolean, statusLine: String?) {
    HoloPanel(visible = visible, borderColor = JarvisGlow) {
        Text(
            text = "MÍDIA · FABTUNE",
            color = JarvisGlow.copy(alpha = 0.85f),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = statusLine ?: "Controle de mídia ativo",
            color = JarvisCyan,
            fontSize = 14.sp
        )
        Text(
            text = "Play / pausa / faixa via sessão de mídia Android",
            color = JarvisCyan.copy(alpha = 0.55f),
            fontSize = 11.sp
        )
    }
}

@Composable
fun StatusHoloPanel(
    visible: Boolean,
    title: String,
    message: String,
    borderColor: Color = JarvisWarning
) {
    HoloPanel(visible = visible, borderColor = borderColor) {
        Text(
            text = title,
            color = borderColor.copy(alpha = 0.9f),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            color = JarvisCyan,
            fontSize = 13.sp
        )
    }
}

@Composable
fun SystemIndicators(
    state: JarvisState,
    online: Boolean,
    alwaysOn: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.label,
            color = when (state) {
                JarvisState.ERROR -> com.javes.app.ui.theme.JarvisError
                JarvisState.OFFLINE -> JarvisWarning
                JarvisState.LISTENING, JarvisState.SPEAKING -> JarvisGlow
                else -> JarvisCyan
            },
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IndicatorChip(label = if (online) "REDE" else "SEM REDE", active = online)
            IndicatorChip(label = "24H", active = alwaysOn)
            IndicatorChip(label = "MIC", active = state == JarvisState.LISTENING)
        }
    }
}

@Composable
private fun IndicatorChip(label: String, active: Boolean) {
    Text(
        text = label,
        color = if (active) JarvisGlow else JarvisCyan.copy(alpha = 0.35f),
        fontSize = 9.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold
    )
}
