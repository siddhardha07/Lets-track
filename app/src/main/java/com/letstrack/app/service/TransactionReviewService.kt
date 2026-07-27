package com.letstrack.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.letstrack.app.MainActivity
import com.letstrack.app.domain.model.CategoryPrediction
import com.letstrack.app.domain.model.PendingTransaction
import com.letstrack.app.domain.model.UserCorrection
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import com.letstrack.app.ml.SmartCategorizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global service to manage transaction review overlay state
 * Can be triggered from anywhere in the app (SMS, notification, import, etc.)
 */
@Singleton
class TransactionReviewService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val smartCategorizer: SmartCategorizer
) {

    companion object {
        private const val TAG = "TransactionReviewService"
    }

    private val _pendingTransaction = MutableStateFlow<PendingTransaction?>(null)
    val pendingTransaction: StateFlow<PendingTransaction?> = _pendingTransaction.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    /**
     * Show transaction review overlay
     */
    fun showReview(transaction: PendingTransaction) {
        Log.d(TAG, "🎯 SHOWING REVIEW OVERLAY for transaction: ${transaction.merchantName}, amount: Rs.${transaction.amount}, confidence: ${transaction.confidence}")
        Log.d(TAG, "🎯 Suggested category: ${transaction.suggestedCategory}")
        _pendingTransaction.value = transaction
        // Don't set isOverlayVisible here - let the system overlay handle it
        // Only set when user clicks "Edit Details" to show in-app overlay
        Log.d(TAG, "🎯 Transaction set, starting system overlay")

        // Start overlay service to show system-wide overlay
        // No need to pass transaction in Intent - it's available via Flow
        try {
            val intent = Intent(context, OverlayService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
                Log.d(TAG, "🎯 Started OverlayService (foreground) to show system overlay")
            } else {
                context.startService(intent)
                Log.d(TAG, "🎯 Started OverlayService to show system overlay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start OverlayService: ${e.message}", e)
        }
    }

    /**
     * Show in-app review overlay (for test buttons or when app is already open)
     */
    fun showInAppReview(transaction: PendingTransaction) {
        Log.d(TAG, "🎯 Showing in-app review for: ${transaction.merchantName}")
        _pendingTransaction.value = transaction
        _isOverlayVisible.value = true
    }

    /**
     * Show in-app detail overlay (when user clicks Edit from system overlay)
     */
    fun showDetailOverlay() {
        Log.d(TAG, "🎯 Showing in-app detail overlay")
        _isOverlayVisible.value = true
    }

    /**
     * Confirm or correct transaction category
     * Updates expense and teaches SmartCategorizer
     */
    suspend fun confirmTransaction(
        transaction: PendingTransaction,
        selectedCategory: String,
        selectedSubCategory: String?,
        notes: String? = null
    ) {
        try {
            Log.d(TAG, "Confirming transaction: ${transaction.merchantName} -> $selectedCategory")

            // Get the expense
            val expense = expenseRepository.getExpenseById(transaction.expenseId)
            if (expense == null) {
                Log.e(TAG, "Expense not found: ${transaction.expenseId}")
                return
            }

            // Find category ID by name, creating it if the user typed a brand new one
            val categories = categoryRepository.getAllCategories().first()
            val existingCategory = categories.find { it.name.equals(selectedCategory, ignoreCase = true) }
            val categoryId = existingCategory?.id
                ?: categoryRepository.insertCategory(
                    com.letstrack.app.domain.model.Category(name = selectedCategory)
                )

            // Update expense with confirmed category
            val updatedExpense = expense.copy(
                categoryId = categoryId,
                subCategory = selectedSubCategory,
                notes = notes ?: expense.notes,
                needsReview = false
            )
            expenseRepository.updateExpense(updatedExpense)

            // Teach SmartCategorizer
            val originalPrediction = CategoryPrediction(
                category = transaction.suggestedCategory,
                subCategory = transaction.suggestedSubCategory,
                confidence = transaction.confidence,
                source = "ml-model"
            )

            val userCorrection = UserCorrection(
                merchantName = transaction.merchantName,
                category = selectedCategory,
                subCategory = selectedSubCategory,
                isCorrect = selectedCategory.equals(transaction.suggestedCategory, ignoreCase = true)
            )

            smartCategorizer.learnFromCorrection(
                merchantName = transaction.merchantName,
                amount = transaction.amount,
                originalPrediction = originalPrediction,
                userCorrection = userCorrection
            )

            Log.d(TAG, "✅ Transaction confirmed and learned: ${transaction.merchantName} -> $selectedCategory")

            // Show immediate confirmation toast using Handler for reliability
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "✓ Saved: ${transaction.merchantName} → $selectedCategory",
                    Toast.LENGTH_LONG
                ).show()
                Log.d(TAG, "🍞 Toast shown: Saved ${transaction.merchantName}")
            }

            // Update similar past transactions from bulk imports
            updateSimilarPastTransactions(transaction.merchantName, categoryId, selectedSubCategory)

        } catch (e: Exception) {
            Log.e(TAG, "Error confirming transaction: ${e.message}", e)
        }
    }

    /**
     * Find and update similar past transactions from bulk imports
     * Called after user categorizes a real-time transaction
     */
    private suspend fun updateSimilarPastTransactions(
        merchantName: String,
        categoryId: Long,
        subCategory: String?
    ) {
        try {
            Log.d(TAG, "🔍 Searching for similar past transactions for: $merchantName")

            // Get all expenses and categories
            val allExpenses = expenseRepository.getAllExpenses().first()
            val categories = categoryRepository.getAllCategories().first()
            val otherCategory = categories.find { it.name.equals("Other", ignoreCase = true) }

            // Find uncategorized expenses with similar merchant names
            val similarExpenses = allExpenses.filter { expense ->
                val isSameMerchant = expense.title.equals(merchantName, ignoreCase = true) ||
                                    expense.title.contains(merchantName, ignoreCase = true) ||
                                    merchantName.contains(expense.title, ignoreCase = true)
                val isUncategorized = expense.categoryId == null ||
                                     expense.categoryId == otherCategory?.id ||
                                     expense.needsReview

                isSameMerchant && isUncategorized
            }

            if (similarExpenses.isNotEmpty()) {
                Log.d(TAG, "📝 Found ${similarExpenses.size} similar past transaction(s) to update")

                similarExpenses.forEach { expense ->
                    val noteAddition = if (expense.notes.isNullOrBlank()) "Auto-categorized" else " | Auto-categorized"
                    val updated = expense.copy(
                        categoryId = categoryId,
                        subCategory = subCategory,
                        needsReview = false,
                        notes = (expense.notes ?: "") + noteAddition
                    )
                    expenseRepository.updateExpense(updated)
                    Log.d(TAG, "   ✓ Updated: ${expense.title} (₹${expense.amount})")
                }

                Log.d(TAG, "✅ Updated ${similarExpenses.size} similar past transaction(s)")

                // Show toast notification to user (only if there are past transactions)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "✓ Also updated ${similarExpenses.size} similar past transaction${if (similarExpenses.size > 1) "s" else ""}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.d(TAG, "🍞 Toast shown: Updated ${similarExpenses.size} past transactions")
                }

            } else {
                Log.d(TAG, "ℹ️ No similar past transactions found to update")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating similar past transactions: ${e.message}", e)
        }
    }

    /**
     * Reject transaction (mark for manual categorization later)
     */
    suspend fun rejectTransaction(transaction: PendingTransaction) {
        try {
            Log.d(TAG, "Rejecting transaction: ${transaction.merchantName}")

            val expense = expenseRepository.getExpenseById(transaction.expenseId)
            if (expense != null) {
                // Mark as needs review
                val updatedExpense = expense.copy(
                    needsReview = true
                )
                expenseRepository.updateExpense(updatedExpense)
                Log.d(TAG, "Transaction marked for review: ${transaction.merchantName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting transaction: ${e.message}", e)
        }
    }

    /**
     * Dismiss overlay
     */
    fun dismissReview() {
        Log.d(TAG, "Dismissing review overlay")
        _isOverlayVisible.value = false
        // Clear after animation
        _pendingTransaction.value = null

        // Stop overlay service
        try {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
            Log.d(TAG, "🎯 Stopped OverlayService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop OverlayService: ${e.message}")
        }
    }

    /**
     * Check if overlay is currently showing
     */
    fun isShowing(): Boolean = _isOverlayVisible.value
}
