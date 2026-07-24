package com.letstrack.app.domain.model

data class BankAccount(
    val id: Long = 0,
    val accountNumber: String,
    val bankName: String,
    val accountNickname: String = "",
    val senderPatterns: List<String>,
    val sampleDebitSms: String,
    val sampleCreditSms: String,
    val debitKeywords: List<String>,
    val creditKeywords: List<String>,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val smsProcessedCount: Int = 0,
    val lastSmsDate: Long? = null
)
