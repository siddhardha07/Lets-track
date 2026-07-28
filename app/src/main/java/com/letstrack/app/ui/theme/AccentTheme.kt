package com.letstrack.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Each accent is a 4-stop tonal ramp (deepest -> lightest) rather than a single flat color, so
 * branded-surface tinting, primary/container roles, and gradients on progress elements can all
 * be derived from one consistent source per accent instead of hand-picked values per role.
 * `deep`/`darkAccent` skew dark, `coreAccent` is the saturated "brand moment" color used on
 * buttons/FAB/selected states in both themes, `light` anchors light-theme containers.
 */
enum class AccentTheme(
    val label: String,
    val deep: Color,
    val darkAccent: Color,
    val coreAccent: Color,
    val light: Color
) {
    GREEN("Green", Color(0xFF091413), Color(0xFF285A48), Color(0xFF408A71), Color(0xFFB0E4CC)),
    BLUE("Blue", Color(0xFF0A2647), Color(0xFF144272), Color(0xFF205295), Color(0xFF2C74B3)),
    TEAL("Teal", Color(0xFF222831), Color(0xFF31363F), Color(0xFF76ABAE), Color(0xFFEEEEEE)),
    VIOLET("Violet", Color(0xFF070F2B), Color(0xFF1B1A55), Color(0xFF535C91), Color(0xFF9290C3)),
    NAVY("Navy", Color(0xFF27374D), Color(0xFF526D82), Color(0xFF9DB2BF), Color(0xFFDDE6ED)),
    NAVY_BROWN("Navy & Brown", Color(0xFF2C3639), Color(0xFF3F4E4F), Color(0xFFA27B5C), Color(0xFFDCD7C9))
}

/**
 * WCAG-luminance-based "on" color so every accent gets readable text regardless of how light
 * or dark its own core tone happens to be -- e.g. Navy's core (#9DB2BF) and Teal's (#76ABAE)
 * are both mid-lightness, where a hardcoded white-or-black guess would be wrong for some.
 * Neither pole is pure per the dark-UI guidance this palette is based on.
 */
fun contrastingOnColor(background: Color): Color =
    if (background.luminance() > 0.42f) Color(0xFF14141F) else Color(0xFFF2F2F7)

/** The gradient used on progress-y elements (chart bars, the floating add button) for a subtle
 * "glow" instead of a flat fill -- built from whatever the active accent's primary resolves to,
 * so callers don't need the raw [AccentTheme] in scope, just the current ColorScheme. */
fun accentGradient(primary: Color, vertical: Boolean = true): Brush {
    val colors = listOf(primary, primary.darken(0.28f))
    return if (vertical) Brush.verticalGradient(colors) else Brush.horizontalGradient(colors)
}

/**
 * Hero-card treatment: dark theme fades a near-black base into the accent at low alpha (a glow
 * against black, since the card sits directly on a near-black page background); light theme
 * fades white into the accent's light stop -- both read as "glassy" because the stops are
 * translucent rather than flat opaque fills.
 */
@Composable
fun heroCardBrush(primary: Color): Brush {
    return if (isDarkTheme()) {
        Brush.linearGradient(listOf(Color(0xFF0A0A10).copy(alpha = 0.92f), primary.copy(alpha = 0.38f)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.88f), primary.copy(alpha = 0.30f)))
    }
}

/** A thin light-catching edge to sell the "glass" read, brighter in light theme where a white
 * card edge is otherwise invisible against a white page. */
@Composable
fun heroCardBorderColor(): Color =
    if (isDarkTheme()) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.65f)

/** Same idea as [heroCardBrush] but much lower-intensity, for dense lists where every row
 * getting a loud gradient would fight for attention instead of reading as one cohesive list. */
@Composable
fun listItemCardBrush(primary: Color): Brush {
    return if (isDarkTheme()) {
        Brush.linearGradient(listOf(Color(0xFF0F0F16).copy(alpha = 0.9f), primary.copy(alpha = 0.16f)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.94f), primary.copy(alpha = 0.14f)))
    }
}
