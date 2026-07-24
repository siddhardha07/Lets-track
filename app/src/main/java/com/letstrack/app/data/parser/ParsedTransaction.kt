package com.letstrack.app.data.parser

data class ParsedTransaction(
    val dateTime: String,
    val valueDate: String,
    val transactionDetails: String,
    val refChequeNo: String,
    val withdrawals: String,
    val deposits: String,
    val balance: String,
    
    // Extracted fields
    val amount: Double,
    val isDebit: Boolean,
    val merchantName: String,
    val upiId: String,
    val description: String
)

data class PdfParseResult(
    val success: Boolean,
    val transactions: List<ParsedTransaction>,
    val errorMessage: String = "",
    val accountNumber: String = "",
    val bankName: String = "",
    val statementPeriod: String = ""
)
