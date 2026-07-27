package com.letstrack.app.data.local.dao

import androidx.room.*
import com.letstrack.app.data.local.entity.PendingReviewTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingReviewDao {

    @Query("SELECT * FROM pending_review_transactions ORDER BY createdAt DESC")
    fun getAllPendingReviews(): Flow<List<PendingReviewTransactionEntity>>

    @Query("SELECT * FROM pending_review_transactions WHERE expenseId = :expenseId LIMIT 1")
    suspend fun getPendingReviewByExpenseId(expenseId: Long): PendingReviewTransactionEntity?

    @Query("SELECT COUNT(*) FROM pending_review_transactions")
    fun getPendingCount(): Flow<Int>

    @Insert
    suspend fun insert(pendingReview: PendingReviewTransactionEntity): Long

    @Update
    suspend fun update(pendingReview: PendingReviewTransactionEntity)

    @Query("DELETE FROM pending_review_transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_review_transactions WHERE expenseId = :expenseId")
    suspend fun deleteByExpenseId(expenseId: Long)

    @Query("DELETE FROM pending_review_transactions")
    suspend fun deleteAll()
}
