package com.letstrack.app.ui.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.letstrack.app.ui.theme.CategoryBlue
import com.letstrack.app.ui.theme.Spacing

/**
 * Replaces the old list-style bottom sheet with 3 circular actions fanning out above the nav
 * bar's floating "+". This must be composed as a sibling placed AFTER the Scaffold (not inside
 * its content slot) so it draws on top of the custom bottom bar -- see MainActivity. Dismissing
 * (scrim tap or the X) only removes this overlay; it never navigates, so whatever screen was
 * showing underneath is exactly what's revealed again.
 */
@Composable
fun AddActionMenu(
    onDismiss: () -> Unit,
    onManualClick: () -> Unit,
    onPdfClick: () -> Unit,
    onCsvClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                AddActionCircle(
                    icon = Icons.Filled.Edit,
                    label = "Manual Entry",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onManualClick
                )
                AddActionCircle(
                    icon = Icons.Filled.Description,
                    label = "Import PDF",
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onPdfClick
                )
                AddActionCircle(
                    icon = Icons.Filled.TableChart,
                    label = "Import CSV",
                    color = CategoryBlue,
                    onClick = onCsvClick
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun AddActionCircle(
    icon: ImageVector,
    label: String,
    color: Color,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = contentColor)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}
