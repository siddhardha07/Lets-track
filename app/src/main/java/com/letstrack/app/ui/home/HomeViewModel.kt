package com.letstrack.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.domain.ai.AiSettingsRepository
import com.letstrack.app.domain.goal.GoalProgress
import com.letstrack.app.domain.goal.GoalProgressProvider
import com.letstrack.app.domain.model.BankAccount
import com.letstrack.app.domain.model.Budget
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.repository.BankAccountRepository
import com.letstrack.app.domain.repository.BudgetRepository
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import com.letstrack.app.ui.components.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** One bar on the Budget graph -- [category] null means the "Overall" bar. Shows both how much
 * has been spent and how much headroom is left (budgetAmount - spentAmount), not just a spend
 * total, since that's the actual question a budget bar needs to answer. */
data class BudgetBarRow(
    val category: Category?,
    val spentAmount: Double,
    val budgetAmount: Double
) {
    val label: String get() = category?.name ?: "Overall"
    val isOverBudget: Boolean get() = spentAmount > budgetAmount
}

/**
 * Everything the Budget graph card needs to render. Built from [HomeViewModel.categorySpending]
 * (same flow the Category donut uses) rather than a separate always-this-month computation, so
 * Home's time filter, custom range, and account filter all apply to Budget exactly like they do
 * to the other graphs.
 *
 * Row selection: with no category filter active, shows the Overall bar (if configured) plus the
 * top-spending budgeted categories, 4 bars total by default. Once the user picks specific
 * categories in Home's Filters sheet, shows exactly those instead -- no cap, no Overall bar
 * (a single-category view has nothing "overall" left to show).
 */
data class BudgetChartData(
    val totalSpent: Double,
    val rows: List<BudgetBarRow>
)

data class CategorySpending(
    val category: Category,
    val totalAmount: Double,
    val percentage: Float
)

data class DailySpending(
    val date: Long,
    val amount: Double
)

data class KeyMetrics(
    val totalIncome: Double,
    val totalExpenses: Double,
    val netBalance: Double,
    val incomeVsPreviousPeriod: Float,  // % change
    val expensesVsPreviousPeriod: Float,  // % change
    val balanceVsPreviousPeriod: Float  // % change
)

enum class TimeFilter {
    TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, LAST_7_DAYS, LAST_30_DAYS, LAST_90_DAYS, CUSTOM
}

/** Controls both how [DailySpending] buckets are formed and how they're labeled on the x-axis
 * of [SpendingTrendChart] -- driven by the actual span of the active filter (in days) rather
 * than the TimeFilter tag alone, so a CUSTOM calendar range gets the same treatment a preset
 * filter of the same length would. */
enum class ChartLabelStyle { WEEKDAY, DAY_OF_MONTH, WEEK_NUMBER, MONTH_NAME }

private fun estimatedSpanDays(filter: TimeFilter, customRange: DateRange?): Int = when (filter) {
    TimeFilter.TODAY -> 1
    TimeFilter.THIS_WEEK, TimeFilter.LAST_7_DAYS -> 7
    TimeFilter.THIS_MONTH, TimeFilter.LAST_30_DAYS -> 30
    TimeFilter.LAST_90_DAYS -> 90
    TimeFilter.THIS_YEAR -> 365
    TimeFilter.CUSTOM -> customRange?.let {
        ((it.endDate - it.startDate) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1)
    } ?: 30
}

