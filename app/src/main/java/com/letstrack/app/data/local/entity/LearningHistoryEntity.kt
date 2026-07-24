package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI Learning History - Track user corrections to improve categorization
 */
@Entity(tableName = "learning_history")
data class LearningHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val expenseId: Long,
    
    // What AI suggested
    val suggestedCategoryId: Long,
    val suggestedMerchantId: Long? = null,
    val confidenceScore: Double,
    
    // What user chose
    val actualCategoryId: Long,
    val actualMerchantId: Long? = null,
    
    // Context for learning
    val transactionAmount: Double,
    val transactionDescription: String,
    val merchantName: String,
    
    val timestamp: Long
)
