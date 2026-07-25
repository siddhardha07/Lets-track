package com.letstrack.app.sms

import android.util.Log
import com.letstrack.app.data.local.dao.BankAccountDao
import com.letstrack.app.data.local.dao.ExpenseDao
import com.letstrack.app.data.local.dao.SmsTransactionDao
import com.letstrack.app.data.local.entity.ExpenseEntity
import com.letstrack.app.data.local.entity.SmsTransactionEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS Processor - Handles incoming SMS processing and storage
 */
@Singleton
class SmsProcessor @Inject constructor(
    private val smsTransactionDao: SmsTransactionDao,
    private val bankAccountDao: BankAccountDao,
    private val expenseDao: ExpenseDao,
    private val smsParser: SmsParser
) {

    companion object {
        private const val TAG = "SmsProcessor"
        private const val CONFIDENCE_THRESHOLD = 60.0 // Below this, transaction needs review
    }

    /**
     * Process an incoming SMS message
     */
    suspend fun processSms(sender: String, message: String, timestamp: Long): Boolean {
        try {
            Log.d(TAG, "Processing SMS from $sender at $timestamp")

            // Check for duplicate - but allow re-import if the expense was deleted
            val existingSms = smsTransactionDao.findDuplicateSms(message, timestamp)
            if (existingSms != null && existingSms.isMatched) {
                // Check if the matched expense still exists
                val expenseExists = existingSms.matchedExpenseId?.let { expenseId ->
                    expenseDao.getExpenseById(expenseId) != null
                }

                if (expenseExists == true) {
                    Log.d(TAG, "Duplicate SMS detected with existing expense, skipping")
                    return false
                } else {
                    Log.d(TAG, "SMS exists but expense was deleted, allowing re-import")
                    // Continue processing to create new expense
                }
            }

            // Parse the SMS
            val parsed = smsParser.parseSms(message, sender)

            // Check if we have a matching bank account
            val matchingAccount = findMatchingAccount(sender, parsed.accountNumber)

            // Create SMS transaction entity
            val smsEntity = SmsTransactionEntity(
                sender = sender,
                message = message,
                timestamp = timestamp,
                extractedAmount = parsed.amount,
                extractedMerchant = parsed.merchant,
                transactionType = parsed.transactionType,
                cardType = parsed.cardType,
                accountNumber = parsed.accountNumber,
                extractedBalance = parsed.balance,
                isParsed = parsed.confidence >= CONFIDENCE_THRESHOLD,
                isMatched = false,
                createdAt = System.currentTimeMillis()
            )

            // Save to database
            val id = smsTransactionDao.insertSms(smsEntity)

            if (id > 0) {
                Log.d(TAG, "SMS saved successfully with ID: $id, confidence: ${parsed.confidence}%")

                // Update bank account usage statistics
                if (matchingAccount != null) {
                    updateAccountStats(matchingAccount.id, timestamp)
                }

                // Auto-create expense if confidence is high enough
                if (parsed.confidence >= CONFIDENCE_THRESHOLD && parsed.amount != null && parsed.transactionType != null) {
                    val expenseId = createExpenseFromSms(parsed, matchingAccount, timestamp, id)
                    if (expenseId > 0) {
                        // Mark SMS as matched
                        smsTransactionDao.markAsMatched(id, expenseId)
                        Log.d(TAG, "Auto-created expense entry with ID: $expenseId")
                    }
                }

                return true
            } else {
                Log.w(TAG, "Failed to save SMS")
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS: ${e.message}", e)
            return false
        }
    }

    /**
     * Create expense entry from parsed SMS
     */
    private suspend fun createExpenseFromSms(
        parsed: SmsParser.ParsedSms,
        matchingAccount: com.letstrack.app.data.local.entity.BankAccountEntity?,
        timestamp: Long,
        smsId: Long
    ): Long {
        return try {
            val expenseEntity = ExpenseEntity(
                amount = parsed.amount ?: 0.0,
                categoryId = 1L, // Default category - will be properly categorized later
                title = parsed.merchant ?: "Bank Transaction",
                description = parsed.fullSmsMessage ?: "${parsed.cardType ?: "Bank"} transaction",
                notes = "Auto-imported from SMS",
                date = timestamp,
                source = "SMS",
                sourceReference = "SMS ID: $smsId",
                merchantName = parsed.merchant ?: "Unknown",
                bankReference = matchingAccount?.accountNumber ?: parsed.accountNumber ?: "0000",
                transactionType = parsed.transactionType ?: "DEBIT",
                balanceAfterTransaction = parsed.balance,
                isAiCategorized = true,
                confidenceScore = parsed.confidence,
                needsReview = parsed.confidence < CONFIDENCE_THRESHOLD,
                createdAt = System.currentTimeMillis()
            )

            val expenseId = expenseDao.insertExpense(expenseEntity)
            Log.d(TAG, "Created expense: ${expenseEntity.transactionType} Rs.${expenseEntity.amount} at ${expenseEntity.title}")
            expenseId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create expense from SMS: ${e.message}", e)
            -1
        }
    }

    /**
     * Determine category based on merchant name and transaction type
     */
    private fun determineCategory(merchant: String?, cardType: String?): String {
        // Simple heuristic-based categorization
        val lowerMerchant = merchant?.lowercase() ?: ""

        return when {
            // Food & Dining
            lowerMerchant.contains("swiggy") || lowerMerchant.contains("zomato") ||
            lowerMerchant.contains("restaurant") || lowerMerchant.contains("cafe") ||
            lowerMerchant.contains("food") -> "Food & Dining"

            // Shopping
            lowerMerchant.contains("amazon") || lowerMerchant.contains("flipkart") ||
            lowerMerchant.contains("myntra") || lowerMerchant.contains("store") ||
            lowerMerchant.contains("shop") -> "Shopping"

            // Transport
            lowerMerchant.contains("uber") || lowerMerchant.contains("ola") ||
            lowerMerchant.contains("rapido") || lowerMerchant.contains("petrol") ||
            lowerMerchant.contains("fuel") -> "Transport"

            // Bills & Utilities
            lowerMerchant.contains("electric") || lowerMerchant.contains("water") ||
            lowerMerchant.contains("gas") || lowerMerchant.contains("recharge") ||
            lowerMerchant.contains("bill") -> "Bills & Utilities"

            // Entertainment
            lowerMerchant.contains("netflix") || lowerMerchant.contains("prime") ||
            lowerMerchant.contains("hotstar") || lowerMerchant.contains("spotify") ||
            lowerMerchant.contains("movie") -> "Entertainment"

            // ATM withdrawal
            cardType == "ATM" -> "Cash"

            // UPI transfer
            cardType == "UPI" -> "Transfer"

            // Default
            else -> "Other"
        }
    }

    /**
     * Find matching bank account for the SMS
     */
    private suspend fun findMatchingAccount(sender: String, accountNumber: String?): com.letstrack.app.data.local.entity.BankAccountEntity? {
        if (accountNumber == null) return null

        // Get all active accounts
        val accounts = bankAccountDao.getAllAccounts()

        // Try to match by account number
        val matchedByAccount = accounts.find { account ->
            accountNumber.endsWith(account.accountNumber) ||
            account.accountNumber.endsWith(accountNumber)
        }

        if (matchedByAccount != null) return matchedByAccount

        // Try to match by sender pattern
        val matchedBySender = accounts.find { account ->
            val senderPatterns = try {
                // Parse JSON array of sender patterns
                account.senderPatterns
                    .trim('[', ']')
                    .split(",")
                    .map { it.trim('"', ' ') }
            } catch (e: Exception) {
                emptyList()
            }

            senderPatterns.any { pattern ->
                sender.contains(pattern, ignoreCase = true)
            }
        }

        return matchedBySender
    }

    /**
     * Update bank account statistics
     */
    private suspend fun updateAccountStats(accountId: Long, lastSmsDate: Long) {
        try {
            val account = bankAccountDao.getAccountById(accountId)
            if (account != null) {
                val updated = account.copy(
                    smsProcessedCount = account.smsProcessedCount + 1,
                    lastSmsDate = lastSmsDate,
                    lastUsedAt = System.currentTimeMillis()
                )
                bankAccountDao.updateAccount(updated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating account stats: ${e.message}", e)
        }
    }
}
