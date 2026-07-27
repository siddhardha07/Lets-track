package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User category corrections - used for learning and improving ML model
 */
@Entity(tableName = "user_category_corrections")
data class UserCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantName: String,
    val transactionAmount: Double,
    val originalCategory: String,
    val originalSubCategory: String?,
    val originalConfidence: Double,
    val correctedCategory: String,
    val correctedSubCategory: String?,
    val wasAccepted: Boolean, // true if user clicked "Yes", false if "No" and corrected
    val timestamp: Long = System.currentTimeMillis()
)
