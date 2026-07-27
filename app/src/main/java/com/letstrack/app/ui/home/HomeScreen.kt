package com.letstrack.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.letstrack.app.ui.components.DateRange
import com.letstrack.app.ui.components.DateRangePicker
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val keyMetrics by viewModel.keyMetrics.collectAsState()
    val categorySpending by viewModel.categorySpending.collectAsState()
    val dailySpending by viewModel.dailySpending.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val transactionType by viewModel.transactionType.collectAsState()
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, "Filters")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Time Filter Chips
            item {
                TimeFilterRow(
                    selectedFilter = timeFilter,
                    onFilterChange = { filter ->
                        if (filter == TimeFilter.CUSTOM) {
                            showDatePicker = true
                        } else {
                            viewModel.setTimeFilter(filter)
                        }
                    }
                )
            }
            
            // Key Metrics Cards
            item {
                KeyMetricsSection(metrics = keyMetrics)
            }
            
            // Pie Chart - Category Spending
            if (categorySpending.isNotEmpty()) {
                item {
                    Text(
                        "Spending by Category",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    CategorySpendingCard(categorySpending = categorySpending)
                }
            }
            
            // Bar Chart - Daily Trend
            if (dailySpending.isNotEmpty()) {
                item {
                    Text(
                        "Spending Trend",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    DailySpendingChart(dailySpending = dailySpending)
                }
            }
            
            // Smart Insights
            item {
                SmartInsightsCard(
                    categorySpending = categorySpending,
                    metrics = keyMetrics
                )
            }
            
            // Balance Over Time Line Chart
            item {
                Text(
                    "Balance Over Time",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                BalanceOverTimeCard(viewModel = viewModel)
            }
            
            // Recent Transactions
            if (recentTransactions.isNotEmpty()) {
                item {
                    Text(
                        "Recent Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(recentTransactions.take(5)) { expense ->
                    RecentTransactionItem(
                        expense = expense,
                        category = viewModel.getCategoryById(expense.categoryId)
                    )
                }
            }
        }
    }
    
    if (showDatePicker) {
        DateRangePicker(
            selectedRange = viewModel.customDateRange.collectAsState().value,
            onRangeSelected = { range ->
                viewModel.setCustomDateRange(range)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
    
    if (showFilterDialog) {
        FilterDialog(
            categories = categories,
            selectedCategories = selectedCategories,
            transactionType = transactionType,
            onCategoryToggle = { categoryId ->
                viewModel.toggleCategoryFilter(categoryId)
            },
            onClearCategories = {
                viewModel.clearCategoryFilters()
            },
            onTransactionTypeChange = { type ->
                viewModel.setTransactionType(type)
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
fun TimeFilterRow(
    selectedFilter: TimeFilter,
    onFilterChange: (TimeFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFilter == TimeFilter.THIS_MONTH,
                onClick = { onFilterChange(TimeFilter.THIS_MONTH) },
                label = { Text("This Month") }
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == TimeFilter.THIS_WEEK,
                onClick = { onFilterChange(TimeFilter.THIS_WEEK) },
                label = { Text("This Week") }
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == TimeFilter.LAST_7_DAYS,
                onClick = { onFilterChange(TimeFilter.LAST_7_DAYS) },
                label = { Text("Last 7 Days") }
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == TimeFilter.LAST_30_DAYS,
                onClick = { onFilterChange(TimeFilter.LAST_30_DAYS) },
                label = { Text("Last 30 Days") }
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == TimeFilter.CUSTOM,
                onClick = { onFilterChange(TimeFilter.CUSTOM) },
                label = { Text(if (selectedFilter == TimeFilter.CUSTOM) "Custom" else "⋯") }
            )
        }
    }
}

@Composable
fun KeyMetricsSection(metrics: KeyMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Income",
            amount = metrics.totalIncome,
            icon = Icons.Default.TrendingUp,
            color = Color(0xFF4CAF50),
            changePercent = metrics.incomeVsPreviousPeriod
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Expenses",
            amount = metrics.totalExpenses,
            icon = Icons.Default.TrendingDown,
            color = Color(0xFFF44336),
            changePercent = metrics.expensesVsPreviousPeriod
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    MetricCard(
        modifier = Modifier.fillMaxWidth(),
        title = "Net Balance",
        amount = metrics.netBalance,
        icon = Icons.Default.AccountBalance,
        color = if (metrics.netBalance >= 0) Color(0xFF2196F3) else Color(0xFFFF9800),
        changePercent = metrics.balanceVsPreviousPeriod
    )
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: Double,
    icon: ImageVector,
    color: Color,
    changePercent: Float
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                formatCurrency(amount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (changePercent != 0f) {
                Text(
                    "${if (changePercent > 0) "+" else ""}${String.format("%.1f", changePercent)}% vs previous",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (changePercent > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun CategorySpendingCard(categorySpending: List<CategorySpending>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vico Bar Chart showing category comparison
            PieChart(
                categorySpending = categorySpending,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Category list with bars
            categorySpending.forEach { spending ->
                CategorySpendingItem(spending)
            }
        }
    }
}

@Composable
fun CategorySpendingItem(spending: CategorySpending) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(parseColor(spending.category.color))
                )
                Text(
                    spending.category.name,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "${String.format("%.1f", spending.percentage)}% · ${formatCurrency(spending.totalAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = spending.percentage / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = parseColor(spending.category.color),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun DailySpendingChart(dailySpending: List<DailySpending>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Vico Bar Chart showing daily spending
            BarChart(
                dailySpending = dailySpending,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun BalanceOverTimeCard(viewModel: HomeViewModel) {
    val balanceOverTime by viewModel.balanceOverTime.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (balanceOverTime.isNotEmpty()) {
                LineChart(
                    balanceOverTime = balanceOverTime,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    "No balance data available",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SmartInsightsCard(
    categorySpending: List<CategorySpending>,
    metrics: KeyMetrics
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Smart Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            if (categorySpending.isNotEmpty()) {
                val topCategory = categorySpending.first()
                Text(
                    "• ${topCategory.category.name} is your top spending category (${String.format("%.1f", topCategory.percentage)}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            if (metrics.totalExpenses > metrics.totalIncome) {
                Text(
                    "• You're spending more than you're earning this period",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else if (metrics.netBalance > 0) {
                Text(
                    "• Great job! You saved ${formatCurrency(metrics.netBalance)} this period",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun RecentTransactionItem(
    expense: com.letstrack.app.domain.model.Expense,
    category: com.letstrack.app.domain.model.Category?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (category != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(parseColor(category.color).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            category.icon ?: "💰",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Column {
                    Text(
                        expense.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        formatDate(expense.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                formatCurrency(kotlin.math.abs(expense.amount)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (expense.amount < 0) Color(0xFFF44336) else Color(0xFF4CAF50)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    categories: List<com.letstrack.app.domain.model.Category>,
    selectedCategories: Set<Long>,
    transactionType: String?,
    onCategoryToggle: (Long) -> Unit,
    onClearCategories: () -> Unit,
    onTransactionTypeChange: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Transaction Type",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = transactionType == null,
                            onClick = { onTransactionTypeChange(null) },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = transactionType == "expense",
                            onClick = { onTransactionTypeChange("expense") },
                            label = { Text("Expenses") }
                        )
                        FilterChip(
                            selected = transactionType == "income",
                            onClick = { onTransactionTypeChange("income") },
                            label = { Text("Income") }
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Categories",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedCategories.isNotEmpty()) {
                            TextButton(onClick = onClearCategories) {
                                Text("Clear")
                            }
                        }
                    }
                }
                
                items(categories) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategoryToggle(category.id) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(parseColor(category.color).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(category.icon ?: "💰")
                            }
                            Text(category.name)
                        }
                        Checkbox(
                            checked = category.id in selectedCategories,
                            onCheckedChange = { onCategoryToggle(category.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

// Utility functions for formatting
fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount)
}

fun formatDate(dateMillis: Long): String {
    val localDateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(dateMillis),
        ZoneId.systemDefault()
    )
    val formatter = DateTimeFormatter.ofPattern("MMM dd, hh:mm a")
    return localDateTime.format(formatter)
}

fun parseColor(colorString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (e: Exception) {
        Color.Gray
    }
}
