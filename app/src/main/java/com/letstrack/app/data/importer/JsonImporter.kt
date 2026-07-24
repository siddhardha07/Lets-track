package com.letstrack.app.data.importer

import android.content.Context
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class JsonImporter @Inject constructor(
    private val expenseRepository: ExpenseRepository
) {
    
    suspend fun importFromJson(inputStream: InputStream): ImportResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            val transactions = jsonObject.getJSONArray("transactions")
            val imported = mutableListOf<Expense>()
            val errors = mutableListOf<String>()
            
            for (i in 0 until transactions.length()) {
                try {
                    val transaction = transactions.getJSONObject(i)
                    val expense = parseTransaction(transaction)
                    expenseRepository.insertExpense(expense)
                    imported.add(expense)
                } catch (e: Exception) {
                    errors.add("Transaction ${i + 1}: ${e.message}")
                }
            }
            
            ImportResult(
                success = imported.size,
                failed = errors.size,
                errors = errors
            )
        } catch (e: Exception) {
            ImportResult(0, 1, listOf("Failed to parse JSON: ${e.message}"))
        }
    }
    
    private fun parseTransaction(json: JSONObject): Expense {
        val dateStr = json.optString("date", "")
        val date = parseDateString(dateStr)
        
        val transactionType = json.getString("type") // "DEBIT" or "CREDIT"
        val amount = json.getDouble("amount")
        val merchant = json.optString("merchant", "Unknown")
        val details = json.optString("details", "")
        val balance = json.optDouble("balance", 0.0)
        
        return Expense(
            amount = amount,
            categoryId = 0, // null category - user will assign later
            title = merchant,
            description = details,
            notes = "", // empty notes for user to fill
            date = date,
            source = "IMPORTED",
            sourceReference = json.optString("no", ""),
            merchantName = merchant,
            transactionType = transactionType,
            needsReview = true // Mark as needs review so user can categorize
        )
    }
    
    private fun parseDateString(dateStr: String): Long {
        if (dateStr.isEmpty() || dateStr == "null") {
            return System.currentTimeMillis()
        }
        
        return try {
            // Format: "01 Jun 2026"
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            formatter.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}

data class ImportResult(
    val success: Int,
    val failed: Int,
    val errors: List<String>
)
