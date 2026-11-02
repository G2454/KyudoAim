package com.kyudo.aimhelper.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sumi-ink ground, washi-paper text, one vermilion accent (same palette as the canvas design).
val Ink = Color(0xFF121417)
val Surface1 = Color(0xFF1A1D21)
val Line = Color(0xFF2C3036)
val Paper = Color(0xFFF2EFE8)
val Muted = Color(0xFF9AA0A6)
val Soft = Color(0xFFC9C5BC)
val Vermilion = Color(0xFFF06A45)
val Teal = Color(0xFF7FC8A9)
val OkText = Color(0xFF9EE0C2)
val OkBg = Color(0xFF1C2A24)
val WarnText = Color(0xFFFF9A7A)
val WarnBg = Color(0xFF2A1C17)
val WarnLine = Color(0xFF5A2E20)
val Bamboo = Color(0xFFC9A46A)
val MatoBlack = Color(0xFF0A0A0A)
val Grass = Color(0xFF2B3A2E)
val Dim = Color(0xFF3A3F46)

@Composable
fun KyudoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Vermilion,
            onPrimary = Ink,
            secondary = Teal,
            onSecondary = Ink,
            background = Ink,
            onBackground = Paper,
            surface = Surface1,
            onSurface = Paper,
            surfaceVariant = Surface1,
            onSurfaceVariant = Muted,
            outline = Line,
        ),
        content = content,
    )
}
