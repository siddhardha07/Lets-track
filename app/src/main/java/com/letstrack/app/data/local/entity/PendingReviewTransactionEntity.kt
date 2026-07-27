package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Pending review transactions - transactions marked for "Review Later"
 */
@Entity(tableName = "pending_review_transactions")
data class PendingReviewTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val merchantName: String,
    val amount: Double,
    val suggestedCategory: String,
    val suggestedSubCategory: String?,
    val confidence: Double,
    val fullSmsMessage: String?,
    val createdAt: Long = System.currentTimeMillis()
)
