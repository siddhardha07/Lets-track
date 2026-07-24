package com.letstrack.app.data.repository

import com.letstrack.app.data.local.dao.ExpenseDao
import com.letstrack.app.data.local.entity.ExpenseEntity
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<Expense>> {
        return expenseDao.getAllExpenses().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getExpenseById(id: Long): Expense? {
        return expenseDao.getExpenseById(id)?.toDomainModel()
    }

    override fun getExpensesByCategory(categoryId: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesByCategory(categoryId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense.toEntity())
    }

    override suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense.toEntity())
    }

    override suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense.toEntity())
    }

    override suspend fun deleteExpenseById(id: Long) {
        expenseDao.deleteExpenseById(id)
    }
    
    override suspend fun deleteAllExpenses() {
        expenseDao.deleteAllExpenses()
    }

    override fun getTotalExpenses(): Flow<Double> {
        return expenseDao.getTotalExpenses().map { it ?: 0.0 }
    }

    override fun getTotalExpensesInRange(startDate: Long, endDate: Long): Flow<Double> {
        return expenseDao.getTotalExpensesInRange(startDate, endDate).map { it ?: 0.0 }
    }

    private fun ExpenseEntity.toDomainModel() = Expense(
        id = id,
        amount = amount,
        categoryId = categoryId,
        title = title,
        description = description,
        notes = notes,
        date = date,
        createdAt = createdAt,
        updatedAt = updatedAt,
        source = source,
        sourceReference = sourceReference,
        merchantName = merchantName,
        merchantId = merchantId,
        upiId = upiId,
        bankReference = bankReference,
        aiSuggestedCategoryId = aiSuggestedCategoryId,
        confidenceScore = confidenceScore,
        isAiCategorized = isAiCategorized,
        needsReview = needsReview,
        transactionType = transactionType,
        balanceAfterTransaction = balanceAfterTransaction
    )

    private fun Expense.toEntity() = ExpenseEntity(
        id = id,
        amount = amount,
        categoryId = categoryId,
        title = title,
        description = description,
        notes = notes,
        date = date,
        createdAt = createdAt,
        updatedAt = updatedAt,
        source = source,
        sourceReference = sourceReference,
        merchantName = merchantName,
        merchantId = merchantId,
        upiId = upiId,
        bankReference = bankReference,
        aiSuggestedCategoryId = aiSuggestedCategoryId,
        confidenceScore = confidenceScore,
        isAiCategorized = isAiCategorized,
        needsReview = needsReview,
        transactionType = transactionType,
        balanceAfterTransaction = balanceAfterTransaction
    )
}
