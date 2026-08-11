package com.letstrack.app.domain.repository

import com.letstrack.app.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getAllBudgets(): Flow<List<Budget>>
    suspend fun setOverallBudget(amount: Double)
    suspend fun setCategoryBudget(categoryId: Long, amount: Double)
    suspend fun clearOverallBudget()
    suspend fun clearCategoryBudget(categoryId: Long)
}
