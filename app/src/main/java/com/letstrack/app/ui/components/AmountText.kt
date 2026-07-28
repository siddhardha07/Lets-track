package com.letstrack.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.letstrack.app.ui.theme.expenseColor
import com.letstrack.app.ui.theme.incomeColor
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Signed currency text with an animated count-up. `isIncome`/`isExpense` never rely on color
 * alone: an up/down arrow accompanies the semantic color for colorblind-safe reading.
 */
@Composable
fun AmountText(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    showSign: Boolean = true,
    showIcon: Boolean = false,
    neutralColor: Color = LocalContentColor.current,
    // Overrides the semantic income color for positive amounts only -- used for the one or two
    // hero balance numbers per screen so they reflect the active accent theme, while list rows
    // keep the default green/red (color + sign + optional arrow) for fast, colorblind-safe
    // scanning of many transactions at once.
    positiveColor: Color? = null
) {
    val animatedAmount by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(600),
        label = "amount-count-up"
    )
    val color = when {
        !showSign -> neutralColor
        amount > 0 -> positiveColor ?: incomeColor()
        amount < 0 -> expenseColor()
        else -> neutralColor
    }
    val formatted = formatSignedCurrency(animatedAmount.toDouble(), showSign)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showIcon && showSign && amount != 0.0) {
            Icon(
                imageVector = if (amount > 0) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (amount > 0) "Income" else "Expense",
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(text = formatted, style = style, color = color)
    }
}

private fun formatSignedCurrency(amount: Double, showSign: Boolean): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val magnitude = formatter.format(abs(amount))
    if (!showSign) return magnitude
    return when {
        amount > 0 -> "+$magnitude"
        amount < 0 -> "-$magnitude"
        else -> magnitude
    }
}
