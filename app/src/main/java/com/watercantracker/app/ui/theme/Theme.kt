package com.watercantracker.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = TealDeep,
    onPrimary = Color.White,
    primaryContainer = AquaPale,
    onPrimaryContainer = TealDeep,
    secondary = TealMid,
    onSecondary = Color.White,
    secondaryContainer = AquaLight,
    onSecondaryContainer = InkDark,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    tertiaryContainer = AmberAccentLight,
    onTertiaryContainer = Color(0xFF5A2E00),
    error = ErrorRed,
    onError = Color.White,
    background = CloudWhite,
    onBackground = InkDark,
    surface = Color.White,
    onSurface = InkDark,
    surfaceVariant = MistGray,
    onSurfaceVariant = SlateGray,
    outline = Color(0xFFB7C2C2)
)

private val DarkColors = darkColorScheme(
    primary = AquaLight,
    onPrimary = NightTeal,
    primaryContainer = TealMid,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9CD7DB),
    onSecondary = NightTeal,
    secondaryContainer = NightSurfaceVariant,
    onSecondaryContainer = NightOnSurface,
    tertiary = Color(0xFFFFB877),
    onTertiary = Color(0xFF3F2200),
    tertiaryContainer = Color(0xFF5A3A12),
    onTertiaryContainer = AmberAccentLight,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = NightSurface,
    onBackground = NightOnSurface,
    surface = Color(0xFF132627),
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = Color(0xFFB6CBCB),
    outline = Color(0xFF5C7373)
)

enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

@Composable
fun WaterCanTrackerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false, // kept off by default to preserve the brand palette
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WaterCanTypography,
        shapes = WaterCanShapes,
        content = content
    )
}
