package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Merchant category database - stores known merchants and their categories
 * Sources: built-in, Wikipedia API, user corrections
 */
@Entity(tableName = "merchant_categories")
data class MerchantCategoryEntity(
    @PrimaryKey val merchantName: String,
    val mainCategory: String,
    val subCategory: String?,
    val confidence: Double, // 0.0 to 1.0
    val source: String, // "built-in", "wikipedia", "user-correction", "ml-model"
    val lastUsed: Long,
    val usageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
