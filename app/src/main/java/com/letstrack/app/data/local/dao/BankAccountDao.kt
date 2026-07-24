package com.letstrack.app.data.local.dao

import androidx.room.*
import com.letstrack.app.data.local.entity.BankAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDao {
    
    @Query("SELECT * FROM bank_accounts WHERE isActive = 1 ORDER BY lastUsedAt DESC")
    fun getAllActiveAccounts(): Flow<List<BankAccountEntity>>
    
    @Query("SELECT * FROM bank_accounts WHERE isActive = 1")
    suspend fun getAllAccounts(): List<BankAccountEntity>
    
    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): BankAccountEntity?
    
    @Query("SELECT * FROM bank_accounts WHERE accountNumber = :accountNumber AND isActive = 1")
    suspend fun getAccountByNumber(accountNumber: String): BankAccountEntity?
    
    @Query("SELECT * FROM bank_accounts WHERE isActive = 1")
    suspend fun getActiveAccountsList(): List<BankAccountEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: BankAccountEntity): Long
    
    @Update
    suspend fun updateAccount(account: BankAccountEntity)
    
    @Query("UPDATE bank_accounts SET isActive = 0 WHERE id = :id")
    suspend fun deactivateAccount(id: Long)
    
    @Query("UPDATE bank_accounts SET lastUsedAt = :timestamp, smsProcessedCount = smsProcessedCount + 1, lastSmsDate = :smsDate WHERE id = :id")
    suspend fun updateAccountUsage(id: Long, timestamp: Long, smsDate: Long)
    
    @Query("DELETE FROM bank_accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)
    
    @Query("SELECT COUNT(*) FROM bank_accounts WHERE isActive = 1")
    suspend fun getActiveAccountCount(): Int
}
