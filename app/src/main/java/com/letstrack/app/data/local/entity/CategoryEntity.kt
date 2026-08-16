package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Unique on name so the two independent default-category seeders (DatabaseCallback's
// onCreate, and LetsTrackApp's seedDefaultCategoriesIfEmpty fallback for upgraded installs
// that never got that onCreate callback) can never produce two rows for the same category --
// see CategoryDao's OnConflictStrategy.IGNORE, which relies on this constraint.
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String = "💰",
    val color: String = "#4CAF50",
    val isDefault: Boolean = false
)
