package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SMS Transaction Entity - Stores parsed SMS for transaction extraction
 */
@Entity(tableName = "sms_transactions")
data class SmsTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // Sender ID (e.g., VM-HDFCBK, SBIINB)
    val message: String, // Full SMS content
    val timestamp: Long,
    
    // Parsed data
    val extractedAmount: Double? = null,
    val extractedMerchant: String? = null,
    val transactionType: String? = null, // DEBIT, CREDIT
    val cardType: String? = null, // UPI, CARD, NEFT, etc.
    val accountNumber: String? = null, // Last 4 digits
    val extractedBalance: Double? = null, // Balance after transaction
    
    // Processing status
    val isParsed: Boolean = false,
    val isMatched: Boolean = false, // Matched to an expense
    val matchedExpenseId: Long? = null,
    
    val createdAt: Long = 0
)
