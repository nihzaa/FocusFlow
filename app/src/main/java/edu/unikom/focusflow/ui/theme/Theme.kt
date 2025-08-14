package edu.unikom.focusflow.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark theme color scheme (menggunakan warna yang sudah ada)
private val DarkColorScheme = darkColorScheme(
    primary = LightGreen,     // Light Green for primary in dark mode
    secondary = Yellow,       // Yellow for secondary
    tertiary = Teal,          // Teal for tertiary
    background = Dark100,     // Dark background
    surface = Dark200,        // Dark surface
    onPrimary = Light100,     // Light text on primary
    onSecondary = Dark100,    // Dark text on yellow secondary
    onTertiary = Light100,    // Light text on tertiary
    onBackground = Light100,  // Light text on background
    onSurface = Light100,     // Light text on surface
    surfaceVariant = Dark200, // Dark surface variant
    onSurfaceVariant = Light200, // Light text on surface variant
    outline = Light200.copy(alpha = 0.5f)
)

// Light theme color scheme (menggunakan warna yang sudah ada)
private val LightColorScheme = lightColorScheme(
    primary = DarkGreen,      // Dark Green for primary in light mode
    secondary = Yellow,       // Yellow for secondary
    tertiary = Teal,          // Teal for tertiary
    background = Light100,    // Light background
    surface = Light200,       // Light surface
    onPrimary = Light100,     // Light text on primary
    onSecondary = Dark100,    // Dark text on yellow secondary
    onTertiary = Light100,    // Light text on tertiary
    onBackground = Dark100,   // Dark text on background
    onSurface = Dark100,      // Dark text on surface
    surfaceVariant = Light200, // Light surface variant
    onSurfaceVariant = Dark200, // Dark text on surface variant
    outline = Dark200.copy(alpha = 0.3f)
)

@Composable
fun FocusFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable for consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Composable khusus untuk menggunakan dengan ThemeManager
@Composable
fun FocusFlowThemeWithManager(
    themeManager: ThemeManager,
    content: @Composable () -> Unit
) {
    val isDarkMode by themeManager.isDarkMode

    FocusFlowTheme(
        darkTheme = isDarkMode,
        dynamicColor = false,
        content = content
    )
}