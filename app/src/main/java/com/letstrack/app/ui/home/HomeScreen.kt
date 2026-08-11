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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.letstrack.app.domain.model.Budget
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
import com.letstrack.app.ui.theme.categoricalAccentMap
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
    onOpenNotifications: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    openBudgetSetupOnLaunch: Boolean = false,
    onBudgetSetupConsumed: () -> Unit = {}
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
    val budgetChartData by viewModel.budgetChartData.collectAsState()
    val budgets by viewModel.budgets.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showBudgetSetupSheet by remember { mutableStateOf(false) }

    // "+" menu's "Add Budget" entry has no dedicated screen/route of its own -- it navigates
    // back to Home and sets this one-shot flag instead, which just opens the same sheet Home's
    // own "Edit" button opens.
    LaunchedEffect(openBudgetSetupOnLaunch) {
        if (openBudgetSetupOnLaunch) {
            showBudgetSetupSheet = true
            onBudgetSetupConsumed()
        }
    }

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

    // Rank-based (by category id), not hue-snapped from each category's own stored color --
    // guarantees no two categories share a color (until there are more categories than palette
    // entries), computed from the full list so it doesn't shift depending on what's filtered.
    val categoryAccentMap = remember(categories) { categoricalAccentMap(categories) }

    // Which graph sections are expanded below the toggle row -- starts empty (all closed) per
    // design, not persisted across app opens. Ordered by HomeGraphIcon.entries below, not tap
    // order, so sections don't jump around as the user opens/closes more of them.
    var openGraphs by remember { mutableStateOf<Set<HomeGraphIcon>>(emptySet()) }

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

            item {
                GraphToggleRow(
                    openGraphs = openGraphs,
                    onToggle = { graph ->
                        openGraphs = if (graph in openGraphs) openGraphs - graph else openGraphs + graph
                    },
                    onAiClick = onOpenAi
                )
            }

            // Rendered in a fixed order (HomeGraphIcon.entries), not tap order, so opening a
            // second graph never reshuffles one that's already open.
            if (HomeGraphIcon.BAR in openGraphs) {
                item { SectionHeader("Spending Trend") }
                item {
                    if (dailySpending.isEmpty()) {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            EmptyState(title = "No spending yet", subtitle = "Your daily spending trend will show up here.")
                        }
                    } else {
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
            }

            if (HomeGraphIcon.PIE in openGraphs) {
                item { SectionHeader("Spending by Category") }
                item {
                    CategoryBreakdownCard(
                        categorySpending = visibleCategorySpending,
                        // Built from the full category list, not visibleCategorySpending's
                        // (possibly donut-filtered) subset -- so a category's color stays the
                        // same whether or not the donut picker has narrowed the view.
                        accentMap = categoryAccentMap,
                        selectedCategoryIds = donutSelectedCategoryIds,
                        onOpenCategoryPicker = { showDonutCategoryPicker = true },
                        onClearSelection = { donutSelectedCategoryIds = emptySet() }
                    )
                }
            }

            if (HomeGraphIcon.BUDGET in openGraphs) {
                item {
                    SectionHeader("Budget", actionLabel = "Edit", onActionClick = { showBudgetSetupSheet = true })
                }
                item {
                    BudgetCard(
                        data = budgetChartData,
                        accentMap = categoryAccentMap,
                        onSetBudget = { showBudgetSetupSheet = true }
                    )
                }
            }

            if (HomeGraphIcon.SAVINGS in openGraphs) {
                item { SectionHeader("Saving Goals") }
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        EmptyState(title = "Saving goals are coming soon", subtitle = "Add a goal from the + button and track it here as a card.")
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

    if (showBudgetSetupSheet) {
        BudgetSetupSheet(
            budgets = budgets,
            categories = categories,
            onSetOverall = viewModel::setOverallBudget,
            onClearOverall = viewModel::clearOverallBudget,
            onSetCategory = viewModel::setCategoryBudget,
            onClearCategory = viewModel::clearCategoryBudget,
            onDismiss = { showBudgetSetupSheet = false }
        )
    }
}

@Composable
private fun BudgetCard(
    data: BudgetChartData,
    accentMap: Map<Long, Color>,
    onSetBudget: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundBrush = heroCardBrush(primary),
        borderColor = heroCardBorderColor()
    ) {
        if (data.rows.isEmpty()) {
            EmptyState(
                title = "No budget set yet",
                subtitle = "Set an overall or per-category monthly limit to track spending against it."
            )
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(text = "Set a budget", onClick = onSetBudget, modifier = Modifier.fillMaxWidth())
        } else {
            BudgetBarsChart(data = data, accentMap = accentMap, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSetupSheet(
    budgets: List<Budget>,
    categories: List<Category>,
    onSetOverall: (Double) -> Unit,
    onClearOverall: () -> Unit,
    onSetCategory: (Long, Double) -> Unit,
    onClearCategory: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var overallText by remember {
        mutableStateOf(budgets.find { it.categoryId == null }?.amount?.let { formatPlainAmount(it) } ?: "")
    }
    val categoryTexts = remember {
        val map = mutableStateMapOf<Long, String>()
        categories.forEach { category ->
            val existing = budgets.find { it.categoryId == category.id }?.amount
            map[category.id] = existing?.let { formatPlainAmount(it) } ?: ""
        }
        map
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Spacing.lg)
        ) {
            // Save sits next to the title instead of at the bottom of the field list -- with
            // enough categories the button used to be a scroll away, easy to miss entirely.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monthly budget", style = MaterialTheme.typography.titleLarge)
                PrimaryButton(
                    text = "Save",
                    onClick = {
                        val overallAmount = overallText.toDoubleOrNull()
                        if (overallAmount != null && overallAmount > 0) onSetOverall(overallAmount) else onClearOverall()
                        categories.forEach { category ->
                            val amount = categoryTexts[category.id]?.toDoubleOrNull()
                            if (amount != null && amount > 0) onSetCategory(category.id, amount) else onClearCategory(category.id)
                        }
                        onDismiss()
                    }
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                item {
                    OutlinedTextField(
                        value = overallText,
                        onValueChange = { overallText = it },
                        label = { Text("Overall limit") },
                        placeholder = { Text("Leave blank for no overall limit") },
                        prefix = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item { Text("Per-category limits", style = MaterialTheme.typography.titleSmall) }

                items(categories, key = { it.id }) { category ->
                    OutlinedTextField(
                        value = categoryTexts[category.id] ?: "",
                        onValueChange = { categoryTexts[category.id] = it },
                        label = { Text(category.name) },
                        placeholder = { Text("No limit") },
                        prefix = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

/** Plain numeric string for editing (no currency symbol/grouping) -- formatCurrency's locale
 * formatting isn't safe to feed back into toDoubleOrNull(). */
private fun formatPlainAmount(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()

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
    // Rank-based, not hue-snapped -- guarantees every chip here gets its own distinct color
    // instead of two similarly-colored categories collapsing onto the same one.
    val accentMap = remember(categories) { categoricalAccentMap(categories) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        rows.forEach { rowCategories ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowCategories.forEach { category ->
                    CategoryFilterChip(
                        label = category.name,
                        accent = accentMap[category.id] ?: categoricalAccent(category.color),
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

/** The graph sections the toggle row can expand -- order here is the fixed render order in
 * [HomeScreen], independent of the order the user actually taps them in. AI isn't part of this
 * enum since it doesn't expand inline -- it navigates to its own chat screen instead. */
enum class HomeGraphIcon(val label: String, val icon: ImageVector) {
    BAR("Trend", Icons.Filled.BarChart),
    PIE("Categories", Icons.Filled.PieChart),
    BUDGET("Budget", Icons.Filled.AccountBalanceWallet),
    SAVINGS("Goals", Icons.Filled.Savings)
}

/**
 * Replaces the old always-visible "AI Insights" carousel: every graph on Home now starts
 * collapsed, and this row is the single place that opens/closes each one (multiple can be open
 * at once, to compare). AI is deliberately not a toggle -- tapping it always calls [onAiClick]
 * (navigate to chat, or the caller can show a "set up your API key" toast) rather than expanding
 * a section here.
 */
@Composable
private fun GraphToggleRow(
    openGraphs: Set<HomeGraphIcon>,
    onToggle: (HomeGraphIcon) -> Unit,
    onAiClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.lg, horizontal = Spacing.sm),
        backgroundBrush = heroCardBrush(primary),
        borderColor = heroCardBorderColor()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeGraphIcon.entries.forEach { graph ->
                GraphToggleIconButton(
                    icon = graph.icon,
                    label = graph.label,
                    selected = graph in openGraphs,
                    onClick = { onToggle(graph) }
                )
            }
            GraphToggleIconButton(
                icon = Icons.Filled.AutoAwesome,
                label = "AI",
                selected = false,
                onClick = onAiClick
            )
        }
    }
}

@Composable
private fun GraphToggleIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Tinted with the theme's own primary color (not a hardcoded one) so these icons follow
    // whichever AccentTheme the user has picked, same as every other themed element on Home.
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(58.dp)
                .clip(ShapeFull)
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
private fun CategoryBreakdownCard(
    categorySpending: List<CategorySpending>,
    accentMap: Map<Long, Color>,
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
                    accentMap = accentMap,
                    centerLabel = formatCurrency(total),
                    selectedCategoryIds = selectedCategoryIds
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                categorySpending.forEach { spending ->
                    CategoryLegendRow(
                        spending = spending,
                        accent = accentMap[spending.category.id] ?: categoricalAccent(spending.category.color),
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
    accent: Color,
    isSelected: Boolean = false,
    isDimmed: Boolean = false
) {
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
    val accentMap = remember(categories) { categoricalAccentMap(categories) }
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
                            accent = accentMap[category.id] ?: categoricalAccent(category.color),
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
