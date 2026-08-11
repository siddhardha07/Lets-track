package com.letstrack.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.ui.components.AccentDot
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.components.AppCardVariant
import com.letstrack.app.ui.components.AmountText
import com.letstrack.app.ui.components.CategoryAvatar
import com.letstrack.app.ui.components.CategoryFilterChip
import com.letstrack.app.ui.components.DateRange
import com.letstrack.app.ui.components.DateRangePicker
import com.letstrack.app.ui.components.EmptyState
import com.letstrack.app.ui.components.HideableBalance
import com.letstrack.app.ui.components.MiniStat
import com.letstrack.app.ui.components.PrimaryButton
import com.letstrack.app.ui.components.SectionHeader
import com.letstrack.app.ui.components.SegmentedControl
import com.letstrack.app.ui.components.TertiaryButton
import com.letstrack.app.ui.theme.ShapeFull
import com.letstrack.app.ui.theme.Spacing
import com.letstrack.app.ui.theme.categoricalAccent
import com.letstrack.app.ui.theme.expenseColor
import com.letstrack.app.ui.theme.heroCardBorderColor
import com.letstrack.app.ui.theme.heroCardBrush
import com.letstrack.app.ui.theme.incomeColor
import com.letstrack.app.ui.theme.listItemCardBrush
import com.letstrack.app.ui.theme.needsReviewColor
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onSeeAllTransactions: () -> Unit = {},
    onOpenNotifications: () -> Unit = {}
) {
    val keyMetrics by viewModel.keyMetrics.collectAsState()
    val categorySpending by viewModel.categorySpending.collectAsState()
    val dailySpending by viewModel.dailySpending.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val transactionType by viewModel.transactionType.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    val selectedAccountIds by viewModel.selectedAccountIds.collectAsState()
    val filteredExpenses by viewModel.filteredExpenses.collectAsState()
    val needsReviewCount by viewModel.needsReviewCount.collectAsState()
    val chartLabelStyle by viewModel.chartLabelStyle.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Local to the donut card only -- picking categories here inspects the pie breakdown,
    // it must never touch filteredExpenses or the rest of the page (hero balance, bar chart,
    // insights, transaction list) would otherwise empty out along with it.
    var donutSelectedCategoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDonutCategoryPicker by remember { mutableStateOf(false) }
    val visibleCategorySpending = remember(categorySpending, donutSelectedCategoryIds) {
        if (donutSelectedCategoryIds.isEmpty()) {
            categorySpending
        } else {
            val subset = categorySpending.filter { it.category.id in donutSelectedCategoryIds }
            val subsetTotal = subset.sumOf { it.totalAmount }
            if (subsetTotal <= 0.0) subset else subset.map { it.copy(percentage = (it.totalAmount / subsetTotal * 100).toFloat()) }
        }
    }

    val insights = remember(filteredExpenses, categories, categorySpending, keyMetrics) {
        InsightsEngine.buildInsights(filteredExpenses, categories, categorySpending, keyMetrics)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            item { HomeHeader(needsReviewCount = needsReviewCount, onOpenNotifications = onOpenNotifications) }

            item {
                HeroBalanceCard(
                    metrics = keyMetrics,
                    timeFilter = timeFilter,
                    onTimeFilterChange = { filter -> viewModel.setTimeFilter(filter) },
                    onOpenCustomRange = { showDatePicker = true },
                    onOpenFilters = { showFilterSheet = true }
                )
            }

            if (insights.isNotEmpty()) {
                item { SectionHeader("AI Insights") }
                item { InsightsCarousel(insights) }
            }

            if (categorySpending.isNotEmpty()) {
                item { SectionHeader("Spending by Category") }
                item {
                    CategoryBreakdownCard(
                        categorySpending = visibleCategorySpending,
                        selectedCategoryIds = donutSelectedCategoryIds,
                        onOpenCategoryPicker = { showDonutCategoryPicker = true },
                        onClearSelection = { donutSelectedCategoryIds = emptySet() }
                    )
                }
            }

            if (dailySpending.isNotEmpty()) {
                item { SectionHeader("Spending Trend") }
                item {
                    val primary = MaterialTheme.colorScheme.primary
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundBrush = heroCardBrush(primary),
                        borderColor = heroCardBorderColor()
                    ) {
                        SpendingTrendChart(
                            dailySpending = dailySpending,
                            labelStyle = chartLabelStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (recentTransactions.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Recent Transactions",
                        actionLabel = "See all",
                        onActionClick = onSeeAllTransactions
                    )
                }
                items(recentTransactions, key = { it.id }) { expense ->
                    TransactionRow(expense = expense, category = viewModel.getCategoryById(expense.categoryId))
                }
            } else {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        EmptyState(
                            title = "No transactions yet",
                            subtitle = "Transactions from SMS or manual entry will show up here."
                        )
                    }
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

    if (showFilterSheet) {
        FilterBottomSheet(
            categories = categories,
            selectedCategories = selectedCategories,
            transactionType = transactionType,
            accounts = bankAccounts,
            selectedAccountIds = selectedAccountIds,
            onCategoryToggle = viewModel::toggleCategoryFilter,
            onClearCategories = viewModel::clearCategoryFilters,
            onTransactionTypeChange = viewModel::setTransactionType,
            onAccountToggle = viewModel::toggleAccountFilter,
            onClearAccounts = viewModel::clearAccountFilter,
            onDismiss = { showFilterSheet = false }
        )
    }

    if (showDonutCategoryPicker) {
        DonutCategoryPickerSheet(
            categories = categories,
            selectedCategoryIds = donutSelectedCategoryIds,
            onCategoryToggle = { id ->
                donutSelectedCategoryIds = if (id in donutSelectedCategoryIds) {
                    donutSelectedCategoryIds - id
                } else {
                    donutSelectedCategoryIds + id
                }
            },
            onClear = { donutSelectedCategoryIds = emptySet() },
            onDismiss = { showDonutCategoryPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonutCategoryPickerSheet(
    categories: List<Category>,
    selectedCategoryIds: Set<Long>,
    onCategoryToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Choose categories", style = MaterialTheme.typography.titleLarge)
                if (selectedCategoryIds.isNotEmpty()) {
                    TertiaryButton(text = "Clear", onClick = onClear)
                }
            }
            Text(
                "Pick one or more to focus the pie chart on just those categories.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRowWrap(categories = categories, selectedCategoryIds = selectedCategoryIds, onCategoryToggle = onCategoryToggle)
            PrimaryButton(text = "Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FlowRowWrap(
    categories: List<Category>,
    selectedCategoryIds: Set<Long>,
    onCategoryToggle: (Long) -> Unit
) {
    // Simple wrapping chip layout (no experimental FlowRow needed): fixed-width rows of chips.
    val rows = remember(categories) { categories.chunked(3) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        rows.forEach { rowCategories ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowCategories.forEach { category ->
                    CategoryFilterChip(
                        label = category.name,
                        accent = categoricalAccent(category.color),
                        selected = category.id in selectedCategoryIds,
                        onClick = { onCategoryToggle(category.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    modifier: Modifier = Modifier,
    needsReviewCount: Int = 0,
    onOpenNotifications: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = timeOfDayGreeting(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = "Your money, at a glance", style = MaterialTheme.typography.headlineSmall)
        }
        Box {
            IconButton(
                onClick = onOpenNotifications,
                modifier = Modifier
                    .size(44.dp)
                    .clip(ShapeFull)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Filled.NotificationsNone, contentDescription = "Notifications")
            }
            if (needsReviewCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(ShapeFull)
                        .background(needsReviewColor())
                )
            }
        }
    }
}

private fun timeOfDayGreeting(): String {
    val hour = LocalDateTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun HeroBalanceCard(
    metrics: KeyMetrics,
    timeFilter: TimeFilter,
    onTimeFilterChange: (TimeFilter) -> Unit,
    onOpenCustomRange: () -> Unit,
    onOpenFilters: () -> Unit
) {
    // LAST_30_DAYS used to sit here labeled "30D", but it produced a range that was
    // functionally indistinguishable from THIS_MONTH ("Month") most of the time, making
    // both chips redundant. Swapped for LAST_90_DAYS ("3M") to actually add a distinct range.
    val quickFilters = listOf(TimeFilter.TODAY, TimeFilter.THIS_WEEK, TimeFilter.THIS_MONTH, TimeFilter.LAST_90_DAYS, TimeFilter.THIS_YEAR)
    val primary = MaterialTheme.colorScheme.primary
    AppCard(
        backgroundBrush = heroCardBrush(primary),
        borderColor = heroCardBorderColor(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SegmentedControl(
                options = quickFilters,
                selected = if (timeFilter in quickFilters) timeFilter else TimeFilter.THIS_MONTH,
                onSelect = onTimeFilterChange,
                label = { it.shortLabel() },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenCustomRange) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Custom date range")
            }
            IconButton(onClick = onOpenFilters) {
                Icon(Icons.Filled.Tune, contentDescription = "Filters")
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = "Net balance",
            style = MaterialTheme.typography.labelLarge,
            color = LocalContentColor.current.copy(alpha = 0.7f)
        )
        HideableBalance(
            amount = metrics.netBalance,
            style = MaterialTheme.typography.displaySmall,
            showIcon = false,
            positiveColor = primary
        )

        Spacer(Modifier.height(Spacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            MiniStat(label = "Income", value = formatCurrency(metrics.totalIncome), color = incomeColor(), icon = Icons.Filled.ArrowUpward)
            MiniStat(label = "Expenses", value = formatCurrency(metrics.totalExpenses), color = expenseColor(), icon = Icons.Filled.ArrowDownward)
        }
    }
}

private fun TimeFilter.shortLabel(): String = when (this) {
    TimeFilter.TODAY -> "Today"
    TimeFilter.THIS_WEEK -> "Week"
    TimeFilter.THIS_MONTH -> "Month"
    TimeFilter.LAST_90_DAYS -> "3M"
    TimeFilter.THIS_YEAR -> "Year"
    else -> "Custom"
}

@Composable
private fun InsightsCarousel(insights: List<Insight>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        items(insights, key = { it.kind }) { insight ->
            InsightCard(insight = insight, modifier = Modifier.width(260.dp))
        }
    }
}

@Composable
private fun InsightCard(insight: Insight, modifier: Modifier = Modifier) {
    AppCard(variant = AppCardVariant.Outlined, modifier = modifier) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(ShapeFull)
                    .background(insightTint(insight.tone).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = insightIcon(insight.kind),
                    contentDescription = null,
                    tint = insightTint(insight.tone),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(insight.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun insightIcon(kind: InsightKind): ImageVector = when (kind) {
    InsightKind.PERIOD_COMPARISON -> Icons.AutoMirrored.Filled.TrendingUp
    InsightKind.TOP_CATEGORY -> Icons.Filled.PieChart
    InsightKind.AUTO_CATEGORIZATION -> Icons.Filled.AutoAwesome
    InsightKind.TIME_OF_DAY_PATTERN -> Icons.Filled.Schedule
    InsightKind.RECURRING_MERCHANTS -> Icons.Filled.Autorenew
}

@Composable
private fun insightTint(tone: InsightTone): Color = when (tone) {
    InsightTone.POSITIVE -> incomeColor()
    InsightTone.NEGATIVE -> expenseColor()
    InsightTone.WARNING -> needsReviewColor()
    InsightTone.NEUTRAL -> MaterialTheme.colorScheme.primary
}

@Composable
private fun CategoryBreakdownCard(
    categorySpending: List<CategorySpending>,
    selectedCategoryIds: Set<Long> = emptySet(),
    onOpenCategoryPicker: () -> Unit = {},
    onClearSelection: () -> Unit = {}
) {
    val total = categorySpending.sumOf { it.totalAmount }
    val primary = MaterialTheme.colorScheme.primary
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundBrush = heroCardBrush(primary),
        borderColor = heroCardBorderColor()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedCategoryIds.isNotEmpty()) {
                TertiaryButton(text = "Clear", onClick = onClearSelection)
            }
            IconButton(onClick = onOpenCategoryPicker) {
                Icon(Icons.Filled.Tune, contentDescription = "Choose categories")
            }
        }
        if (categorySpending.isEmpty()) {
            EmptyState(
                title = "No spending in the selected categories",
                subtitle = "Pick different categories, or clear the selection to see everything."
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CategoryDonutChart(
                    categorySpending = categorySpending,
                    centerLabel = formatCurrency(total),
                    selectedCategoryIds = selectedCategoryIds
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                categorySpending.forEach { spending ->
                    CategoryLegendRow(
                        spending = spending,
                        isSelected = spending.category.id in selectedCategoryIds,
                        isDimmed = selectedCategoryIds.isNotEmpty() && spending.category.id !in selectedCategoryIds
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(
    spending: CategorySpending,
    isSelected: Boolean = false,
    isDimmed: Boolean = false
) {
    val accent = categoricalAccent(spending.category.color)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeFull)
            .then(if (isSelected) Modifier.background(accent.copy(alpha = 0.12f)) else Modifier)
            .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AccentDot(color = accent)
                Text(
                    spending.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                "${spending.percentage.roundToInt()}% · ${formatCurrency(spending.totalAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { spending.percentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(ShapeFull)
                .alpha(if (isDimmed) 0.35f else 1f),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun TransactionRow(expense: Expense, category: Category?) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.md),
        backgroundBrush = listItemCardBrush(MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f)
            ) {
                CategoryAvatar(category = category, size = 48.dp)
                Column {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = relativeDate(expense.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (expense.needsReview) {
                            AccentDot(color = needsReviewColor(), size = 6.dp)
                        }
                    }
                }
            }
            AmountText(amount = expense.signedAmount(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * [Expense.amount] is always stored as an unsigned magnitude (confirmed across manual entry,
 * SMS parsing, and PDF import) -- [Expense.transactionType] is the actual sign of record.
 */
fun Expense.signedAmount(): Double = if (transactionType == "CREDIT") amount else -amount

private fun relativeDate(dateMillis: Long): String {
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateMillis), ZoneId.systemDefault())
    val today = LocalDate.now()
    val date = dateTime.toLocalDate()
    return when {
        date == today -> "Today · " + dateTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
        date == today.minusDays(1) -> "Yesterday"
        else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    categories: List<Category>,
    selectedCategories: Set<Long>,
    transactionType: String?,
    accounts: List<com.letstrack.app.domain.model.BankAccount>,
    selectedAccountIds: Set<Long>,
    onCategoryToggle: (Long) -> Unit,
    onClearCategories: () -> Unit,
    onTransactionTypeChange: (String?) -> Unit,
    onAccountToggle: (Long) -> Unit,
    onClearAccounts: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Transaction type", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CategoryFilterChip(
                        label = "All",
                        accent = MaterialTheme.colorScheme.primary,
                        selected = transactionType == null,
                        onClick = { onTransactionTypeChange(null) }
                    )
                    CategoryFilterChip(
                        label = "Expenses",
                        accent = expenseColor(),
                        selected = transactionType == "expense",
                        onClick = { onTransactionTypeChange("expense") }
                    )
                    CategoryFilterChip(
                        label = "Income",
                        accent = incomeColor(),
                        selected = transactionType == "income",
                        onClick = { onTransactionTypeChange("income") }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Categories", style = MaterialTheme.typography.titleSmall)
                    if (selectedCategories.isNotEmpty()) {
                        TertiaryButton(text = "Clear", onClick = onClearCategories)
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(categories, key = { it.id }) { category ->
                        CategoryFilterChip(
                            label = category.name,
                            accent = categoricalAccent(category.color),
                            selected = category.id in selectedCategories,
                            onClick = { onCategoryToggle(category.id) }
                        )
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Accounts", style = MaterialTheme.typography.titleSmall)
                        if (selectedAccountIds.isNotEmpty()) {
                            TertiaryButton(text = "Clear", onClick = onClearAccounts)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(accounts, key = { it.id }) { account ->
                            CategoryFilterChip(
                                label = account.accountNickname.ifBlank { account.bankName },
                                accent = MaterialTheme.colorScheme.primary,
                                selected = account.id in selectedAccountIds,
                                onClick = { onAccountToggle(account.id) }
                            )
                        }
                    }
                }
            }

            PrimaryButton(text = "Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

// Shared formatting helpers -- also imported by ExpensesScreen.kt and AddExpenseScreen.kt,
// keep signatures stable.
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
