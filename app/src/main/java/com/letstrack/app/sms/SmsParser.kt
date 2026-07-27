package com.letstrack.app.sms

import android.util.Log
import com.letstrack.app.data.local.entity.SmsTransactionEntity
import java.util.regex.Pattern

/**
 * SMS Parser - Extracts transaction details from bank SMS
 * Uses patterns learned from sample messages
 */
class SmsParser {

    companion object {
        private const val TAG = "SmsParser"

        // Common patterns for Indian bank SMS
        private val AMOUNT_PATTERNS = listOf(
            "Rs\\.?\\s*([\\d,]+(?:\\.\\d{2})?)",
            "INR\\s*([\\d,]+(?:\\.\\d{2})?)",
            "Amount:\\s*Rs\\.?\\s*([\\d,]+(?:\\.\\d{2})?)",
            "(?:debited|credited|withdrawn|deposited|spent).*?Rs\\.?\\s*([\\d,]+(?:\\.\\d{2})?)"
        )

        private val DATE_PATTERNS = listOf(
            "(\\d{2}-\\d{2}-\\d{4})",
            "(\\d{2}/\\d{2}/\\d{4})",
            "(\\d{2}\\.\\d{2}\\.\\d{4})",
            "on\\s+(\\d{2}-\\d{2}-\\d{2})"
        )

        private val ACCOUNT_PATTERNS = listOf(
            "A/[Cc]\\s*(?:no\\.?)?\\s*[Xx]*?(\\d{4,6})",
            "account\\s*(?:no\\.?)?\\s*[Xx]*?(\\d{4,6})",
            "card\\s*(?:no\\.?)?\\s*[Xx]*?(\\d{4})",
            "ending\\s*(\\d{4})"
        )

        private val BALANCE_PATTERNS = listOf(
            // Pattern 1: balance followed by Rs or INR
            "(?:balance|bal|avl\\.?\\s*bal).*?(?:Rs\\.?|INR)\\s*([\\d,]+(?:\\.\\d{2})?)",
            // Pattern 2: balance with colon separator
            "(?:balance|bal):\\s*(?:Rs\\.?|INR)?\\s*([\\d,]+(?:\\.\\d{2})?)",
            // Pattern 3: "available balance" or "avl bal" variations
            "(?:available|avl)\\s+(?:balance|bal).*?(?:Rs\\.?|INR)?\\s*([\\d,]+(?:\\.\\d{2})?)",
            // Pattern 4: Credit SMS format - "INR 123.45 and avl bal"
            "(?:INR|Rs\\.?)\\s*[\\d,]+(?:\\.\\d{2})?.*?(?:balance|bal).*?(?:Rs\\.?|INR)\\s*([\\d,]+(?:\\.\\d{2})?)"
        )

        private val MERCHANT_PATTERNS = listOf(
            // Pattern 1: "at MERCHANT" or "to MERCHANT"
            "(?:to|at)\\s+([A-Z][A-Z\\s]{2,30})",
            // Pattern 2: "UPI ID"
            "(?:VPA|UPI)\\s+([a-z0-9@\\.]+)",
            // Pattern 3: "credited from MERCHANT" or "paid to MERCHANT"
            "(?:credited from|paid to)\\s+([A-Z][A-Z\\s]{2,30})",
            // Pattern 4: IDFC format - "; MERCHANT credited|debited"
            ";\\s+([A-Z][A-Z\\s]{2,30})\\s+(?:credited|debited)",
            // Pattern 5: "for MERCHANT" or "on MERCHANT"
            "(?:for|on)\\s+([A-Z][A-Z0-9\\s]{3,30})",
            // Pattern 6: After "by" - "debited by Rs X for MERCHANT"
            "(?:debited|credited).*?(?:at|to|for)\\s+([A-Z][A-Z\\s]{3,30})"
        )

        private val DEBIT_KEYWORDS = listOf(
            "debited", "debit", "withdrawn", "withdrawal", "spent",
            "purchase", "paid", "payment", "transfer", "sent"
        )

        private val CREDIT_KEYWORDS = listOf(
            "credited", "credit", "deposited", "deposit", "received",
            "refund", "cashback", "salary", "transfer from"
        )

        private val CARD_TYPE_KEYWORDS = mapOf(
            "UPI" to listOf("UPI", "IMPS", "VPA"),
            "CARD" to listOf("card", "POS", "swipe"),
            "NEFT" to listOf("NEFT"),
            "RTGS" to listOf("RTGS"),
            "ATM" to listOf("ATM", "cash withdrawal"),
            "CHEQUE" to listOf("cheque", "check")
        )
    }

