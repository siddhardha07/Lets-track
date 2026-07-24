package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val categoryId: Long,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val date: Long = Instant.now().toEpochMilli(),
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
    
    // Source tracking for AI/Import features
    val source: String = "MANUAL", // MANUAL, SMS, PDF, CSV, EMAIL
    val sourceReference: String = "", // Original SMS/PDF data for reference
    
    // Merchant and AI features
    val merchantName: String = "",
    val merchantId: Long? = null, // FK to merchant table
    val upiId: String = "", // UPI transaction ID if available
    val bankReference: String = "", // Bank ref/cheque number
    
    // AI Categorization
    val aiSuggestedCategoryId: Long? = null,
    val confidenceScore: Double = 0.0, // 0-100
    val isAiCategorized: Boolean = false,
    val needsReview: Boolean = false,
    
    // Transaction type
    val transactionType: String = "DEBIT", // DEBIT or CREDIT
)
