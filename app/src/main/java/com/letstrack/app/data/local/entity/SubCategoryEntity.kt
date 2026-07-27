package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sub-categories for better expense classification
 * Example: Food -> Groceries, Restaurants, Food Delivery
 */
@Entity(tableName = "sub_categories")
data class SubCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mainCategory: String,
    val subCategoryName: String,
    val icon: String? = null, // emoji or icon name
    val isDefault: Boolean = false, // pre-defined sub-categories
    val displayOrder: Int = 0
)
