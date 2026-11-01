package de.carcophan.snorerec.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SnoreLabDarkColorScheme = darkColorScheme(
    primary = SnoreLabCyan,
    onPrimary = Color(0xFF061527),
    primaryContainer = SnoreLabSurfaceBright,
    onPrimaryContainer = SnoreTextPrimary,
    secondary = SnoreQuietGreen,
    onSecondary = Color(0xFF042211),
    secondaryContainer = Color(0xFF133A2E),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = SnoreLightYellow,
    onTertiary = Color(0xFF281F02),
    background = SnoreLabBgDark,
    onBackground = SnoreTextPrimary,
    surface = SnoreLabBgDark,
    onSurface = SnoreTextPrimary,
    surfaceVariant = SnoreLabSurfaceElevated,
    onSurfaceVariant = SnoreTextSecondary,
    surfaceContainerLowest = Color(0xFF060D1A),
    surfaceContainerLow = SnoreLabSurface,
    surfaceContainer = SnoreLabSurface,
    surfaceContainerHigh = SnoreLabSurfaceElevated,
    surfaceContainerHighest = SnoreLabSurfaceBright,
    outline = SnoreLabBorder,
    outlineVariant = Color(0xFF1E355B),
    error = SnoreEpicRed,
    onError = Color.White
)

@Composable
fun SnoreRecTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = SnoreLabDarkColorScheme,
        typography = Typography,
        content = content
    )
}
