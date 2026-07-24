package com.letstrack.app.data.local.dao

import androidx.room.*
import com.letstrack.app.data.local.entity.SmsTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTransactionDao {
    
    @Query("SELECT * FROM sms_transactions ORDER BY timestamp DESC")
    fun getAllSmsTransactions(): Flow<List<SmsTransactionEntity>>
    
    @Query("SELECT * FROM sms_transactions WHERE isParsed = 0 ORDER BY timestamp DESC")
    fun getUnparsedSms(): Flow<List<SmsTransactionEntity>>
    
    @Query("SELECT * FROM sms_transactions WHERE isMatched = 0 AND isParsed = 1 ORDER BY timestamp DESC")
    fun getUnmatchedSms(): Flow<List<SmsTransactionEntity>>
    
    @Query("SELECT * FROM sms_transactions WHERE sender = :sender ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSmsBySender(sender: String, limit: Int = 100): List<SmsTransactionEntity>
    
    @Query("SELECT * FROM sms_transactions WHERE accountNumber = :accountLast4 AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getSmsForAccount(accountLast4: String, startTime: Long, endTime: Long): List<SmsTransactionEntity>
    
    @Query("""
        SELECT * FROM sms_transactions 
        WHERE message = :message AND timestamp = :timestamp
        LIMIT 1
    """)
    suspend fun findDuplicateSms(message: String, timestamp: Long): SmsTransactionEntity?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSms(sms: SmsTransactionEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(smsList: List<SmsTransactionEntity>): List<Long>
    
    @Update
    suspend fun updateSms(sms: SmsTransactionEntity)
    
    @Query("UPDATE sms_transactions SET isMatched = 1, matchedExpenseId = :expenseId WHERE id = :smsId")
    suspend fun markAsMatched(smsId: Long, expenseId: Long)
    
    @Query("DELETE FROM sms_transactions WHERE id = :id")
    suspend fun deleteSms(id: Long)
    
    @Query("DELETE FROM sms_transactions")
    suspend fun deleteAllSms()
    
    @Query("SELECT COUNT(*) FROM sms_transactions")
    fun getSmsCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM sms_transactions WHERE isParsed = 1")
    suspend fun getParsedCount(): Int
    
    @Query("SELECT COUNT(*) FROM sms_transactions WHERE isMatched = 1")
    suspend fun getMatchedCount(): Int
}
