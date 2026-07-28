package com.letstrack.app.ui.expenses

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.data.importer.JsonImporter
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import com.letstrack.app.ml.SmartCategorizer
import com.letstrack.app.sms.SmsImportService
import com.letstrack.app.sms.SmsPermissionHandler
import com.letstrack.app.ui.components.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject

enum class DateFilter {
    ALL, YEAR, MONTH, DAY, CUSTOM
}

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val jsonImporter: JsonImporter,
    private val smartCategorizer: SmartCategorizer,
    private val smsImportService: SmsImportService,
    private val smsPermissionHandler: SmsPermissionHandler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    private val _customEndDate = MutableStateFlow<Long?>(null)
    
    val customDateRange: StateFlow<DateRange?> = combine(
        _customStartDate,
        _customEndDate
    ) { start, end ->
        if (start != null && end != null) DateRange(start, end) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _totalBalance = MutableStateFlow(0.0)
    val totalBalance: StateFlow<Double> = _totalBalance.asStateFlow()

    private val _totalCredited = MutableStateFlow(0.0)
    val totalCredited: StateFlow<Double> = _totalCredited.asStateFlow()

    private val _totalDebited = MutableStateFlow(0.0)
    val totalDebited: StateFlow<Double> = _totalDebited.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Filtered expenses based on search and date
    val filteredExpenses: StateFlow<List<Expense>> = combine(
        _expenses,
        _searchQuery,
        _dateFilter,
        _customStartDate,
        _customEndDate
    ) { expenses, query, filter, customStart, customEnd ->
        var filtered = expenses

        // Apply date filter
        filtered = when (filter) {
            DateFilter.ALL -> expenses
            DateFilter.DAY -> {
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val tomorrow = today + 24 * 60 * 60 * 1000
                expenses.filter { it.date >= today && it.date < tomorrow }
            }
            DateFilter.MONTH -> {
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentYear = calendar.get(Calendar.YEAR)
                expenses.filter { expense ->
                    val expenseCal = Calendar.getInstance().apply { timeInMillis = expense.date }
                    expenseCal.get(Calendar.MONTH) == currentMonth &&
                    expenseCal.get(Calendar.YEAR) == currentYear
                }
            }
            DateFilter.YEAR -> {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                expenses.filter { expense ->
                    val expenseCal = Calendar.getInstance().apply { timeInMillis = expense.date }
                    expenseCal.get(Calendar.YEAR) == currentYear
                }
            }
            DateFilter.CUSTOM -> {
                if (customStart != null && customEnd != null) {
                    expenses.filter { it.date >= customStart && it.date <= customEnd }
                } else {
                    expenses
                }
            }
        }

        // Apply search filter
        if (query.isEmpty()) {
            filtered
        } else {
            filtered.filter { expense ->
                expense.title.contains(query, ignoreCase = true) ||
                expense.description.contains(query, ignoreCase = true) ||
                expense.amount.toString().contains(query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadExpenses()
        loadCategories()
        calculateBalances()
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

    private fun calculateBalances() {
        viewModelScope.launch {
            filteredExpenses.collect { expenses ->
                val totalDebited = expenses
                    .filter { it.transactionType == "DEBIT" }
                    .sumOf { it.amount }
                val totalCredited = expenses
                    .filter { it.transactionType == "CREDIT" }
                    .sumOf { it.amount }

                _totalDebited.value = totalDebited
                _totalCredited.value = totalCredited

                // Use balance from latest SMS transaction
                val latestBalanceFromSms = expenses
                    .filter { it.source == "SMS" && it.balanceAfterTransaction != null }
                    .maxByOrNull { it.date }
                    ?.balanceAfterTransaction

                _totalBalance.value = latestBalanceFromSms ?: 0.0
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onDateFilterChange(filter: DateFilter) {
        _dateFilter.value = filter
    }
    
    fun setCustomDateRange(range: DateRange) {
        _customStartDate.value = range.startDate
        _customEndDate.value = range.endDate
        _dateFilter.value = DateFilter.CUSTOM
    }


    fun getCategoryById(categoryId: Long): Category? {
        return _categories.value.find { it.id == categoryId }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense)
        }
    }

    fun deleteAllExpenses() {
        viewModelScope.launch {
            expenseRepository.deleteAllExpenses()
        }
    }

    /**
     * Pull-to-refresh does three things: (1) catches up on any bank SMS the broadcast
     * receiver missed (app killed, permission granted late, etc.) by re-scanning a recent
     * window -- [SmsImportService] already dedupes by message+timestamp so re-scanning
     * overlap is harmless; (2) re-syncs every transaction's *category* to whatever its
     * merchant is currently learned as, since a correction made on one transaction (via edit
     * or the review overlay) should retroactively apply to every other transaction from that
     * same merchant -- but only the category, never subCategory, since that's per-transaction
     * detail (e.g. "Lunch" vs "Dinner" under Food) that a blind merchant-level sync shouldn't
     * overwrite.
     */
    fun refresh() {
        // Atomic check-and-set -- a plain "if already refreshing, return" read-then-write can
        // let two near-simultaneous calls both see "not refreshing yet" and both launch a scan.
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                catchUpOnMissedSms()
                resyncMerchantCategories()
            } finally {
                val elapsed = System.currentTimeMillis() - startedAt
                val minimumVisibleDuration = 500L
                if (elapsed < minimumVisibleDuration) delay(minimumVisibleDuration - elapsed)
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun catchUpOnMissedSms() {
        if (!smsPermissionHandler.hasReadSmsPermission()) return
        try {
            val now = System.currentTimeMillis()
            
            // Get the timestamp of the last processed SMS message
            val lastSmsTimestamp = smsImportService.getLastProcessedSmsTimestamp()
            
            val startTime = if (lastSmsTimestamp != null) {
                // Only scan for NEW messages since the last one we processed
                // Add 1ms to avoid re-processing the same message
                lastSmsTimestamp + 1
            } else {
                // First time: scan last 1 minute as fallback
                val catchUpWindow = 60 * 1000L // 1 minute
                now - catchUpWindow
            }
            
            // Only import if there's actually a time window to scan
            if (startTime < now) {
                Log.d("ExpensesViewModel", "Scanning for SMS from ${java.util.Date(startTime)} to ${java.util.Date(now)}")
                smsImportService.importSmsFromDateRange(startTime, now)
            } else {
                Log.d("ExpensesViewModel", "No new SMS to scan - already up to date")
            }
        } catch (e: Exception) {
            Log.e("ExpensesViewModel", "SMS catch-up scan failed: ${e.message}", e)
        }
    }

    private suspend fun resyncMerchantCategories() {
        val categories = _categories.value
        val byMerchant = _expenses.value
            .filter { it.merchantName.isNotBlank() }
            .groupBy { it.merchantName.uppercase().trim() }

        for (group in byMerchant.values) {
            try {
                val sample = group.first()
                val prediction = smartCategorizer.categorize(
                    merchantName = sample.merchantName,
                    amount = sample.amount,
                    transactionType = sample.transactionType,
                    message = ""
                )
                if (prediction.category == "Other" || prediction.confidence <= 0.0) continue

                val newCategory = categories.find { it.name == prediction.category } ?: continue

                for (expense in group) {
                    val alreadyResolved = expense.categoryId == newCategory.id && !expense.needsReview
                    if (alreadyResolved) continue

                    expenseRepository.updateExpense(
                        expense.copy(
                            categoryId = newCategory.id,
                            needsReview = false,
                            isAiCategorized = true,
                            confidenceScore = prediction.confidence
                        )
                    )
                }
            } catch (e: Exception) {
                // One merchant failing to resync shouldn't abort the rest, and definitely
                // shouldn't leave the pull-to-refresh spinner stuck on -- refresh()'s own
                // try/finally already guarantees isRefreshing resets, but there's no reason
                // to skip every other merchant just because one had bad data.
                Log.e("ExpensesViewModel", "Merchant resync failed for a group: ${e.message}", e)
            }
        }
    }

    suspend fun importJsonFile(): String {
        return try {
            // Look for the JSON file in the app's directory
            val jsonFile = File(context.filesDir, "151103XXXX.json")
            if (!jsonFile.exists()) {
                return "JSON file not found. Please place 151103XXXX.json in the app's files directory."
            }

            val result = jsonImporter.importFromJson(jsonFile.inputStream())

            if (result.success > 0) {
                "✓ Imported ${result.success} transactions successfully"
            } else {
                "Failed to import: ${result.errors.firstOrNull() ?: "Unknown error"}"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
