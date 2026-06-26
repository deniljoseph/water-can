package com.watercantracker.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode { LIGHT, DARK, SYSTEM }

/** Three dark mode variants */
enum class DarkModeVariant {
    DARK,       // Standard Material dark (dark grey surfaces)
    AMOLED,     // True black — saves battery on OLED screens
    DARK_GRAY   // Slightly lighter dark — easier on eyes
}

/** Selectable accent colors */
enum class AccentColor(
    val displayName: String,
    val primary: Color,
    val primaryDark: Color,
    val container: Color
) {
    TEAL(   "Teal",   Color(0xFF0B5D6E), Color(0xFF7FD8DE), Color(0xFFE3F7F6)),
    BLUE(   "Blue",   Color(0xFF1565C0), Color(0xFF90CAF9), Color(0xFFE3F2FD)),
    GREEN(  "Green",  Color(0xFF2E7D32), Color(0xFF81C784), Color(0xFFE8F5E9)),
    ORANGE( "Orange", Color(0xFFE65100), Color(0xFFFFCC80), Color(0xFFFFF3E0)),
    PURPLE( "Purple", Color(0xFF6A1B9A), Color(0xFFCE93D8), Color(0xFFF3E5F5)),
    RED(    "Red",    Color(0xFFC62828), Color(0xFFEF9A9A), Color(0xFFFFEBEE))
}

private fun lightColors(accent: AccentColor) = lightColorScheme(
    primary          = accent.primary,
    onPrimary        = Color.White,
    primaryContainer = accent.container,
    onPrimaryContainer = accent.primary,
    secondary        = accent.primary.copy(alpha = 0.75f),
    onSecondary      = Color.White,
    tertiary         = AmberAccent,
    onTertiary       = Color.White,
    tertiaryContainer = AmberAccentLight,
    background       = CloudWhite,
    onBackground     = InkDark,
    surface          = Color.White,
    onSurface        = InkDark,
    surfaceVariant   = MistGray,
    onSurfaceVariant = SlateGray,
    error            = ErrorRed,
    onError          = Color.White
)

private fun darkColors(accent: AccentColor, variant: DarkModeVariant): ColorScheme {
    val surface = when (variant) {
        DarkModeVariant.AMOLED    -> Color(0xFF000000)
        DarkModeVariant.DARK_GRAY -> Color(0xFF2C2C2C)
        DarkModeVariant.DARK      -> Color(0xFF121212)
    }
    val background = when (variant) {
        DarkModeVariant.AMOLED    -> Color(0xFF000000)
        DarkModeVariant.DARK_GRAY -> Color(0xFF1E1E1E)
        DarkModeVariant.DARK      -> Color(0xFF0F1212)
    }
    return darkColorScheme(
        primary          = accent.primaryDark,
        onPrimary        = Color(0xFF003545),
        primaryContainer = accent.primary,
        onPrimaryContainer = Color.White,
        secondary        = accent.primaryDark.copy(alpha = 0.8f),
        onSecondary      = Color(0xFF003545),
        tertiary         = Color(0xFFFFB877),
        onTertiary       = Color(0xFF3F2200),
        background       = background,
        onBackground     = NightOnSurface,
        surface          = surface,
        onSurface        = NightOnSurface,
        surfaceVariant   = surface.copy(alpha = 0.6f).compositeOver(Color(0xFF1E3333)),
        onSurfaceVariant = Color(0xFFB6CBCB),
        error            = Color(0xFFFFB4AB),
        onError          = Color(0xFF690005)
    )
}

private fun Color.compositeOver(background: Color): Color {
    val a = this.alpha
    return Color(
        red   = this.red   * a + background.red   * (1 - a),
        green = this.green * a + background.green * (1 - a),
        blue  = this.blue  * a + background.blue  * (1 - a),
        alpha = 1f
    )
}

@Composable
fun WaterCanTrackerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkModeVariant: DarkModeVariant = DarkModeVariant.DARK,
    accentColor: AccentColor = AccentColor.TEAL,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.LIGHT  -> false
        AppThemeMode.DARK   -> true
        AppThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColors(accentColor, darkModeVariant)
        else      -> lightColors(accentColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = WaterCanTypography,
        shapes      = WaterCanShapes,
        content     = content
    )
}
