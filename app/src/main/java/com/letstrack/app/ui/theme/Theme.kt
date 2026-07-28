package com.letstrack.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

/**
 * Builds a full ColorScheme from an [AccentTheme]'s 4-stop ramp instead of hand-picked values
 * per role. Neutral surfaces get the accent's core tone mixed in at a low fraction ("branded
 * dark/light surfaces") per the dark-UI guidance this palette follows -- a flat neutral grey
 * everywhere would lose the accent identity outside of primary-colored elements.
 */
private fun buildColorScheme(accent: AccentTheme, darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = accent.coreAccent,
            onPrimary = contrastingOnColor(accent.coreAccent),
            primaryContainer = accent.darkAccent,
            onPrimaryContainer = accent.light,
            secondary = accent.darkAccent,
            onSecondary = contrastingOnColor(accent.darkAccent),
            tertiary = BrandTertiary,
            onTertiary = Color(0xFF3D2900),
            background = NeutralBackgroundDark.mixWith(accent.coreAccent, 0.05f),
            onBackground = NeutralOnSurfaceDark,
            surface = NeutralSurfaceDark.mixWith(accent.coreAccent, 0.07f),
            onSurface = NeutralOnSurfaceDark,
            surfaceVariant = NeutralSurfaceVariantDark.mixWith(accent.coreAccent, 0.10f),
            onSurfaceVariant = NeutralOnSurfaceVariantDark,
            outline = NeutralOutlineDark,
            error = ErrorDark,
            onError = Color(0xFF3D0A0A)
        )
    } else {
        lightColorScheme(
            primary = accent.coreAccent,
            onPrimary = contrastingOnColor(accent.coreAccent),
            primaryContainer = accent.light,
            onPrimaryContainer = contrastingOnColor(accent.light),
            secondary = accent.darkAccent,
            onSecondary = contrastingOnColor(accent.darkAccent),
            tertiary = BrandTertiary,
            onTertiary = Color.White,
            background = NeutralBackgroundLight.mixWith(accent.coreAccent, 0.03f),
            onBackground = NeutralOnSurfaceLight,
            surface = NeutralSurfaceLight.mixWith(accent.coreAccent, 0.05f),
            onSurface = NeutralOnSurfaceLight,
            surfaceVariant = NeutralSurfaceVariantLight.mixWith(accent.coreAccent, 0.08f),
            onSurfaceVariant = NeutralOnSurfaceVariantLight,
            outline = NeutralOutlineLight,
            error = ErrorLight,
            onError = Color.White
        )
    }
}

@Composable
fun LetsTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentTheme: AccentTheme = AccentTheme.GREEN,
    // Dynamic color (Material You) is off by default so the chosen accent always renders
    // consistently. Kept as a parameter in case a future toggle opts back in.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> buildColorScheme(accentTheme, darkTheme)
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
        shapes = LetsTrackShapes,
        content = content
    )
}
