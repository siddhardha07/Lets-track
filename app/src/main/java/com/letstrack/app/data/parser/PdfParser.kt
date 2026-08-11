package com.letstrack.app.data.parser

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PdfParser(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun parsePdf(uri: Uri, password: String? = null): PdfParseResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext PdfParseResult(false, emptyList(), "Cannot open file")

            val document = if (password.isNullOrEmpty()) {
                PDDocument.load(inputStream)
            } else {
                PDDocument.load(inputStream, password)
            }

            val pdfTextStripper = PDFTextStripper()
            val text = pdfTextStripper.getText(document)

            // Debug-only: dumps the full extracted statement text (account number, every
            // transaction, balances) to a plain-text file for inspecting parse failures. This
            // used to run unconditionally, including in release builds - real financial
            // statement contents written to disk on every import, with nothing ever cleaning
            // the file up. Gated behind BuildConfig.DEBUG now so it stays useful for
            // development without ever happening on a real user's device.
            val isDebugBuild = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebugBuild) {
                val debugFile = File(context.getExternalFilesDir(null), "pdf_extracted_text.txt")
                debugFile.writeText(text)
                android.util.Log.d("PdfParser", "Text extracted: ${text.length} chars, saved to ${debugFile.absolutePath}")
            }
            android.util.Log.d("PdfParser", "Text extracted: ${text.length} chars")
            
            document.close()
            inputStream.close()

            // Parse IDFC First Bank format
            val result = parseIdfcFirstBank(text)
            android.util.Log.d("PdfParser", "Parsed ${result.transactions.size} transactions")
            result
        } catch (e: Exception) {
            android.util.Log.e("PdfParser", "Error parsing PDF", e)
            PdfParseResult(
                success = false,
                transactions = emptyList(),
                errorMessage = "Error parsing PDF: ${e.message}\n${e.stackTraceToString()}"
            )
        }
    }

    private fun parseIdfcFirstBank(text: String): PdfParseResult {
        val transactions = mutableListOf<ParsedTransaction>()
        
        android.util.Log.d("PdfParser", "parseIdfcFirstBank: Starting parse")
        
        // Extract account number
        val accountRegex = "SAVINGS ACCOUNT DETAILS FOR A/C : (\\d+)".toRegex()
        val accountMatch = accountRegex.find(text)
        val accountNumber = accountMatch?.groupValues?.get(1) ?: ""
        android.util.Log.d("PdfParser", "Account number: $accountNumber")

        // Extract statement period
        val periodRegex = "STATEMENT PERIOD\\s*:\\s*(.+?)to\\s*(.+?)\\n".toRegex(RegexOption.IGNORE_CASE)
        val periodMatch = periodRegex.find(text)
        val statementPeriod = if (periodMatch != null) {
            "${periodMatch.groupValues[1].trim()} to ${periodMatch.groupValues[2].trim()}"
        } else ""
        android.util.Log.d("PdfParser", "Statement period: $statementPeriod")

        // Find transaction table and parse multi-line transactions
        val lines = text.lines()
        android.util.Log.d("PdfParser", "Total lines: ${lines.size}")
        
        var inTransactionSection = false
        val datePattern = Regex("^(\\d{2} \\w{3} \\d{2} \\d{2}:\\d{2})")
        val balancePattern = Regex("([\\d,]+\\.\\d{2})\\s+CR\\s*$")
        
        val transactionBlocks = mutableListOf<String>()
        var currentBlock = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Detect start of transaction section
            if (trimmed.contains("Date and Time") && trimmed.contains("Transaction Details")) {
                inTransactionSection = true
                android.util.Log.d("PdfParser", "Found transaction section")
                continue
            }

            if (!inTransactionSection) continue
            
            // Stop at summary section
            if (trimmed.contains("Number of Withdrawals") || trimmed.contains("Number of Deposits")) {
                break
            }
            
            // Skip header lines
            if (trimmed.contains("Opening Balance") || trimmed.contains("Closing Balance") ||
                trimmed.contains("Ref/Cheque") || trimmed.contains("Withdrawals") || 
                trimmed.contains("(INR)")) {
                continue
            }

            // Check if this starts a new transaction (date pattern)
            if (datePattern.containsMatchIn(trimmed)) {
                // Save previous block
                if (currentBlock.isNotEmpty()) {
                    transactionBlocks.add(currentBlock.toString())
                }
                currentBlock = StringBuilder(trimmed)
            } else if (currentBlock.isNotEmpty()) {
                // Append to current transaction
                currentBlock.append(" ").append(trimmed)
            }
            
            // Check if this completes a transaction (ends with balance)
            if (balancePattern.containsMatchIn(trimmed)) {
                if (currentBlock.isNotEmpty()) {
                    transactionBlocks.add(currentBlock.toString())
                    currentBlock = StringBuilder()
                }
            }
        }
        
        // Add last block if exists
        if (currentBlock.isNotEmpty()) {
            transactionBlocks.add(currentBlock.toString())
        }

        android.util.Log.d("PdfParser", "Found ${transactionBlocks.size} transaction blocks")

        // Parse each transaction block
        for ((index, block) in transactionBlocks.withIndex()) {
            try {
                val transaction = parseTransactionBlock(block)
                if (transaction != null) {
                    transactions.add(transaction)
                    if (index < 3) {
                        android.util.Log.d("PdfParser", "TX $index: ${transaction.merchantName} ₹${transaction.amount}")
                    }
                }
            } catch (e: Exception) {
                if (index < 5) {
                    android.util.Log.w("PdfParser", "Failed block $index: ${block.take(100)}", e)
                }
            }
        }

        android.util.Log.d("PdfParser", "Total transactions parsed: ${transactions.size}")
        
        return PdfParseResult(
            success = true,
            transactions = transactions,
            accountNumber = accountNumber,
            bankName = "IDFC First Bank",
            statementPeriod = statementPeriod
        )
    }
    
    private fun parseTransactionBlock(block: String): ParsedTransaction? {
        // Block format: "01 Jun 26 10:21 01 Jun 26 UPI/DR/.../Payment 200.00 37,761.77 CR"
        
        // Extract date and time (at start)
        val dateMatch = Regex("^(\\d{2} \\w{3} \\d{2} \\d{2}:\\d{2})").find(block) ?: return null
        val dateTime = dateMatch.value
        
        // Extract value date (second date)
        val valueDateMatch = Regex("\\d{2}:\\d{2}\\s+(\\d{2} \\w{3} \\d{2})").find(block)
        val valueDate = valueDateMatch?.groupValues?.get(1) ?: ""
        
        // Extract balance (ends with "X,XXX.XX CR")
        val balanceMatch = Regex("([\\d,]+\\.\\d{2})\\s+CR\\s*$").find(block)
        val balance = balanceMatch?.groupValues?.get(1) ?: ""
        
        // Extract amounts before balance
        // Pattern: either "withdrawal deposit balance" or "deposit balance"
        val amountsMatch = Regex("([\\d,]+\\.\\d{2})(?:\\s+([\\d,]+\\.\\d{2}))?\\s+[\\d,]+\\.\\d{2}\\s+CR").find(block)
        
        val withdrawals: String
        val deposits: String
        
        if (amountsMatch != null) {
            if (amountsMatch.groupValues.size > 2 && amountsMatch.groupValues[2].isNotBlank()) {
                // Has both withdrawal and deposit
                withdrawals = amountsMatch.groupValues[1]
                deposits = amountsMatch.groupValues[2]
            } else {
                // Only one amount - could be withdrawal or deposit
                // Check transaction details for DR (debit) or CR (credit)
                val amount = amountsMatch.groupValues[1]
                if (block.contains("UPI/DR") || block.contains("NACH/DR") || block.contains("/DR/")) {
                    withdrawals = amount
                    deposits = ""
                } else {
                    withdrawals = ""
                    deposits = amount
                }
            }
        } else {
            withdrawals = ""
            deposits = ""
        }
        
        // Extract transaction details (between value date and amounts)
        val detailsStart = block.indexOf(valueDate) + valueDate.length
        val detailsEnd = amountsMatch?.range?.first ?: (balanceMatch?.range?.first ?: block.length)
        val details = if (detailsStart > 0 && detailsStart < detailsEnd) {
            block.substring(detailsStart, detailsEnd).trim()
        } else {
            ""
        }
        
        // Determine amount and type
        val isDebit = withdrawals.isNotEmpty()
        val amountStr = if (isDebit) withdrawals else deposits
        val amount = try {
            amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
        
        if (amount == 0.0) return null

        // Extract merchant name
        val (merchant, upiId) = extractMerchantInfo(details)

        return ParsedTransaction(
            dateTime = dateTime,
            valueDate = valueDate,
            transactionDetails = details,
            refChequeNo = "",
            withdrawals = withdrawals,
            deposits = deposits,
            balance = balance,
            amount = amount,
            isDebit = isDebit,
            merchantName = merchant,
            upiId = upiId,
            description = details
        )
    }

    private fun extractMerchantInfo(details: String): Pair<String, String> {
        // UPI pattern: UPI/DR/123456789/MERCHANT@UPI/Payment
        val upiRegex = "UPI/[^/]+/[^/]+/([^/]+)/".toRegex()
        val upiMatch = upiRegex.find(details)
        val upiId = upiMatch?.groupValues?.get(1) ?: ""

        // Extract merchant name
        var merchant = when {
            upiId.isNotEmpty() -> {
                // Extract name before @
                upiId.substringBefore("@").replace(".", " ").trim()
            }
            details.contains("SHOPBA") -> "Shopping"
            details.contains("NEFT") || details.contains("IMPS") -> {
                // Try to find name in NEFT/IMPS transaction
                val nameRegex = "/([A-Z ]+)/".toRegex()
                nameRegex.find(details)?.groupValues?.get(1)?.trim() ?: "Transfer"
            }
            else -> "Unknown"
        }

        // Clean up merchant name
        merchant = merchant
            .replace("_", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .split(" ")
            .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }

        return Pair(merchant, upiId)
    }

    suspend fun exportToJson(transactions: List<ParsedTransaction>, outputFile: File): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                val jsonObject = org.json.JSONObject()
                val jsonArray = org.json.JSONArray()
                
                transactions.forEach { tx ->
                    val txJson = org.json.JSONObject()
                    txJson.put("dateTime", tx.dateTime)
                    txJson.put("valueDate", tx.valueDate)
                    txJson.put("transactionDetails", tx.transactionDetails)
                    txJson.put("refChequeNo", tx.refChequeNo)
                    txJson.put("withdrawals", tx.withdrawals)
                    txJson.put("deposits", tx.deposits)
                    txJson.put("balance", tx.balance)
                    txJson.put("amount", tx.amount)
                    txJson.put("type", if (tx.isDebit) "DEBIT" else "CREDIT")
                    txJson.put("merchantName", tx.merchantName)
                    txJson.put("upiId", tx.upiId)
                    txJson.put("description", tx.description)
                    jsonArray.put(txJson)
                }
                
                jsonObject.put("transactions", jsonArray)
                jsonObject.put("count", transactions.size)
                
                outputFile.writeText(jsonObject.toString(2))
                true
            } catch (e: Exception) {
                android.util.Log.e("PdfParser", "Failed to export JSON", e)
                false
            }
        }
}