    data class ParsedSms(
        val amount: Double?,
        val transactionType: String?, // DEBIT or CREDIT
        val merchant: String?,
        val accountNumber: String?,
        val balance: Double?,
        val cardType: String?,
        val date: String?,
        val confidence: Double, // 0-100
        val fullSmsMessage: String? = null // Store complete SMS for reference
    )

    /**
     * Parse SMS message and extract transaction details
     */
    fun parseSms(message: String, sender: String): ParsedSms {
        Log.d(TAG, "Parsing SMS from $sender: ${message.take(100)}...")

        // Check if this is a warning/notification (not an actual transaction)
        if (isWarningMessage(message)) {
            Log.d(TAG, "Detected warning/notification SMS, skipping")
            return ParsedSms(
                amount = null,
                merchant = null,
                transactionType = null,
                accountNumber = null,
                balance = null,
                cardType = null,
                date = null,
                confidence = 0.0,
                fullSmsMessage = message
            )
        }

        var confidenceScore = 0.0
        val maxScore = 7.0 // Total possible score

        // Extract amount
        val amount = extractAmount(message)
        if (amount != null) confidenceScore += 2.0

        // Determine transaction type
        val type = determineTransactionType(message)
        if (type != null) confidenceScore += 2.0

        // Extract account number
        val accountNumber = extractAccountNumber(message)
        if (accountNumber != null) confidenceScore += 1.0

        // Extract merchant/payee
        val merchant = extractMerchant(message)
        if (merchant != null) confidenceScore += 0.5

        // Extract balance
        val balance = extractBalance(message)
        if (balance != null) confidenceScore += 0.5

        // Determine card type
        val cardType = determineCardType(message)
        if (cardType != null) confidenceScore += 0.5

        // Extract date
        val date = extractDate(message)
        if (date != null) confidenceScore += 0.5

        val confidence = (confidenceScore / maxScore) * 100

        Log.d(TAG, "Parsed: amount=$amount, type=$type, account=$accountNumber, confidence=$confidence%")

        return ParsedSms(
            amount = amount,
            transactionType = type,
            merchant = merchant,
            accountNumber = accountNumber,
            balance = balance,
            cardType = cardType,
            date = date,
            confidence = confidence,
            fullSmsMessage = message // Store the full SMS for reference
        )
    }

