package com.letstrack.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.letstrack.app.domain.model.Category
import kotlin.math.abs
import kotlin.math.min

/**
 * Whether the theme *actually rendering right now* is dark -- not whether the OS is in dark
 * mode. `isSystemInDarkTheme()` is wrong here: when a user explicitly overrides ThemeMode to
 * Light while their system is in dark mode (or vice versa), it would disagree with the real
 * ColorScheme in effect and semantic colors would pick the wrong light/dark variant.
 */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

// Brand primary/container colors are no longer static -- they're derived per-[AccentTheme]
// in Theme.kt so the whole palette (including branded-surface tinting and gradients) can be
// swapped from Settings. See AccentTheme.kt.
val BrandTertiary = Color(0xFFF5A524)     // warm amber accent, used for AI/insight highlights

/** Linear-interpolates toward [other] -- used for branded-surface tinting (mix a hue into a
 * neutral surface at low fraction) and for building a second gradient stop from one color. */
fun Color.mixWith(other: Color, fraction: Float): Color = lerp(this, other, fraction)

fun Color.darken(amount: Float): Color = mixWith(Color.Black, amount)

// ---- Neutrals ----
val NeutralBackgroundLight = Color(0xFFF7F7FB)
val NeutralSurfaceLight = Color(0xFFFFFFFF)
val NeutralSurfaceVariantLight = Color(0xFFEEF0F6)
val NeutralOutlineLight = Color(0xFFE2E4ED)
val NeutralOnSurfaceLight = Color(0xFF15151F)
val NeutralOnSurfaceVariantLight = Color(0xFF6B6F80)

val NeutralBackgroundDark = Color(0xFF0E0E16)
val NeutralSurfaceDark = Color(0xFF1A1A24)
val NeutralSurfaceVariantDark = Color(0xFF23232F)
val NeutralOutlineDark = Color(0xFF2E2F3C)
val NeutralOnSurfaceDark = Color(0xFFF2F2F7)
val NeutralOnSurfaceVariantDark = Color(0xFFA3A6B8)

// ---- Semantic (income / expense / review) ----
val IncomeLight = Color(0xFF15A362)
val IncomeDark = Color(0xFF4ADE80)
val ExpenseLight = Color(0xFFE1493D)
val ExpenseDark = Color(0xFFFF7A6E)
val NeedsReviewLight = Color(0xFFB8790C)
val NeedsReviewDark = Color(0xFFF5A524)

val ErrorLight = Color(0xFFDC3545)
val ErrorDark = Color(0xFFFF6B6B)

// Semantic colors aren't part of Material3's ColorScheme slots, so expose them as
// theme-aware composables instead (mirrors the light/dark switch LetsTrackTheme already does).
@Composable
fun incomeColor(): Color = if (isDarkTheme()) IncomeDark else IncomeLight

@Composable
fun expenseColor(): Color = if (isDarkTheme()) ExpenseDark else ExpenseLight

@Composable
fun needsReviewColor(): Color = if (isDarkTheme()) NeedsReviewDark else NeedsReviewLight

// ---- Categorical palette (used for category chips / avatars / chart series) ----
val CategoryBlue = Color(0xFF3B82F6)
val CategoryViolet = Color(0xFF8B5CF6)
val CategoryPink = Color(0xFFEC4899)
val CategoryOrange = Color(0xFFF97316)
val CategoryAmber = Color(0xFFF5A524)
val CategoryGreen = Color(0xFF15A362)
val CategoryTeal = Color(0xFF14B8A6)
val CategoryRed = Color(0xFFE1493D)
val CategoryIndigo = Color(0xFF5D5FEF)
val CategorySlate = Color(0xFF64748B)

val CategoricalPalette = listOf(
    CategoryBlue, CategoryViolet, CategoryPink, CategoryOrange, CategoryAmber,
    CategoryGreen, CategoryTeal, CategoryRed, CategoryIndigo, CategorySlate
)

/**
 * Categories store a free-text hex string chosen ad-hoc over time, which produces a visually
 * inconsistent set of colors across the app. Snapping each stored hex to the nearest color in
 * [CategoricalPalette] (by hue) keeps every category visually consistent with the design system
 * while leaving the stored value untouched.
 */
fun categoricalAccent(hex: String): Color {
    val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        ?: return CategorySlate
    val targetHue = parsed.toHue()
    return CategoricalPalette.minByOrNull { candidate ->
        hueDistance(targetHue, candidate.toHue())
    } ?: CategorySlate
}

/**
 * Assigns each category a distinct color by its rank among [categories] (sorted by id), instead
 * of snapping its own stored hex to the nearest palette hue like [categoricalAccent] does --
 * hue-snapping let two categories with similar ad-hoc colors collapse onto the same palette entry
 * (e.g. two purple-ish categories both landing on violet).
 *
 * The first [CategoricalPalette] entries (10) go to the hand-picked, on-brand colors. Past that,
 * silently wrapping back to CategoricalPalette[0] would just recreate the exact same collision
 * for a different reason -- so instead it generates evenly-spaced hues around the color wheel for
 * however many extra categories exist, which stays collision-free no matter how many categories
 * a real account accumulates over time, not just up to 10.
 *
 * Used for the charts/legends/chips where multiple categories are directly compared side by side
 * (donut + legend, budget bars, filter chips) -- category icon backgrounds elsewhere still use
 * [categoricalAccent] on the raw stored hex, since those aren't shown side by side the same way.
 */
fun categoricalAccentMap(categories: List<Category>): Map<Long, Color> {
    val distinctById = categories.distinctBy { it.id }.sortedBy { it.id }
    val overflowCount = (distinctById.size - CategoricalPalette.size).coerceAtLeast(0)
    return distinctById.mapIndexed { index, category ->
        val color = if (index < CategoricalPalette.size) {
            CategoricalPalette[index]
        } else {
            val hue = 360f * (index - CategoricalPalette.size) / overflowCount
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.85f)))
        }
        category.id to color
    }.toMap()
}

private fun Color.toHue(): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgbInt(), hsv)
    return hsv[0]
}

private fun Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )

private fun hueDistance(a: Float, b: Float): Float {
    val diff = abs(a - b)
    return min(diff, 360f - diff)
}
