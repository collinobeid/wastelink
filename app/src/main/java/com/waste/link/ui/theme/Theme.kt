package com.waste.link.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PrimaryBlue = Color(0xFF2B9FE8)
private val SecondaryCyan = Color(0xFF57C7F7)
private val TertiaryBlue = Color(0xFF1F8FE0)
private val ErrorRed = Color(0xFFDC2626)
private val BackgroundLight = Color(0xFFF1F5F1)
private val SurfaceLight = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = SecondaryCyan,
    tertiary = TertiaryBlue,
    error = ErrorRed,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onError = Color.White,
    onBackground = Color(0xFF16181C),
    onSurface = Color(0xFF16181C)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = SecondaryCyan,
    tertiary = TertiaryBlue,
    error = ErrorRed,
    background = Color(0xFF121412),
    surface = Color(0xFF1A1C19),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onError = Color.White,
    onBackground = Color(0xFFE2E3DF),
    onSurface = Color(0xFFE2E3DF)
)

@Composable
fun WasteLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled for explicit brand colors
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
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}