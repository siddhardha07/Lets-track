package com.letstrack.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.letstrack.app.ui.theme.Elevation
import com.letstrack.app.ui.theme.ShapeMd
import com.letstrack.app.ui.theme.Spacing
import com.letstrack.app.ui.theme.isDarkTheme

enum class AppCardVariant { Default, Elevated, Outlined, Tinted }

/**
 * Standard card surface shared by every screen (shape, elevation, padding), replacing
 * the ad-hoc `Card(...)` blocks that were previously duplicated per screen.
 *
 * Passing [backgroundBrush] switches to a gradient "glass" card (hero balance cards, list
 * rows) instead of a flat [variant] fill -- Surface's automatic content-color derivation only
 * works for flat colors, so that path provides its own light/dark content color instead.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    variant: AppCardVariant = AppCardVariant.Default,
    tint: Color = MaterialTheme.colorScheme.primaryContainer,
    backgroundBrush: Brush? = null,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    if (backgroundBrush != null) {
        val contentColor = if (isDarkTheme()) Color(0xFFF2F2F7) else Color(0xFF15151F)
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = modifier
                    .clip(ShapeMd)
                    .background(backgroundBrush)
                    .then(
                        if (borderColor != null) Modifier.border(1.dp, borderColor, ShapeMd) else Modifier
                    )
                    .padding(contentPadding),
                content = content
            )
        }
        return
    }

    val containerColor = if (variant == AppCardVariant.Tinted) tint else MaterialTheme.colorScheme.surface
    val elevation = if (variant == AppCardVariant.Elevated) Elevation.level2 else Elevation.level0
    val border = if (variant == AppCardVariant.Outlined) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    } else null

    Surface(
        modifier = modifier,
        shape = ShapeMd,
        color = containerColor,
        tonalElevation = elevation,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
