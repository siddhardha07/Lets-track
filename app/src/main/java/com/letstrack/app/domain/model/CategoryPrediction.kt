package com.letstrack.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Category prediction result from Smart Categorizer
 */
data class CategoryPrediction(
    val category: String,
    val subCategory: String? = null,
    val confidence: Double, // 0.0 to 1.0
    val source: String, // "merchant-db", "ml-model", "rule-based", "wikipedia", "unknown"
    val reasoning: String? = null // Optional explanation for debugging
) {
    /**
     * Confidence levels for UX decisions
     */
    val confidenceLevel: ConfidenceLevel
        get() = when {
            confidence >= 0.9 -> ConfidenceLevel.HIGH
            confidence >= 0.6 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

    enum class ConfidenceLevel {
        HIGH,    // >= 90%: Auto-categorize, no user prompt
        MEDIUM,  // 60-90%: Show Yes/No confirmation
        LOW      // < 60%: Require manual selection
    }
}

/**
 * User correction/confirmation for learning
 */
data class UserCorrection(
    val merchantName: String,
    val category: String,
    val subCategory: String?,
    val isCorrect: Boolean // true if user confirmed, false if corrected
)

/**
 * Pending transaction for real-time overlay
 */
@Parcelize
data class PendingTransaction(
    val expenseId: Long,
    val amount: Double,
    val merchantName: String,
    val date: Long,
    val suggestedCategory: String,
    val suggestedSubCategory: String?,
    val confidence: Double,
    val fullSmsMessage: String?,
    val transactionType: String = "DEBIT"
) : Parcelable
