package com.letstrack.app.data.importer

import android.util.Log
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import com.letstrack.app.ml.SmartCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/**
 * Imports expenses from a CSV file. Expected header row (case-insensitive, any column order):
 *   date,type,amount,merchant,details,balance
 *
 *   date     - "dd MMM yyyy" (e.g. "09 Aug 2026"), or "dd/MM/yyyy" / "yyyy-MM-dd". Optional -
 *              defaults to now if missing/unparseable.
 *   type     - DEBIT or CREDIT (case-insensitive). Required.
 *   amount   - plain number, commas allowed (e.g. "1,234.50"). Required.
 *   merchant - payee/merchant name. Optional - defaults to "Unknown".
 *   details  - free-text description, also fed to the categorizer for better accuracy. Optional.
 *   balance  - account balance after the transaction. Optional, currently unused for anything
 *              but future duplicate-detection.
 *
 * Mirrors JsonImporter's shape (same categorization + category-mapping path) so CSV and JSON
 * imports behave identically once a row/object is parsed - only the parsing itself differs.
 */
class CsvImporter @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val smartCategorizer: SmartCategorizer,
    private val categoryRepository: CategoryRepository
) {
    companion object {
        private const val TAG = "CsvImporter"
        private val DATE_FORMATS = listOf(
            "dd MMM yyyy",
            "dd/MM/yyyy",
            "yyyy-MM-dd"
        )
    }

    suspend fun importFromCsv(inputStream: InputStream): ImportResult = withContext(Dispatchers.IO) {
        try {
            val lines = inputStream.bufferedReader().use { it.readLines() }
                .filter { it.isNotBlank() }

            if (lines.isEmpty()) {
                return@withContext ImportResult(0, 0, listOf("CSV file is empty"))
            }

            val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
            val dateCol = header.indexOf("date")
            val typeCol = header.indexOf("type")
            val amountCol = header.indexOf("amount")
            val merchantCol = header.indexOf("merchant")
            val detailsCol = header.indexOf("details")
            val balanceCol = header.indexOf("balance")

            if (typeCol == -1 || amountCol == -1) {
                return@withContext ImportResult(
                    0, 1,
                    listOf("CSV must have at least 'type' and 'amount' columns. Found: ${header.joinToString(", ")}")
                )
            }

            val errors = mutableListOf<String>()
            var success = 0

            lines.drop(1).forEachIndexed { index, line ->
                try {
                    val cols = parseCsvLine(line)
                    fun col(i: Int): String? = if (i >= 0 && i < cols.size) cols[i].trim() else null

                    val transactionType = col(typeCol)?.uppercase()
                        ?.takeIf { it == "DEBIT" || it == "CREDIT" }
                        ?: throw IllegalArgumentException("type must be DEBIT or CREDIT, got '${col(typeCol)}'")
                    val amount = col(amountCol)?.replace(",", "")?.toDoubleOrNull()
                        ?: throw IllegalArgumentException("amount is missing or not a number")
                    val merchant = col(merchantCol)?.takeIf { it.isNotBlank() } ?: "Unknown"
                    val details = col(detailsCol) ?: ""
                    val balance = col(balanceCol)?.replace(",", "")?.toDoubleOrNull()
                    val date = col(dateCol)?.let { parseDate(it) } ?: System.currentTimeMillis()

                    val prediction = try {
                        smartCategorizer.categorize(
                            merchantName = merchant,
                            amount = amount,
                            transactionType = transactionType,
                            message = details
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "AI categorization failed for $merchant: ${e.message}")
                        null
                    }

                    val categoryId = if (prediction != null && prediction.confidence >= 0.6) {
                        getCategoryIdByName(prediction.category) ?: 0
                    } else {
                        0 // Uncategorized if confidence too low - matches JsonImporter's convention
                    }

                    val expense = Expense(
                        amount = amount,
                        categoryId = categoryId,
                        subCategory = prediction?.subCategory,
                        title = merchant,
                        description = details,
                        notes = "",
                        date = date,
                        source = "IMPORTED",
                        sourceReference = "CSV",
                        merchantName = merchant,
                        transactionType = transactionType,
                        balanceAfterTransaction = balance,
                        needsReview = prediction == null || prediction.confidence < 0.9,
                        isAiCategorized = prediction != null,
                        confidenceScore = prediction?.confidence ?: 0.0
                    )
                    expenseRepository.insertExpense(expense)
                    success++
                } catch (e: Exception) {
                    errors.add("Row ${index + 2}: ${e.message}") // +2: 1-indexed, plus header row
                }
            }

            ImportResult(success, errors.size, errors)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse CSV: ${e.message}", e)
            ImportResult(0, 1, listOf("Failed to parse CSV: ${e.message}"))
        }
    }

    private suspend fun getCategoryIdByName(categoryName: String): Long? {
        return try {
            com.letstrack.app.domain.model.resolveCategoryId(categoryRepository, categoryName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting category ID for '$categoryName': ${e.message}")
            null
        }
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        for (pattern in DATE_FORMATS) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.ENGLISH)
                formatter.isLenient = false
                return formatter.parse(dateStr)?.time ?: continue
            } catch (e: Exception) {
                // try next format
            }
        }
        return System.currentTimeMillis()
    }

    /**
     * Minimal RFC4180-style CSV line splitter: handles quoted fields (so a merchant/details
     * value containing a comma doesn't get split apart) and "" as an escaped quote inside a
     * quoted field. Not a full CSV spec implementation, but covers what a bank/expense export
     * actually needs.
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
