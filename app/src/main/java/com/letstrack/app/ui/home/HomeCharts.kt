package com.letstrack.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.letstrack.app.ui.theme.ShapeFull
import com.letstrack.app.ui.theme.accentGradient
import com.letstrack.app.ui.theme.mixWith
import com.letstrack.app.ui.theme.Spacing
import com.letstrack.app.ui.theme.categoricalAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Real animated donut for category spending. Vico (the charting library used elsewhere)
 * has no native pie/donut primitive, so this draws one directly with Canvas: one arc per
 * category sized by its share of total spend, with an animated sweep-in and a center total.
 */
@Composable
fun CategoryDonutChart(
    categorySpending: List<CategorySpending>,
    accentMap: Map<Long, Color>,
    centerLabel: String,
    modifier: Modifier = Modifier,
    selectedCategoryIds: Set<Long> = emptySet()
) {
    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(categorySpending) {
        sweepProgress.snapTo(0f)
        sweepProgress.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    // Purely a visualization -- picking categories to focus on happens through the dedicated
    // picker button/sheet only, so tapping the ring or a legend row directly does nothing.
    Box(modifier = modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 22.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (categorySpending.isEmpty()) return@Canvas

            var startAngle = -90f
            categorySpending.forEach { spending ->
                val sweep = (spending.percentage / 100f) * 360f * sweepProgress.value
                val isDimmed = selectedCategoryIds.isNotEmpty() && spending.category.id !in selectedCategoryIds
                if (sweep > 0f) {
                    val accent = accentMap[spending.category.id] ?: categoricalAccent(spending.category.color)
                    drawArc(
                        color = accent.copy(alpha = if (isDimmed) 0.3f else 1f),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                startAngle += (spending.percentage / 100f) * 360f
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Total spent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Custom-drawn spending trend bar chart (capsule bars, dashed gridlines, a highlighted peak
 * day with a floating value badge and guideline). Reacts to whatever period is selected
 * upstream since it just renders whatever [dailySpending] it's given.
 */
@Composable
fun SpendingTrendChart(
    dailySpending: List<DailySpending>,
    labelStyle: ChartLabelStyle = ChartLabelStyle.WEEKDAY,
    modifier: Modifier = Modifier
) {
    if (dailySpending.isEmpty()) return

    val maxAmount = dailySpending.maxOf { it.amount }
    val axisStep = niceAxisStep(maxAmount)
    val axisTop = axisStep * 4
    val peakIndex = dailySpending.indices.maxByOrNull { dailySpending[it].amount } ?: 0

    // Which bar's value is shown in the floating badge -- defaults to the peak day, but the
    // user can tap any other bar to inspect it. Resets to the peak whenever the data changes.
    var selectedIndex by remember(dailySpending) { mutableStateOf(peakIndex) }
    val selectedAmount = dailySpending[selectedIndex].amount
    val selectedFraction = (selectedAmount / axisTop).toFloat().coerceIn(0f, 1f)

    val progress = remember { Animatable(0f) }
    LaunchedEffect(dailySpending) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }

    val peakColor = MaterialTheme.colorScheme.primary
    val mutedBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val gridColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelFormatter = remember(labelStyle) {
        val pattern = when (labelStyle) {
            ChartLabelStyle.WEEKDAY -> "EEE"
            ChartLabelStyle.DAY_OF_MONTH -> "d"
            ChartLabelStyle.WEEK_NUMBER -> "d" // unused -- WEEK_NUMBER labels come from bar index instead
            ChartLabelStyle.MONTH_NAME -> "MMM"
        }
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    fun labelFor(index: Int, spending: DailySpending): String =
        if (labelStyle == ChartLabelStyle.WEEK_NUMBER) "Week ${index + 1}" else labelFormatter.format(Date(spending.date))

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Column(
                modifier = Modifier.width(44.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                for (tick in 4 downTo 0) {
                    Text(
                        text = if (tick == 0) "₹0" else formatAxisLabel(axisStep * tick),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val barSlotWidth = maxWidth / dailySpending.size
                val badgeWidth = 72.dp

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val levels = 4
                    for (i in 0..levels) {
                        val y = size.height * (1f - i.toFloat() / levels)
                        drawLine(
                            color = gridColor.copy(alpha = 0.25f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                    }
                    val selectedY = size.height * (1f - selectedFraction * progress.value)
                    drawLine(
                        color = peakColor,
                        start = Offset(0f, selectedY),
                        end = Offset(size.width, selectedY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dailySpending.forEachIndexed { index, spending ->
                        val fraction = ((spending.amount / axisTop).toFloat().coerceIn(0f, 1f) * progress.value)
                            .coerceAtLeast(0.015f)
                        val isSelected = index == selectedIndex
                        // Fixed-width capsule regardless of slot width -- a CircleShape clip on a
                        // box far wider than tall (e.g. a single bar filling the row) inscribes an
                        // oval in the full bounds instead of a pill, so width is capped here and
                        // rounding uses a percent shape (relative to the smaller side) instead.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedIndex = index },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(fraction)
                                    .width(28.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSelected) accentGradient(peakColor) else SolidColor(mutedBarColor))
                            )
                        }
                    }
                }

                val badgeCenterX = barSlotWidth * selectedIndex + barSlotWidth / 2
                val badgeY = (maxHeight * (1f - selectedFraction * progress.value) - 32.dp).coerceAtLeast(0.dp)
                Box(
                    modifier = Modifier
                        .offset(x = (badgeCenterX - badgeWidth / 2).coerceIn(0.dp, maxWidth - badgeWidth), y = badgeY)
                        .width(badgeWidth)
                        .clip(ShapeFull)
                        .background(accentGradient(peakColor, vertical = false))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatCurrency(selectedAmount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        Row(modifier = Modifier.fillMaxWidth().padding(start = 52.dp)) {
            dailySpending.forEachIndexed { index, spending ->
                Text(
                    text = labelFor(index, spending),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedIndex = index },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == selectedIndex) peakColor else labelColor,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Budget graph: a "thermometer" bar per row -- each bar's filled portion is spend, the faint
 * track above it is remaining headroom up to the budget, so both "how much spent" and "how much
 * more can I spend" read directly off one bar instead of needing a separate number. A bar that
 * hits the top (spent >= budget) turns red instead of overflowing the track. Built from
 * [BudgetChartData], which picks which rows to show (Overall + top spenders by default, or
 * exactly the categories the user filtered to) -- this composable just draws whatever list it's
 * handed.
 */
@Composable
fun BudgetBarsChart(
    data: BudgetChartData,
    accentMap: Map<Long, Color>,
    modifier: Modifier = Modifier
) {
    if (data.rows.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth().height(200.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.rows.forEach { row ->
            BudgetBarColumn(row = row, accentMap = accentMap, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun BudgetBarColumn(row: BudgetBarRow, accentMap: Map<Long, Color>, modifier: Modifier = Modifier) {
    // Same rank-based map the Category donut/legend/filter chips use, so a category's color
    // matches everywhere on Home, not just within this one card.
    val accent = row.category?.let { accentMap[it.id] ?: categoricalAccent(it.color) } ?: MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = if (row.isOverBudget) errorColor else accent

    // Scaled to whichever is taller, spend or the limit -- no extra headroom padding on top of
    // that (there used to be a 1.15x pad, which left a permanent unfilled dark cap at the very
    // top of every bar, even the tallest one). Now whichever value is tallest always reaches
    // the full height of the bar.
    val axisMax = remember(row) { maxOf(row.budgetAmount, row.spentAmount, 1.0) }
    val limitFraction = (row.budgetAmount / axisMax).toFloat().coerceIn(0f, 1f)

    val progress = remember(row) { Animatable(0f) }
    LaunchedEffect(row) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }
    val spentFraction = (row.spentAmount / axisMax).toFloat().coerceIn(0f, 1f) * progress.value

    // "Glassy" treatment (translucent gradient stops, same language as heroCardBrush elsewhere)
    // for both segments, not a flat opaque fill.
    val accentGlassBrush = Brush.verticalGradient(
        listOf(accent.mixWith(Color.White, 0.15f).copy(alpha = 0.9f), accent.mixWith(Color.Black, 0.3f).copy(alpha = 0.75f))
    )
    val errorGlassBrush = Brush.verticalGradient(
        listOf(errorColor.mixWith(Color.White, 0.3f).copy(alpha = 0.9f), errorColor.mixWith(Color.Black, 0.35f).copy(alpha = 0.75f))
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = "Limit ${formatCurrency(row.budgetAmount)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(Spacing.xs))
        Canvas(
            modifier = Modifier
                .width(40.dp)
                .weight(1f)
        ) {
            val corner = size.width / 2f
            val trackShape = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner, corner)))
            }
            clipPath(trackShape) {
                drawRect(color = trackColor)
                // Below the limit: the category's own accent, same color language as every
                // other budget/category visual. Above it: always red, regardless of category --
                // this is the one signal that's meant to be unmissable.
                val belowLimitFraction = minOf(spentFraction, limitFraction)
                if (belowLimitFraction > 0f) {
                    drawRect(
                        brush = accentGlassBrush,
                        topLeft = Offset(0f, size.height * (1f - belowLimitFraction)),
                        size = Size(size.width, size.height * belowLimitFraction)
                    )
                }
                if (spentFraction > limitFraction) {
                    drawRect(
                        brush = errorGlassBrush,
                        topLeft = Offset(0f, size.height * (1f - spentFraction)),
                        size = Size(size.width, size.height * (spentFraction - limitFraction))
                    )
                }
            }
            // Dashed marker at exactly the budget limit's height, drawn outside the clip so it
            // isn't cut off by the track's rounded corners.
            val limitY = size.height * (1f - limitFraction)
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(0f, limitY),
                end = Offset(size.width, limitY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = formatCurrency(row.spentAmount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = barColor,
            maxLines = 1
        )
        Text(
            text = row.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}


/** Picks a "nice" round axis step (1/2/5 x a power of ten) so 4 ticks cover [maxValue]. */
private fun niceAxisStep(maxValue: Double): Double {
    if (maxValue <= 0.0) return 1.0
    val rawStep = maxValue / 4.0
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val normalized = rawStep / magnitude
    val niceNormalized = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return niceNormalized * magnitude
}

private fun formatAxisLabel(value: Double): String = "₹${value.roundToInt()}"
