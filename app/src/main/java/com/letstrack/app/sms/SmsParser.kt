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
            "(?:balance|bal|avl\\.? bal).*?Rs\\.?\\s*([\\d,]+(?:\\.\\d{2})?)",
            "(?:balance|bal):\\s*Rs\\.?\\s*([\\d,]+(?:\\.\\d{2})?)"
        )
        
        private val MERCHANT_PATTERNS = listOf(
            "(?:to|at)\\s+([A-Z][A-Z\\s]{2,30})",
            "(?:VPA|UPI)\\s+([a-z0-9@\\.]+)",
            "(?:credited from|paid to)\\s+([A-Z][A-Z\\s]{2,30})"
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
        val confidence: Double // 0-100
    )
    
    /**
     * Parse SMS message and extract transaction details
     */
    fun parseSms(message: String, sender: String): ParsedSms {
        Log.d(TAG, "Parsing SMS from $sender: ${message.take(100)}...")
        
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
            confidence = confidence
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
                    return merchant
                }
            }
        }
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
}
