package de.carcophan.snorerec.ui.theme

import androidx.compose.ui.graphics.Color

// SnoreLab Signature Midnight Navy Palette
val SnoreLabBgDark = Color(0xFF091326)
val SnoreLabSurface = Color(0xFF11213D)
val SnoreLabSurfaceElevated = Color(0xFF182E52)
val SnoreLabSurfaceBright = Color(0xFF213D6B)
val SnoreLabBorder = Color(0xFF2A4A7F)

val SnoreLabCyan = Color(0xFF22D3EE)
val SnoreLabPrimaryBlue = Color(0xFF0EA5E9)
val SnoreLabDeepBlue = Color(0xFF0284C7)

// SnoreLab 4 Intensity Levels
val SnoreQuietGreen = Color(0xFF2ECC71)
val SnoreLightYellow = Color(0xFFF1C40F)
val SnoreLoudOrange = Color(0xFFFF9800)
val SnoreEpicRed = Color(0xFFFF3B5C)

val SnoreTextPrimary = Color(0xFFF1F5F9)
val SnoreTextSecondary = Color(0xFF94A3B8)
val SnoreTextMuted = Color(0xFF64748B)

fun snoreScoreColor(score: Int): Color = when {
    score <= 15 -> SnoreQuietGreen
    score <= 40 -> SnoreLightYellow
    score <= 80 -> SnoreLoudOrange
    else -> SnoreEpicRed
}

fun snoreScoreCategoryLabel(score: Int): String = when {
    score == 0 -> "Ruhe"
    score <= 15 -> "Leicht"
    score <= 40 -> "Mäßig"
    score <= 80 -> "Laut"
    else -> "Episch"
}
