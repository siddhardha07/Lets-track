package com.letstrack.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.letstrack.app.ui.theme.ShapeFull
import com.letstrack.app.ui.theme.Spacing

/** Pill segmented control (e.g. the Home period selector) with an animated sliding indicator. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(ShapeFull)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        val segmentWidth = maxWidth / options.size
        val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
        val offset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(220),
            label = "segment-offset"
        )
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(width = segmentWidth, height = 40.dp)
                .padding(3.dp)
                .clip(ShapeFull)
                .background(MaterialTheme.colorScheme.surface)
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { option ->
                val isSelected = option == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "segment-text-color"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Accent-colored selectable chip used for category filters. */
@Composable
fun CategoryFilterChip(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(ShapeFull)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}
