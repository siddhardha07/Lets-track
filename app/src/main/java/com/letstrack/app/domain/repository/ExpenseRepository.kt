package com.letstrack.app.domain.repository

import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun getExpenseById(id: Long): Expense?
    fun getExpensesByCategory(categoryId: Long): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense): Long
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)
    suspend fun deleteExpenseById(id: Long)
    suspend fun deleteAllExpenses()
    fun getTotalExpenses(): Flow<Double>
    fun getTotalExpensesInRange(startDate: Long, endDate: Long): Flow<Double>
}
