package com.letstrack.app.domain.repository

import com.letstrack.app.domain.model.BankAccount
import kotlinx.coroutines.flow.Flow

interface BankAccountRepository {
    fun getAllActiveAccounts(): Flow<List<BankAccount>>
    suspend fun getAccountById(id: Long): BankAccount?
    suspend fun getAccountByNumber(accountNumber: String): BankAccount?
    suspend fun getActiveAccountsList(): List<BankAccount>
    suspend fun insertAccount(account: BankAccount): Long
    suspend fun updateAccount(account: BankAccount)
    suspend fun deactivateAccount(id: Long)
    suspend fun updateAccountUsage(id: Long, timestamp: Long, smsDate: Long)
    suspend fun deleteAccount(id: Long)
    suspend fun getActiveAccountCount(): Int
}
