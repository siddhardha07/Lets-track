package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Merchant Memory Entity - Stores learned patterns about merchants
 * Used by AI to auto-categorize future transactions
 */
@Entity(tableName = "merchants")
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String, // Cleaned up name for matching
    
    // Most common category for this merchant
    val primaryCategoryId: Long,
    
    // Learning statistics
    val averageAmount: Double = 0.0,
    val frequency: String = "", // DAILY, WEEKLY, MONTHLY, YEARLY, ONE_TIME
    val timesSeen: Int = 0,
    val lastSeenDate: Long = 0,
    
    // Pattern matching data
    val upiHandles: String = "", // Comma-separated list of UPI IDs
    val keywords: String = "", // Comma-separated keywords for matching
    
    // Confidence
    val confidence: Double = 0.0, // 0-100
    
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
