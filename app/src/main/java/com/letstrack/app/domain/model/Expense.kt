package com.letstrack.app.domain.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class Expense(
    val id: Long = 0,
    val amount: Double,
    val categoryId: Long,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val date: Long = Instant.now().toEpochMilli(),
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
    
    // Source tracking
    val source: String = "MANUAL",
    val sourceReference: String = "",
    
    // Merchant info
    val merchantName: String = "",
    val merchantId: Long? = null,
    val upiId: String = "",
    val bankReference: String = "",
    
    // AI features
    val aiSuggestedCategoryId: Long? = null,
    val confidenceScore: Double = 0.0,
    val isAiCategorized: Boolean = false,
    val needsReview: Boolean = false,
    
    // Transaction type
    val transactionType: String = "DEBIT"
) {
    fun getLocalDateTime(): LocalDateTime {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(date),
            ZoneId.systemDefault()
        )
    }
}
