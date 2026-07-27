package com.letstrack.app.ui.home

import android.graphics.Typeface
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.compose.legend.verticalLegend
import com.patrykandpatrick.vico.compose.legend.verticalLegendItem
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.style.currentChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.composed.plus
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PieChart(
    categorySpending: List<CategorySpending>,
    modifier: Modifier = Modifier
) {
    // Note: Vico doesn't have a native pie chart
    // We'll create a simple column chart for now showing category comparison
    // Or use the list view which is already implemented in HomeScreen
    
    val chartEntryModel = remember(categorySpending) {
        if (categorySpending.isEmpty()) {
            null
        } else {
            entryModelOf(
                categorySpending.mapIndexed { index, spending ->
                    FloatEntry(
                        x = index.toFloat(),
                        y = spending.totalAmount.toFloat()
                    )
                }
            )
        }
    }

    if (chartEntryModel != null) {
        ProvideChartStyle {
            Chart(
                chart = columnChart(
                    columns = categorySpending.map { spending ->
                        LineComponent(
                            color = parseColor(spending.category.color).toArgb(),
                            thicknessDp = 16f,
                            shape = Shapes.roundedCornerShape(topLeftPercent = 40, topRightPercent = 40)
                        )
                    }
                ),
                model = chartEntryModel,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        categorySpending.getOrNull(value.toInt())?.category?.name?.take(3) ?: ""
                    }
                ),
                modifier = modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

@Composable
fun BarChart(
    dailySpending: List<DailySpending>,
    modifier: Modifier = Modifier
) {
    val chartEntryModel = remember(dailySpending) {
        if (dailySpending.isEmpty()) {
            null
        } else {
            entryModelOf(
                dailySpending.mapIndexed { index, spending ->
                    FloatEntry(
                        x = index.toFloat(),
                        y = spending.amount.toFloat()
                    )
                }
            )
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    if (chartEntryModel != null) {
        ProvideChartStyle {
            Chart(
                chart = columnChart(
                    columns = listOf(
                        LineComponent(
                            color = Color(0xFFF44336).toArgb(),
                            thicknessDp = 12f,
                            shape = Shapes.roundedCornerShape(topLeftPercent = 40, topRightPercent = 40)
                        )
                    )
                ),
                model = chartEntryModel,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        dailySpending.getOrNull(value.toInt())?.let { spending ->
                            dateFormatter.format(Date(spending.date))
                        } ?: ""
                    },
                    itemPlacer = remember {
                        AxisItemPlacer.Horizontal.default(
                            spacing = 1,
                            addExtremeLabelPadding = true
                        )
                    }
                ),
                modifier = modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }
    }
}

@Composable
fun LineChart(
    balanceOverTime: List<BalancePoint>,
    modifier: Modifier = Modifier
) {
    val chartEntryModel = remember(balanceOverTime) {
        if (balanceOverTime.isEmpty()) {
            null
        } else {
            entryModelOf(
                balanceOverTime.mapIndexed { index, point ->
                    FloatEntry(
                        x = index.toFloat(),
                        y = point.balance.toFloat()
                    )
                }
            )
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    if (chartEntryModel != null) {
        ProvideChartStyle {
            Chart(
                chart = lineChart(),
                model = chartEntryModel,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        balanceOverTime.getOrNull(value.toInt())?.let { point ->
                            dateFormatter.format(Date(point.date))
                        } ?: ""
                    },
                    itemPlacer = remember {
                        AxisItemPlacer.Horizontal.default(
                            spacing = 2,
                            addExtremeLabelPadding = true
                        )
                    }
                ),
                modifier = modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }
    }
}

data class BalancePoint(
    val date: Long,
    val balance: Double
)
