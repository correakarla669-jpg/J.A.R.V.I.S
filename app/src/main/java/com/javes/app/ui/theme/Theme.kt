package com.javes.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    secondary = JarvisBlue,
    background = JarvisNavy,
    surface = JarvisNavy,
    onPrimary = JarvisBlack,
    onBackground = JarvisCyan,
    onSurface = JarvisCyan
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        content = content
    )
}
