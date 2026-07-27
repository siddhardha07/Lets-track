package com.letstrack.app.ui.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private var currentExpenseId: Long? = null

    init {
        loadCategories()
        seedDefaultCategoriesIfEmpty()
    }

    private fun seedDefaultCategoriesIfEmpty() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { existingCategories ->
                if (existingCategories.isEmpty()) {
                    // Add default categories
                    val defaultCategories = listOf(
                        Category(name = "Food", icon = "🍔", color = "#FF5722"),
                        Category(name = "Shopping", icon = "🛍️", color = "#E91E63"),
                        Category(name = "Transportation", icon = "🚗", color = "#9C27B0"),
                        Category(name = "Entertainment", icon = "🎬", color = "#673AB7"),
                        Category(name = "Bills & Utilities", icon = "💡", color = "#3F51B5"),
                        Category(name = "Healthcare", icon = "🏥", color = "#2196F3"),
                        Category(name = "Education", icon = "📚", color = "#009688"),
                        Category(name = "Groceries", icon = "🛒", color = "#4CAF50"),
                        Category(name = "Personal Care", icon = "💆", color = "#8BC34A"),
                        Category(name = "Gifts & Donations", icon = "🎁", color = "#FFC107"),
                        Category(name = "Travel", icon = "✈️", color = "#FF9800"),
                        Category(name = "Other", icon = "📝", color = "#795548")
                    )
                    defaultCategories.forEach { category ->
                        categoryRepository.insertCategory(category)
                    }
                }
            }
        }
    }

    fun loadExpense(expenseId: Long) {
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(expenseId)
            expense?.let {
                currentExpenseId = it.id
                _uiState.update { state ->
                    state.copy(
                        amount = it.amount.toString(),
                        title = it.title,
                        subCategory = it.subCategory ?: "",
                        description = it.description,
                        notes = it.notes,
                        dateMillis = it.date,
                        transactionType = it.transactionType
                    )
                }
                // Find and set the category
                val category = _categories.value.find { cat -> cat.id == it.categoryId }
                category?.let { cat ->
                    _uiState.update { state -> state.copy(selectedCategory = cat) }
                }
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

    fun onAmountChange(amount: String) {
        _uiState.update { it.copy(amount = amount) }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun onSubCategoryChange(subCategory: String) {
        _uiState.update { it.copy(subCategory = subCategory) }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onCategorySelect(category: Category) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onDateChange(dateMillis: Long) {
        _uiState.update { it.copy(dateMillis = dateMillis) }
    }

    fun createNewCategory(name: String, icon: String, color: String) {
        viewModelScope.launch {
            val newCategory = Category(
                name = name,
                icon = icon,
                color = color
            )
            categoryRepository.insertCategory(newCategory)
            // The new category will automatically appear via the Flow
            // Select it automatically
            val allCategories = categoryRepository.getAllCategories().first()
            val createdCategory = allCategories.find { it.name == name }
            createdCategory?.let { category ->
                _uiState.update { it.copy(selectedCategory = category) }
            }
        }
    }

    fun saveExpense(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.amount.isEmpty() || state.title.isEmpty() || state.selectedCategory == null) {
            return
        }

        val amount = state.amount.toDoubleOrNull() ?: return

        viewModelScope.launch {
            if (currentExpenseId != null) {
                // Update existing expense
                val expense = Expense(
                    id = currentExpenseId!!,
                    amount = amount,
                    categoryId = state.selectedCategory.id,
                    title = state.title,
                    subCategory = state.subCategory.ifEmpty { null },
                    description = state.description,
                    notes = state.notes,
                    date = state.dateMillis,
                    transactionType = state.transactionType
                )
                expenseRepository.updateExpense(expense)
            } else {
                // Insert new expense
                val expense = Expense(
                    amount = amount,
                    categoryId = state.selectedCategory.id,
                    title = state.title,
                    subCategory = state.subCategory.ifEmpty { null },
                    description = state.description,
                    notes = state.notes,
                    date = state.dateMillis,
                    transactionType = state.transactionType
                )
                expenseRepository.insertExpense(expense)
            }
            onSuccess()
        }
    }
}

data class AddExpenseUiState(
    val amount: String = "",
    val title: String = "",
    val subCategory: String = "",
    val description: String = "",
    val notes: String = "",
    val selectedCategory: Category? = null,
    val dateMillis: Long = Instant.now().toEpochMilli(),
    val transactionType: String = "DEBIT"
)