// Daily labels stop being legible well before 31 days -- THIS_MONTH's ~28-31 day span was
// staying on DAY_OF_MONTH and cramming 28+ labels onto one axis. Dropping the daily ceiling to
// 20 days means THIS_MONTH (and anything similarly sized) now buckets into weeks instead.
private fun chartGranularityForSpan(spanDays: Int): ChartLabelStyle = when {
    spanDays <= 7 -> ChartLabelStyle.WEEKDAY
    spanDays <= 20 -> ChartLabelStyle.DAY_OF_MONTH
    spanDays <= 60 -> ChartLabelStyle.WEEK_NUMBER
    else -> ChartLabelStyle.MONTH_NAME
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val budgetRepository: BudgetRepository,
    goalProgressProvider: GoalProgressProvider,
    private val aiSettingsRepository: AiSettingsRepository
) : ViewModel() {

    // A one-shot suspend read, not a continuously-observed StateFlow -- the AI icon's tap
    // handler needs the actual current value at the moment of the click, not whatever a
    // WhileSubscribed StateFlow happened to have cached (which can be stale right after
    // navigating back from Settings having just saved a key -- see AiChatViewModel's
    // currentApiKeyOrNull for the same class of bug on the chat screen's side).
    suspend fun hasAiApiKeyNow(): Boolean = aiSettingsRepository.currentActiveProviderAndKey() != null

    // Raw configured budgets (no spend attached) -- what the setup sheet edits. Budget *status*
    // for display (spend vs limit) is [budgetChartData] below, kept separate since that one is
    // filter-dependent and this one isn't.
    val budgets: StateFlow<List<Budget>> = budgetRepository.getAllBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unconditional on Home (not one of the toggle-row graph sections) -- top goals by percent
    // complete, with any manually-reordered ones (sortOrder set) pinned first in their chosen
    // order. Achieved goals drop out of this list entirely once celebrated.
    val topGoals: StateFlow<List<GoalProgress>> = goalProgressProvider.goalProgress.map { list ->
        list.filter { !it.goal.isAchieved }
            .sortedWith(
                compareBy(
                    { it.goal.sortOrder ?: Int.MAX_VALUE },
                    { -it.percent }
                )
            )
            .take(4)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // The "Savings Graph" toggle section shows every active goal (not capped at 4 like the Home
    // card stack) -- it's the dedicated analytics view, the card stack is the quick-glance one.
    val activeGoalsForGraph: StateFlow<List<GoalProgress>> = goalProgressProvider.goalProgress.map { list ->
        list.filter { !it.goal.isAchieved }.sortedByDescending { it.percent }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setOverallBudget(amount: Double) {
        viewModelScope.launch { budgetRepository.setOverallBudget(amount) }
    }

    fun setCategoryBudget(categoryId: Long, amount: Double) {
        viewModelScope.launch { budgetRepository.setCategoryBudget(categoryId, amount) }
    }

    fun clearOverallBudget() {
        viewModelScope.launch { budgetRepository.clearOverallBudget() }
    }

    fun clearCategoryBudget(categoryId: Long) {
        viewModelScope.launch { budgetRepository.clearCategoryBudget(categoryId) }
    }

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _timeFilter = MutableStateFlow(TimeFilter.THIS_MONTH)
    val timeFilter: StateFlow<TimeFilter> = _timeFilter.asStateFlow()
    
    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()
    
    private val _selectedCategories = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCategories: StateFlow<Set<Long>> = _selectedCategories.asStateFlow()
    
    private val _transactionType = MutableStateFlow<String?>(null) // null = all, "income", "expense"
    val transactionType: StateFlow<String?> = _transactionType.asStateFlow()

    val bankAccounts: StateFlow<List<BankAccount>> = bankAccountRepository.getAllActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAccountIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedAccountIds: StateFlow<Set<Long>> = _selectedAccountIds.asStateFlow()

    fun toggleAccountFilter(accountId: Long) {
        val current = _selectedAccountIds.value.toMutableSet()
        if (!current.add(accountId)) current.remove(accountId)
        _selectedAccountIds.value = current
    }

    fun clearAccountFilter() {
        _selectedAccountIds.value = emptySet()
    }

    // Time/category/type filters, before the account filter is applied. Kept as a separate
    // private stage rather than adding a 6th flow to the combine() below (which would need the
    // array-based overload) - lower-risk than restructuring the existing block.
    private val filteredExpensesBeforeAccountFilter: StateFlow<List<Expense>> = combine(
        _expenses,
        _timeFilter,
        _customDateRange,
        _selectedCategories,
        _transactionType
    ) { expenses, timeFilter, customRange, selectedCats, transType ->
        var filtered = expenses
        
        // Apply time filter
        filtered = when (timeFilter) {
            TimeFilter.TODAY -> {
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                filtered.filter { it.date >= todayStart }
            }
            TimeFilter.THIS_WEEK -> {
                val weekStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                filtered.filter { it.date >= weekStart }
            }
            TimeFilter.THIS_MONTH -> {
                val monthStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                filtered.filter { it.date >= monthStart }
            }
            TimeFilter.THIS_YEAR -> {
                val yearStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                filtered.filter { it.date >= yearStart }
            }
            TimeFilter.LAST_7_DAYS -> {
                val sevenDaysAgo = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -7)
                }.timeInMillis
                filtered.filter { it.date >= sevenDaysAgo }
            }
            TimeFilter.LAST_30_DAYS -> {
                val thirtyDaysAgo = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -30)
                }.timeInMillis
                filtered.filter { it.date >= thirtyDaysAgo }
            }
            TimeFilter.LAST_90_DAYS -> {
                val ninetyDaysAgo = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -90)
                }.timeInMillis
                filtered.filter { it.date >= ninetyDaysAgo }
            }
            TimeFilter.CUSTOM -> {
                if (customRange != null) {
                    filtered.filter { it.date >= customRange.startDate && it.date <= customRange.endDate }
                } else {
                    filtered
                }
            }
        }
        
        // Apply category filter
        if (selectedCats.isNotEmpty()) {
            filtered = filtered.filter { it.categoryId in selectedCats }
        }
        
        // Apply transaction type filter
        when (transType) {
            "income" -> filtered = filtered.filter { it.transactionType == "CREDIT" }
            "expense" -> filtered = filtered.filter { it.transactionType == "DEBIT" }
            else -> {}  // Show all
        }
        
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Matches SmsProcessor.findMatchingAccount's own logic (suffix match either direction,
    // since bankReference may be a full or masked account number depending on the source).
    val filteredExpenses: StateFlow<List<Expense>> = combine(
        filteredExpensesBeforeAccountFilter,
        _selectedAccountIds,
        bankAccounts
    ) { expenses, selectedIds, accounts ->
        if (selectedIds.isEmpty()) return@combine expenses
        val selectedAccountNumbers = accounts.filter { it.id in selectedIds }.map { it.accountNumber }
        expenses.filter { expense ->
            expense.bankReference.isNotBlank() && selectedAccountNumbers.any { acctNum ->
                expense.bankReference.endsWith(acctNum) || acctNum.endsWith(expense.bankReference)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category spending breakdown
    val categorySpending: StateFlow<List<CategorySpending>> = combine(
        filteredExpenses,
        _categories
    ) { expenses, categories ->
        val expensesOnly = expenses.filter { it.transactionType == "DEBIT" }
        val totalSpending = expensesOnly.sumOf { kotlin.math.abs(it.amount) }

        if (totalSpending == 0.0) {
            emptyList()
        } else {
            val categoryMap = expensesOnly.groupBy { it.categoryId }
            categoryMap.mapNotNull { (categoryId, expenseList) ->
                val category = categories.find { it.id == categoryId }
                if (category != null) {
                    val amount = expenseList.sumOf { kotlin.math.abs(it.amount) }
                    CategorySpending(
                        category = category,
                        totalAmount = amount,
                        percentage = (amount / totalSpending * 100).toFloat()
                    )
                } else null
            }.sortedByDescending { it.totalAmount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Drives both the chart's bucket width and its x-axis label text (see [chartGranularityForSpan]):
    // day buckets for short ranges, week buckets for medium ranges, month buckets for long ones --
    // computed from the actual span so a custom calendar range gets sensible treatment too, not
    // just the preset filters.
    val chartLabelStyle: StateFlow<ChartLabelStyle> = combine(_timeFilter, _customDateRange) { filter, customRange ->
        chartGranularityForSpan(estimatedSpanDays(filter, customRange))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChartLabelStyle.DAY_OF_MONTH)

    val dailySpending: StateFlow<List<DailySpending>> = combine(filteredExpenses, chartLabelStyle) { expenses, style ->
        val expensesOnly = expenses.filter { it.transactionType == "DEBIT" }
        val rangeStart = expensesOnly.minOfOrNull { it.date } ?: 0L
        val oneWeekMillis = 7L * 24 * 60 * 60 * 1000L

        val bucketKey: (Expense) -> Long = when (style) {
            ChartLabelStyle.MONTH_NAME -> { expense ->
                Calendar.getInstance().apply {
                    timeInMillis = expense.date
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            ChartLabelStyle.WEEK_NUMBER -> { expense ->
                val weekIndex = ((expense.date - rangeStart) / oneWeekMillis).coerceAtLeast(0)
                rangeStart + weekIndex * oneWeekMillis
            }
            else -> { expense ->
                Calendar.getInstance().apply {
                    timeInMillis = expense.date
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }
        expensesOnly.groupBy(bucketKey).map { (date, expenseList) ->
            DailySpending(
                date = date,
                amount = expenseList.sumOf { kotlin.math.abs(it.amount) }
            )
        }.sortedBy { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetChartData: StateFlow<BudgetChartData> = combine(
        budgets,
        categorySpending,
        _selectedCategories
    ) { budgetList, catSpending, selectedCats ->
        val totalSpent = catSpending.sumOf { it.totalAmount }

        // Based on categorySpending (already respects every Home filter) rather than iterating
        // all configured budgets, so a category with a budget but no spend in the current view
        // doesn't show up sitting at a stale zero.
        val categoryRows = catSpending.mapNotNull { spending ->
            val budgetAmount = budgetList.find { it.categoryId == spending.category.id }?.amount
                ?: return@mapNotNull null
            BudgetBarRow(spending.category, spending.totalAmount, budgetAmount)
        }

        val rows = if (selectedCats.isNotEmpty()) {
            categoryRows.filter { it.category != null && it.category.id in selectedCats }
        } else {
            val overallBudget = budgetList.find { it.categoryId == null }?.amount
            val overallRow = overallBudget?.let { BudgetBarRow(null, totalSpent, it) }
            val remainingSlots = if (overallRow != null) 3 else 4
            listOfNotNull(overallRow) + categoryRows.sortedByDescending { it.spentAmount }.take(remainingSlots)
        }

        BudgetChartData(totalSpent, rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetChartData(0.0, emptyList()))

    // Key metrics with comparison
    val keyMetrics: StateFlow<KeyMetrics> = combine(
        filteredExpenses,
        _expenses,
        _timeFilter,
        _customDateRange
    ) { filtered, allExpenses, timeFilter, customRange ->
        val totalIncome = filtered.filter { it.transactionType == "CREDIT" }.sumOf { it.amount }
        val totalExpenses = filtered.filter { it.transactionType == "DEBIT" }.sumOf { kotlin.math.abs(it.amount) }
        
        // Calculate previous period
        val previousPeriodRange = getPreviousPeriodRange(timeFilter, customRange)
        val previousPeriodExpenses = if (previousPeriodRange != null) {
            allExpenses.filter { 
                it.date >= previousPeriodRange.first && it.date <= previousPeriodRange.second 
            }
        } else {
            emptyList()
        }
        
        val prevIncome = previousPeriodExpenses.filter { it.transactionType == "CREDIT" }.sumOf { it.amount }
        val prevExpenses = previousPeriodExpenses.filter { it.transactionType == "DEBIT" }.sumOf { kotlin.math.abs(it.amount) }
        val prevBalance = prevIncome - prevExpenses
        val currentBalance = totalIncome - totalExpenses
        
        // Calculate percentage changes
        val incomeChange = if (prevIncome > 0) ((totalIncome - prevIncome) / prevIncome * 100).toFloat() else 0f
        val expensesChange = if (prevExpenses > 0) ((totalExpenses - prevExpenses) / prevExpenses * 100).toFloat() else 0f
        val balanceChange = if (prevBalance != 0.0) ((currentBalance - prevBalance) / kotlin.math.abs(prevBalance) * 100).toFloat() else 0f
        
        KeyMetrics(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netBalance = currentBalance,
            incomeVsPreviousPeriod = incomeChange,
            expensesVsPreviousPeriod = expensesChange,
            balanceVsPreviousPeriod = balanceChange
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KeyMetrics(0.0, 0.0, 0.0, 0f, 0f, 0f))

    // Recent transactions (last 10)
    val recentTransactions: StateFlow<List<Expense>> = filteredExpenses.map { expenses ->
        expenses.sortedByDescending { it.date }.take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unfiltered count of transactions needing review, for the notification bell badge.
    val needsReviewCount: StateFlow<Int> = _expenses.map { expenses ->
        expenses.count { it.needsReview }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadExpenses()
        loadCategories()
    }

    private fun loadExpenses() {
        viewModelScope.launch {
            expenseRepository.getAllExpenses().collect { expenseList ->
                _expenses.value = expenseList
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categoryList ->
                _categories.value = categoryList
            }
        }
    }

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }
    
    fun setCustomDateRange(range: DateRange) {
        _customDateRange.value = range
        _timeFilter.value = TimeFilter.CUSTOM
    }
    
    fun toggleCategoryFilter(categoryId: Long) {
        val current = _selectedCategories.value.toMutableSet()
        if (categoryId in current) {
            current.remove(categoryId)
        } else {
            current.add(categoryId)
        }
        _selectedCategories.value = current
    }
    
    fun clearCategoryFilters() {
        _selectedCategories.value = emptySet()
    }
    
    fun setTransactionType(type: String?) {
        _transactionType.value = type
    }

    fun getCategoryById(categoryId: Long): Category? {
        return _categories.value.find { it.id == categoryId }
    }
    
    private fun getPreviousPeriodRange(timeFilter: TimeFilter, customRange: DateRange?): Pair<Long, Long>? {
        val calendar = Calendar.getInstance()
        
        return when (timeFilter) {
            TimeFilter.TODAY -> {
                val yesterday = calendar.apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val yesterdayEnd = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.timeInMillis
                Pair(yesterday, yesterdayEnd)
            }
            TimeFilter.THIS_WEEK -> {
                val lastWeekStart = calendar.apply {
                    add(Calendar.WEEK_OF_YEAR, -1)
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val lastWeekEnd = Calendar.getInstance().apply {
                    add(Calendar.WEEK_OF_YEAR, -1)
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    add(Calendar.DAY_OF_YEAR, 6)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.timeInMillis
                Pair(lastWeekStart, lastWeekEnd)
            }
            TimeFilter.THIS_MONTH -> {
                val lastMonthStart = calendar.apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val lastMonthEnd = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.timeInMillis
                Pair(lastMonthStart, lastMonthEnd)
            }
            TimeFilter.THIS_YEAR -> {
                val lastYearStart = calendar.apply {
                    add(Calendar.YEAR, -1)
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val lastYearEnd = Calendar.getInstance().apply {
                    add(Calendar.YEAR, -1)
                    set(Calendar.DAY_OF_YEAR, getActualMaximum(Calendar.DAY_OF_YEAR))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }.timeInMillis
                Pair(lastYearStart, lastYearEnd)
            }
            TimeFilter.LAST_7_DAYS -> {
                val fourteenDaysAgo = calendar.apply {
                    add(Calendar.DAY_OF_YEAR, -14)
                }.timeInMillis
                val sevenDaysAgo = calendar.apply {
                    add(Calendar.DAY_OF_YEAR, 7)
                }.timeInMillis
                Pair(fourteenDaysAgo, sevenDaysAgo)
            }
            TimeFilter.LAST_30_DAYS -> {
                val sixtyDaysAgo = calendar.apply {
                    add(Calendar.DAY_OF_YEAR, -60)
                }.timeInMillis
                val thirtyDaysAgo = calendar.apply {
                    add(Calendar.DAY_OF_YEAR, 30)
                }.timeInMillis
                Pair(sixtyDaysAgo, thirtyDaysAgo)
            }
            TimeFilter.LAST_90_DAYS -> {
                val oneEightyDaysAgo = calendar.apply {
                    add(Calendar.DAY_OF_YEAR, -180)
                }.timeInMillis
                val ninetyDaysAgo = calendar.apply {
                    add(Calendar.DAY_OF_YEAR, 90)
                }.timeInMillis
                Pair(oneEightyDaysAgo, ninetyDaysAgo)
            }
            TimeFilter.CUSTOM -> {
                if (customRange != null) {
                    val duration = customRange.endDate - customRange.startDate
                    val prevEnd = customRange.startDate - 1
                    val prevStart = prevEnd - duration
                    Pair(prevStart, prevEnd)
                } else {
                    null
                }
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense)
        }
    }
}
