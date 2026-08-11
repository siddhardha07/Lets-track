package com.letstrack.app.ui.expenses

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.data.importer.JsonImporter
import com.letstrack.app.domain.model.BankAccount
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.repository.BankAccountRepository
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

sealed class MerchantLearningStatus {
    object Idle : MerchantLearningStatus()
    data class Learning(val merchantName: String) : MerchantLearningStatus()
    data class Applied(val merchantName: String, val count: Int) : MerchantLearningStatus()
    data class Error(val message: String) : MerchantLearningStatus()
}

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val jsonImporter: JsonImporter,
    private val smartCategorizer: SmartCategorizer,
    private val smsImportService: SmsImportService,
    private val smsPermissionHandler: SmsPermissionHandler,
    private val merchantCategoryDao: com.letstrack.app.data.local.dao.MerchantCategoryDao,
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
    
    private val _merchantLearningStatus = MutableStateFlow<MerchantLearningStatus>(MerchantLearningStatus.Idle)
    val merchantLearningStatus: StateFlow<MerchantLearningStatus> = _merchantLearningStatus.asStateFlow()

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

    // Filtered expenses based on search and date, before the account filter is applied.
    private val filteredExpensesBeforeAccountFilter: StateFlow<List<Expense>> = combine(
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
     * Pull-to-refresh only catches up on missed SMS messages.
     * Merchant learning & category updates happen when user manually edits a transaction.
     */
    fun refresh() {
        // Atomic check-and-set -- a plain "if already refreshing, return" read-then-write can
        // let two near-simultaneous calls both see "not refreshing yet" and both launch a scan.
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                catchUpOnMissedSms()  // ONLY fetch new SMS messages
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

    /**
     * Learn from user's category choice and apply it to all transactions from the same merchant.
     * Called when user manually edits a transaction's category.
     * Automatically triggers UI update via expenses Flow.
     */
    fun learnFromUserCorrection(expense: Expense, newCategoryId: Long) {
        viewModelScope.launch {
            try {
                val merchantName = expense.merchantName.trim()
                if (merchantName.isEmpty()) return@launch
                
                _merchantLearningStatus.value = MerchantLearningStatus.Learning(merchantName)
                
                val category = getCategoryById(newCategoryId) ?: return@launch
                
                // Save to merchant_categories table for future learning
                merchantCategoryDao.insert(
                    com.letstrack.app.data.local.entity.MerchantCategoryEntity(
                        merchantName = merchantName.uppercase(),
                        mainCategory = category.name,
                        subCategory = expense.subCategory,
                        confidence = 1.0,  // 100% confidence - user explicitly chose this
                        source = "user-correction",
                        lastUsed = System.currentTimeMillis(),
                        usageCount = 1,
                        createdAt = System.currentTimeMillis()
                    )
                )
                
                Log.d("ExpensesViewModel", "Learned: ${merchantName.uppercase()} -> ${category.name} (user correction)")
                
                // Apply this learning to ALL other transactions from same merchant
                val updatedCount = applyMerchantLearning(merchantName, newCategoryId)
                
                // Update status with result - UI will show feedback
                _merchantLearningStatus.value = MerchantLearningStatus.Applied(merchantName, updatedCount)
                
                // Clear status after 3 seconds
                delay(3000)
                _merchantLearningStatus.value = MerchantLearningStatus.Idle
                
            } catch (e: Exception) {
                Log.e("ExpensesViewModel", "Failed to learn from user correction: ${e.message}", e)
                _merchantLearningStatus.value = MerchantLearningStatus.Error(e.message ?: "Unknown error")
                delay(3000)
                _merchantLearningStatus.value = MerchantLearningStatus.Idle
            }
        }
    }
    
    /**
     * Apply learned category to all transactions from a specific merchant.
     * Returns the number of transactions updated.
     */
    private suspend fun applyMerchantLearning(merchantName: String, categoryId: Long): Int {
        return try {
            val merchantTransactions = _expenses.value.filter { 
                it.merchantName.equals(merchantName, ignoreCase = true) 
            }
            
            var updatedCount = 0
            for (transaction in merchantTransactions) {
                // Only update if different category
                if (transaction.categoryId != categoryId) {
                    expenseRepository.updateExpense(
                        transaction.copy(
                            categoryId = categoryId,
                            isAiCategorized = false,  // Now it's user-learned!
                            needsReview = false,
                            updatedAt = System.currentTimeMillis()  // Trigger UI update
                        )
                    )
                    updatedCount++
                }
            }
            
            Log.d("ExpensesViewModel", "Applied merchant learning: Updated $updatedCount transactions for $merchantName")
            updatedCount
            
        } catch (e: Exception) {
            Log.e("ExpensesViewModel", "Failed to apply merchant learning: ${e.message}", e)
            0
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
