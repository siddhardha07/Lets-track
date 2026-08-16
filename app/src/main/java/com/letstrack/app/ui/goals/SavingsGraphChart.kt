package com.letstrack.app.ui.goals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.letstrack.app.domain.goal.GoalProgress
import com.letstrack.app.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** No-decimals currency, just for this compact ring label -- formatCurrency's ₹X,XXX.XX is too
 * long to fit a 130dp-wide column on one line without silently clipping mid-string. */
private fun formatCurrencyCompact(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }.format(amount)

/**
 * The "Savings Graph" toggle section's chart -- progress rings, not the Budget graph's vertical
 * thermometer bars, so the two visually read as different things even though they're both
 * "spent/saved vs a limit" underneath. Reuses the same Canvas arc-drawing approach
 * CategoryDonutChart already uses for Home's category breakdown, just one small ring per goal
 * instead of one multi-segment ring for all categories -- "how much of this goal is done" reads
 * naturally as a filling circle.
 *
 * Capped at 3 and laid out by how many there actually are, rather than a fixed-size scrollable
 * row regardless of count -- with just one goal, a small ring off to one side in a mostly-empty
 * card looked sparse, so a lone goal gets centered and drawn noticeably bigger instead.
 */
@Composable
fun SavingsGraphChart(goals: List<GoalProgress>, modifier: Modifier = Modifier) {
    if (goals.isEmpty()) return
    val displayed = goals.take(3)
    val ringSize = if (displayed.size == 1) 140.dp else 88.dp
    val columnWidth = if (displayed.size == 1) 200.dp else 132.dp

    Row(
        modifier = modifier.fillMaxWidth().height(if (displayed.size == 1) 220.dp else 190.dp),
        horizontalArrangement = if (displayed.size == 1) Arrangement.Center else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        displayed.forEach { progress ->
            SavingsRingCard(
                progress = progress,
                ringSize = ringSize,
                modifier = Modifier.width(columnWidth)
            )
        }
    }
}

@Composable
private fun SavingsRingCard(progress: GoalProgress, ringSize: Dp, modifier: Modifier = Modifier) {
    val goal = progress.goal
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val isBig = ringSize > 100.dp

    val sweepProgress = remember(goal.id) { Animatable(0f) }
    LaunchedEffect(progress.percent, goal.id) {
        sweepProgress.snapTo(0f)
        sweepProgress.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }

    val pctLabel = min(999, (progress.percent * 100).roundToInt())

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = (if (isBig) 13 else 9).dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)

                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                val sweep = 360f * progress.percent.coerceAtMost(1f) * sweepProgress.value
                if (sweep > 0f) {
                    drawArc(
                        color = primary,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                text = if (progress.isFullyFunded) "🎉" else "$pctLabel%",
                style = if (isBig) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = goal.name,
            style = if (isBig) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${formatCurrencyCompact(progress.savedAmount)} / ${formatCurrencyCompact(goal.targetAmount)}",
            style = if (isBig) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
