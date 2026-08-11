package com.letstrack.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Wraps a balance figure so it's masked by default and only shown for a few seconds after the
 * user explicitly taps to reveal it - a shoulder-surfing/screen-share precaution for the one
 * number on screen that actually matters (net worth), rather than something you have to
 * remember to turn on. Every recomposition of this composable (e.g. reopening the screen)
 * starts hidden again; nothing is persisted.
 */
@Composable
fun HideableBalance(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    showSign: Boolean = true,
    showIcon: Boolean = false,
    neutralColor: Color = LocalContentColor.current,
    positiveColor: Color? = null,
    autoHideMillis: Long = 6_000L
) {
    var revealed by remember { mutableStateOf(false) }

    // Ticks back to hidden `autoHideMillis` after each reveal. Restarts (via the `revealed` key)
    // if the user re-taps while it's already showing, rather than compounding timers.
    androidx.compose.runtime.LaunchedEffect(revealed) {
        if (revealed) {
            delay(autoHideMillis)
            revealed = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { revealed = !revealed }
    ) {
        if (revealed) {
            AmountText(
                amount = amount,
                style = style,
                showSign = showSign,
                showIcon = showIcon,
                neutralColor = neutralColor,
                positiveColor = positiveColor
            )
        } else {
            Text(text = "₹ •••••", style = style, color = neutralColor)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (revealed) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
            contentDescription = if (revealed) "Hide balance" else "Show balance",
            tint = neutralColor.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }
}
