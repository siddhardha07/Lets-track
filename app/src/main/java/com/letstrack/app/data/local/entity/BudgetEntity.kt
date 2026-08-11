package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A monthly spending limit. [categoryId] null means this is the overall budget (all spending
 * combined, across every account); non-null means it's a per-category limit. There's at most one
 * row per categoryId (including at most one with a null categoryId) -- enforced by
 * BudgetRepositoryImpl doing a find-then-replace instead of a bare insert, not by a DB constraint,
 * matching how the rest of this app's entities are kept simple.
 *
 * Monthly-only by design (no stored period/start-date): the amount is a standing limit, and
 * "this month" is always computed fresh from the current calendar date wherever it's checked
 * (see BudgetStatusProvider), so nothing here needs to roll over or reset.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long? = null,
    val amount: Double,
    val updatedAt: Long = System.currentTimeMillis()
)