    private fun extractAmount(message: String): Double? {
        for (patternStr in AMOUNT_PATTERNS) {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(message)

            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "")
                return amountStr?.toDoubleOrNull()
            }
        }
        return null
    }

    private fun determineTransactionType(message: String): String? {
        val lowerMessage = message.lowercase()

        val hasDebit = DEBIT_KEYWORDS.any { lowerMessage.contains(it) }
        val hasCredit = CREDIT_KEYWORDS.any { lowerMessage.contains(it) }

        return when {
            hasDebit && !hasCredit -> "DEBIT"
            hasCredit && !hasDebit -> "CREDIT"
            hasDebit && hasCredit -> {
                // Check which comes first
                val debitIndex = DEBIT_KEYWORDS.mapNotNull {
                    lowerMessage.indexOf(it).takeIf { it >= 0 }
                }.minOrNull() ?: Int.MAX_VALUE

                val creditIndex = CREDIT_KEYWORDS.mapNotNull {
                    lowerMessage.indexOf(it).takeIf { it >= 0 }
                }.minOrNull() ?: Int.MAX_VALUE

                if (debitIndex < creditIndex) "DEBIT" else "CREDIT"
            }
            else -> null
        }
    }

    private fun extractAccountNumber(message: String): String? {
        for (patternStr in ACCOUNT_PATTERNS) {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(message)

            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private fun extractMerchant(message: String): String? {
        for (patternStr in MERCHANT_PATTERNS) {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(message)

            if (matcher.find()) {
                val merchant = matcher.group(1)?.trim()
                if (merchant != null && merchant.length > 2) {
                    Log.d(TAG, "Extracted merchant: $merchant using pattern: $patternStr")
                    return merchant
                }
            }
        }

        // Fallback: Extract any capitalized words after common keywords
        val fallbackPattern = "(?:debited|credited).*?([A-Z]{3,}(?:\\s+[A-Z]{3,})?)"
        val fallbackMatcher = Pattern.compile(fallbackPattern).matcher(message)
        if (fallbackMatcher.find()) {
            val merchant = fallbackMatcher.group(1)?.trim()
            if (merchant != null && merchant.length > 2 && !merchant.matches("RRN|UPI|NEFT|RTGS|INR|AVL|BAL|AC".toRegex())) {
                Log.d(TAG, "Extracted merchant (fallback): $merchant")
                return merchant
            }
        }

        Log.d(TAG, "No merchant found in message")
        return null
    }

    private fun extractBalance(message: String): Double? {
        for (patternStr in BALANCE_PATTERNS) {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(message)

            if (matcher.find()) {
                val balanceStr = matcher.group(1)?.replace(",", "")
                return balanceStr?.toDoubleOrNull()
            }
        }
        return null
    }

    private fun determineCardType(message: String): String? {
        val lowerMessage = message.lowercase()

        for ((type, keywords) in CARD_TYPE_KEYWORDS) {
            if (keywords.any { lowerMessage.contains(it.lowercase()) }) {
                return type
            }
        }
        return null
    }

    private fun extractDate(message: String): String? {
        for (patternStr in DATE_PATTERNS) {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(message)

            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    /**
     * Check if SMS is a warning/notification rather than an actual transaction
     */
    private fun isWarningMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Future tense warning patterns - MUST CHECK FIRST
        val futureWarningPatterns = listOf(
            "will be debited",
            "will be credited",
            "will get debited",
            "will get credited",
            "shall be debited",
            "shall be credited"
        )

        // Check future tense first (highest priority)
        if (futureWarningPatterns.any { lowerMessage.contains(it) }) {
            Log.d(TAG, "Detected future warning SMS: ${futureWarningPatterns.find { lowerMessage.contains(it) }}")
            return true
        }

        // Other warning patterns
        val otherWarningPatterns = listOf(
            "is scheduled",
            "scheduled for",
            "stop if you want",
            "reply stop",
            "to cancel",
            "autopay scheduled",
            "mandate scheduled",
            "reminder:",
            "alert:",
            "notification:",
            "due on",
            "payment reminder",
            "upcoming payment",
            "to stop execution",
            "pause mandate",
            "stop mandate",
            "cancel mandate",
            "si scheduled"
        )

        // Check if message contains other warning patterns
        val hasOtherWarning = otherWarningPatterns.any { lowerMessage.contains(it) }

        // Check if it's a completed transaction (past tense - only check if no future warning found)
        val hasCompletedTransaction = (lowerMessage.contains("debited from") ||  // Changed to "from"
                                       lowerMessage.contains("credited to") ||   // Changed to "to"
                                       lowerMessage.contains("is debited") ||
                                       lowerMessage.contains("is credited") ||
                                       lowerMessage.contains("has been debited") ||
                                       lowerMessage.contains("has been credited"))

        // If it has other warning patterns AND no completed transaction, it's a warning
        val isWarning = hasOtherWarning && !hasCompletedTransaction

        if (isWarning) {
            Log.d(TAG, "Detected warning SMS: ${otherWarningPatterns.find { lowerMessage.contains(it) }}")
        }

        return isWarning
    }
}
