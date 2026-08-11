package com.letstrack.app.sms

import android.util.Log
import com.letstrack.app.data.local.dao.BankAccountDao
import com.letstrack.app.data.local.dao.ExpenseDao
import com.letstrack.app.data.local.dao.SmsTransactionDao
import com.letstrack.app.data.local.entity.ExpenseEntity
import com.letstrack.app.data.local.entity.SmsTransactionEntity
import com.letstrack.app.ml.SmartCategorizer
import com.letstrack.app.service.TransactionReviewService
import com.letstrack.app.domain.model.PendingTransaction
import com.letstrack.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val smsParser: SmsParser,
    private val smartCategorizer: SmartCategorizer,
    private val transactionReviewService: TransactionReviewService,
    private val categoryRepository: CategoryRepository
) {

    companion object {
        private const val TAG = "SmsProcessor"
        private const val CONFIDENCE_THRESHOLD = 60.0 // Below this, transaction needs review
    }

    // The real-time broadcast receiver, a pull-to-refresh catch-up scan, and a manual bulk
    // import can all call processSms for overlapping date ranges at effectively the same time.
    // Without serializing them, two callers can both run the "does this SMS already exist?"
    // check before either has committed its insert, and both proceed to create a duplicate
    // expense -- this singleton-wide lock makes the check-then-insert atomic across all callers.
    private val processingMutex = Mutex()

    /**
     * Process an incoming SMS message
     * @param isBulkImport If true, skip overlay (used during bulk import)
     */
    suspend fun processSms(sender: String, message: String, timestamp: Long, isBulkImport: Boolean = false): Boolean =
        processingMutex.withLock {
        try {
            Log.d(TAG, "Processing SMS from $sender at $timestamp")

            // Parse the SMS - needed before dedup now, since amount+balance is the primary
            // dedup signal (see below).
            val parsed = smsParser.parseSms(message, sender)

            // Check for duplicate - but allow re-import if the expense was deleted.
            //
            // Matching on (amount, resulting balance) instead of message text: two distinct
            // real transactions landing on the exact same amount AND leaving the account at
            // the exact same balance is practically impossible, whereas matching on the raw
            // message text breaks the moment a bank tweaks wording/whitespace between two
            // otherwise-identical re-sends, or a re-scan reads the message back slightly
            // differently than the live broadcast did. Falls back to the old message+timestamp
            // check only when a message doesn't carry a balance (e.g. some credit alerts don't
            // mention one), since amount alone isn't a safe dedup key on its own - plenty of
            // unrelated transactions share the same amount.
            val existingSms = if (parsed.amount != null && parsed.balance != null) {
                smsTransactionDao.findDuplicateByAmountAndBalance(parsed.amount, parsed.balance, timestamp)
            } else {
                smsTransactionDao.findDuplicateSms(message, timestamp)
            }
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

                // Auto-create expense if confidence is high enough, message
                if (parsed.confidence >= CONFIDENCE_THRESHOLD && parsed.amount != null && parsed.transactionType != null) {
                    val expenseId = createExpenseFromSms(parsed, matchingAccount, timestamp, id, message, isBulkImport)
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
     * Create expense with AI categorization
     */
    private suspend fun createExpenseFromSms(
        parsed: SmsParser.ParsedSms,
        matchingAccount: com.letstrack.app.data.local.entity.BankAccountEntity?,
        timestamp: Long,
        smsId: Long,
        fullSmsMessage: String,
        isBulkImport: Boolean
    ): Long {
        return try {
            val merchantName = parsed.merchant ?: "Bank Transaction"
            val amount = parsed.amount ?: 0.0

            // Use AI to predict category
            val prediction = smartCategorizer.categorize(
                merchantName = merchantName,
                amount = amount,
                transactionType = parsed.transactionType ?: "DEBIT",
                message = fullSmsMessage
            )

            Log.d(TAG, "AI Prediction: ${prediction.category} (${(prediction.confidence * 100).toInt()}% confidence)")

            // Look up category ID by name. Falling back to a hardcoded id (previously 1L,
            // i.e. always "Food" regardless of what the message actually was) meant any
            // lookup miss silently mis-categorized the expense. Fall back to "Other" by
            // name instead, and only as a last resort to whatever category happens to
            // exist first - never to a magic number that may not even exist.
            val categoryId = getCategoryIdByName(prediction.category)
                ?: getCategoryIdByName("Other")
                ?: categoryRepository.getAllCategories().first().firstOrNull()?.id
                ?: 0L

            // Create expense entity with AI prediction
            val expenseEntity = ExpenseEntity(
                amount = amount,
                categoryId = categoryId,
                title = merchantName,
                description = parsed.fullSmsMessage ?: fullSmsMessage,
                notes = "Auto-imported from SMS",
                date = timestamp,
                source = "SMS",
                sourceReference = "SMS ID: $smsId",
                merchantName = merchantName,
                bankReference = matchingAccount?.accountNumber ?: parsed.accountNumber ?: "0000",
                transactionType = parsed.transactionType ?: "DEBIT",
                balanceAfterTransaction = parsed.balance,
                // AI Categorization fields
                subCategory = prediction.subCategory,
                confidenceScore = prediction.confidence * 100,
                isAiCategorized = true,
                categorizationSource = when {
                    prediction.confidence >= 0.9 -> "auto-high"
                    prediction.confidence >= 0.6 -> "auto-medium"
                    else -> "auto-low"
                },
                needsReview = prediction.confidence < 0.9,
                isPendingReview = prediction.confidence < 0.6,
                createdAt = System.currentTimeMillis()
            )

            val expenseId = expenseDao.insertExpense(expenseEntity)
            Log.d(TAG, "✅ Created expense ID: $expenseId - ${expenseEntity.transactionType} Rs.${expenseEntity.amount} at ${expenseEntity.title}")
            Log.d(TAG, "🎯 Checking confidence level: ${prediction.confidence} (${prediction.confidenceLevel})")

            // Trigger overlay based on confidence level (skip if bulk import)
            when (prediction.confidenceLevel) {
                com.letstrack.app.domain.model.CategoryPrediction.ConfidenceLevel.HIGH -> {
                    if (isBulkImport) {
                        // During bulk import, auto-categorize without overlay
                        Log.d(TAG, "📦 BULK IMPORT - HIGH confidence (${prediction.confidence}) - auto-categorized as ${prediction.category}")
                    } else {
                        // Real-time: Even high confidence should show confirmation overlay
                        Log.d(TAG, "✓ HIGH confidence (${prediction.confidence}) - SHOWING OVERLAY for confirmation")
                        transactionReviewService.showReview(
                            PendingTransaction(
                                expenseId = expenseId,
                                amount = amount,
                                merchantName = merchantName,
                                date = timestamp,
                                suggestedCategory = prediction.category,
                                suggestedSubCategory = prediction.subCategory,
                                confidence = prediction.confidence,
                                fullSmsMessage = fullSmsMessage,
                                transactionType = parsed.transactionType ?: "DEBIT"
                            )
                        )
                    }
                }
                com.letstrack.app.domain.model.CategoryPrediction.ConfidenceLevel.MEDIUM,
                com.letstrack.app.domain.model.CategoryPrediction.ConfidenceLevel.LOW -> {
                    if (isBulkImport) {
                        // During bulk import, just save without overlay
                        Log.d(TAG, "📦 BULK IMPORT - Skipping overlay for ${merchantName} (${prediction.confidence}% conf)")
                    } else {
                        // Real-time transaction: Show overlay for user confirmation
                        Log.d(TAG, "⚠️ MEDIUM/LOW confidence (${prediction.confidence}) - TRIGGERING OVERLAY for ${prediction.category}")
                        Log.d(TAG, "🎯 Creating PendingTransaction for merchant: $merchantName")
                        transactionReviewService.showReview(
                            PendingTransaction(
                                expenseId = expenseId,
                                amount = amount,
                                merchantName = merchantName,
                                date = timestamp,
                                suggestedCategory = prediction.category,
                                suggestedSubCategory = prediction.subCategory,
                                confidence = prediction.confidence,
                                fullSmsMessage = fullSmsMessage,
                                transactionType = parsed.transactionType ?: "DEBIT"
                            )
                        )
                        Log.d(TAG, "🎯 OVERLAY TRIGGERED - check TransactionReviewService logs")
                    }
                }
            }

            expenseId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create expense from SMS: ${e.message}", e)
            -1
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
    /**
     * Get category ID by category name
     */
    private suspend fun getCategoryIdByName(categoryName: String): Long? {
        return try {
            com.letstrack.app.domain.model.resolveCategoryId(categoryRepository, categoryName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting category ID for '$categoryName': ${e.message}")
            null
        }
    }
}
