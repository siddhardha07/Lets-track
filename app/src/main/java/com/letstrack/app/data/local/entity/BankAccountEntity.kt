package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bank_accounts")
data class BankAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Account identification
    val accountNumber: String, // Last 4-6 digits (e.g., "3937", "023937")
    val bankName: String, // e.g., "IDFC FIRST Bank"
    val accountNickname: String = "", // User-given name (e.g., "Salary Account")
    
    // SMS sender patterns (JSON array of possible senders)
    // e.g., ["VM-IDFCFB", "IDFCFB", "IDFC-BANK"]
    val senderPatterns: String,
    
    // Sample messages for learning (stored for re-parsing if needed)
    val sampleDebitSms: String,
    val sampleCreditSms: String,
    
    // Extracted patterns from samples
    val debitKeywords: String, // JSON: ["debited", "debited by", "withdrawn"]
    val creditKeywords: String, // JSON: ["credited", "is credited", "deposited"]
    
    // Regex patterns for extraction (auto-generated from samples)
    val amountPattern: String, // e.g., "Rs\\. ([\\d,]+\\.\\d{2})|INR ([\\d,]+\\.\\d{2})"
    val datePattern: String, // e.g., "(\\d{2}/\\d{2}/\\d{2})"
    val balancePattern: String, // e.g., "balance.*?Rs\\. ([\\d,]+\\.\\d{2})"
    val accountPattern: String, // e.g., "A/[Cc] .*?(\\d{4,6})"
    
    // Merchant extraction pattern (for UPI/transfers)
    val merchantPattern: String = "", // e.g., "(credited|to): ([A-Z ]+)"
    
    // Metadata
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    
    // Statistics
    val smsProcessedCount: Int = 0, // How many SMS successfully parsed
    val lastSmsDate: Long? = null // Last SMS received date
)
