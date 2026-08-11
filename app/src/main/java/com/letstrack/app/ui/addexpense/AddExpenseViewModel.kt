package com.letstrack.app.ui.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.CategoryPrediction
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.model.UserCorrection
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import com.letstrack.app.ml.SmartCategorizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val smartCategorizer: SmartCategorizer,
    private val merchantCategoryDao: com.letstrack.app.data.local.dao.MerchantCategoryDao
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private val _bulkUpdateConfirmation = MutableStateFlow<BulkUpdateConfirmation?>(null)
    val bulkUpdateConfirmation: StateFlow<BulkUpdateConfirmation?> = _bulkUpdateConfirmation.asStateFlow()

    private var currentExpenseId: Long? = null
    // The as-loaded expense, kept so saveExpense() can .copy() from it and preserve fields the
    // form doesn't expose (merchantName, source tracking, AI metadata) instead of losing them.
    private var originalExpense: Expense? = null
    
    // Store the onSuccess callback for deferred execution after user confirms/declines
    private var pendingSuccessCallback: (() -> Unit)? = null

    // Generic merchant names that shouldn't trigger bulk learning
    private val genericMerchantNames = setOf(
        "bank transaction",
        "atm withdrawal",
        "cash withdrawal",
        "online transfer",
        "bank transfer",
        "debit",
        "credit",
        "refund"
    )

    init {
        loadCategories()
        // Seeding now happens once at app startup (LetsTrackApp.onCreate), sourced from
        // DefaultCategories, so it reliably runs regardless of which screen the user opens
        // first. This ViewModel used to carry its own copy of the default list (which had
        // drifted from the other two copies - see DefaultCategories) and kept re-seeding
        // live via .collect{} for as long as this screen was open, which would silently
        // undo an intentional "delete all categories" done elsewhere while this screen
        // was still alive. Removed rather than reimplemented.
    }

    fun loadExpense(expenseId: Long) {
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(expenseId)
            expense?.let {
                currentExpenseId = it.id
                originalExpense = it
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

    fun onTransactionTypeChange(transactionType: String) {
        _uiState.update { it.copy(transactionType = transactionType) }
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
                // Update existing expense -- .copy() from the originally-loaded expense so
                // fields the form doesn't expose (merchantName, source tracking, AI metadata)
                // survive the edit instead of silently resetting to defaults.
                val original = originalExpense
                val categoryChanged = original != null && original.categoryId != state.selectedCategory.id
                val expense = (original ?: Expense(amount = amount, categoryId = state.selectedCategory.id, title = state.title)).copy(
                    amount = amount,
                    categoryId = state.selectedCategory.id,
                    title = state.title,
                    subCategory = state.subCategory.ifEmpty { null },
                    description = state.description,
                    notes = state.notes,
                    date = state.dateMillis,
                    transactionType = state.transactionType,
                    needsReview = if (categoryChanged) false else (original?.needsReview ?: false)
                )
                expenseRepository.updateExpense(expense)

                // If category changed for a specific merchant transaction, ask user before bulk update
                if (categoryChanged && original != null && original.merchantName.isNotBlank() && 
                    !isGenericMerchant(original.merchantName)) {
                    // Check how many other transactions would be affected
                    val affectedCount = countAffectedTransactions(original.merchantName, original.id)
                    
                    if (affectedCount > 0) {
                        // Store the callback for later and show confirmation dialog
                        pendingSuccessCallback = onSuccess
                        _bulkUpdateConfirmation.value = BulkUpdateConfirmation(
                            merchantName = original.merchantName,
                            categoryId = state.selectedCategory.id,
                            categoryName = state.selectedCategory.name,
                            affectedCount = affectedCount,
                            subCategory = expense.subCategory
                        )
                        // Return early - don't call onSuccess yet, wait for user decision
                        return@launch
                    }
                    
                    // Save merchant learning to database (for future transactions)
                    merchantCategoryDao.insert(
                        com.letstrack.app.data.local.entity.MerchantCategoryEntity(
                            merchantName = original.merchantName.uppercase().trim(),
                            mainCategory = state.selectedCategory.name,
                            subCategory = expense.subCategory,
                            confidence = 1.0,  // 100% - user manually chose this
                            source = "user-correction",
                            lastUsed = System.currentTimeMillis(),
                            usageCount = 1,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    
                    // Also teach SmartCategorizer for future AI predictions
                    val originalCategoryName = _categories.value.find { it.id == original.categoryId }?.name ?: "Other"
                    smartCategorizer.learnFromCorrection(
                        merchantName = original.merchantName,
                        amount = expense.amount,
                        originalPrediction = CategoryPrediction(
                            category = originalCategoryName,
                            subCategory = original.subCategory,
                            confidence = original.confidenceScore,
                            source = if (original.isAiCategorized) "ml-model" else "manual"
                        ),
                        userCorrection = UserCorrection(
                            merchantName = original.merchantName,
                            category = state.selectedCategory.name,
                            subCategory = expense.subCategory,
                            isCorrect = false
                        )
                    )
                }
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
    
    /**
     * Check if a merchant name is generic (shouldn't trigger bulk learning)
     */
    private fun isGenericMerchant(merchantName: String): Boolean {
        return genericMerchantNames.contains(merchantName.lowercase().trim())
    }
    
    /**
     * Count how many other transactions would be affected by merchant learning
     */
    private suspend fun countAffectedTransactions(merchantName: String, excludeId: Long): Int {
        val allExpenses = expenseRepository.getAllExpenses().first()
        return allExpenses.count { 
            it.id != excludeId && 
            it.merchantName.equals(merchantName, ignoreCase = true) 
        }
    }
    
    /**
     * User confirmed bulk update - apply learning to all merchant transactions
     */
    fun confirmBulkUpdate() {
        viewModelScope.launch {
            val confirmation = _bulkUpdateConfirmation.value
            if (confirmation != null) {
                applyMerchantLearning(
                    confirmation.merchantName, 
                    confirmation.categoryId,
                    confirmation.subCategory
                )
            }
            _bulkUpdateConfirmation.value = null
            // Now call the success callback to navigate away
            pendingSuccessCallback?.invoke()
            pendingSuccessCallback = null
        }
    }
    
    /**
     * User declined bulk update - just dismiss dialog and navigate
     */
    fun declineBulkUpdate() {
        _bulkUpdateConfirmation.value = null
        // Still call success callback to navigate away (just don't update other transactions)
        pendingSuccessCallback?.invoke()
        pendingSuccessCallback = null
    }

    /**
     * Apply learned category to all transactions from a specific merchant.
     */
    private suspend fun applyMerchantLearning(merchantName: String, categoryId: Long, subCategory: String?) {
        try {
            val allExpenses = expenseRepository.getAllExpenses().first()
            val merchantTransactions = allExpenses.filter { 
                it.merchantName.equals(merchantName, ignoreCase = true) 
            }
            
            var updatedCount = 0
            for (transaction in merchantTransactions) {
                if (transaction.categoryId != categoryId) {
                    expenseRepository.updateExpense(
                        transaction.copy(
                            categoryId = categoryId,
                            subCategory = subCategory,
                            isAiCategorized = false,
                            needsReview = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    updatedCount++
                }
            }
            
            android.util.Log.d("AddExpenseViewModel", "✅ Applied learning: Updated $updatedCount transactions for $merchantName")
        } catch (e: Exception) {
            android.util.Log.e("AddExpenseViewModel", "Failed to apply merchant learning: ${e.message}", e)
        }
    }
}

/**
 * Data class for bulk update confirmation dialog
 */
data class BulkUpdateConfirmation(
    val merchantName: String,
    val categoryId: Long,
    val categoryName: String,
    val affectedCount: Int,
    val subCategory: String?
)

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
