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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private val _totalBalance = MutableStateFlow(0.0)
    val totalBalance: StateFlow<Double> = _totalBalance.asStateFlow()

    private val _totalCredited = MutableStateFlow(0.0)
    val totalCredited: StateFlow<Double> = _totalCredited.asStateFlow()

    private val _totalDebited = MutableStateFlow(0.0)
    val totalDebited: StateFlow<Double> = _totalDebited.asStateFlow()

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

    fun onCustomDateRangeSet(startDate: Long, endDate: Long) {
        _customStartDate.value = startDate
        _customEndDate.value = endDate
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
