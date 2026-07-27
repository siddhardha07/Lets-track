package com.letstrack.app.data.local.dao

import androidx.room.*
import com.letstrack.app.data.local.entity.UserCorrectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCorrectionDao {

    @Query("SELECT * FROM user_category_corrections ORDER BY timestamp DESC")
    fun getAllCorrections(): Flow<List<UserCorrectionEntity>>

    @Query("SELECT * FROM user_category_corrections WHERE merchantName = :merchantName ORDER BY timestamp DESC")
    suspend fun getCorrectionsByMerchant(merchantName: String): List<UserCorrectionEntity>

    @Query("SELECT * FROM user_category_corrections WHERE correctedCategory = :category ORDER BY timestamp DESC")
    suspend fun getCorrectionsByCategory(category: String): List<UserCorrectionEntity>

    @Query("SELECT * FROM user_category_corrections WHERE wasAccepted = :wasAccepted ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getCorrections(wasAccepted: Boolean, limit: Int = 100): List<UserCorrectionEntity>

    @Insert
    suspend fun insert(correction: UserCorrectionEntity): Long

    @Insert
    suspend fun insertAll(corrections: List<UserCorrectionEntity>)

    @Query("DELETE FROM user_category_corrections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM user_category_corrections")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM user_category_corrections")
    suspend fun getCount(): Int
}
