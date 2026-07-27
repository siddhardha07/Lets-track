package com.letstrack.app.data.importer

import android.content.Context
import android.util.Log
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.domain.repository.ExpenseRepository
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.ml.SmartCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class JsonImporter @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val smartCategorizer: SmartCategorizer,
    private val categoryRepository: CategoryRepository
) {
    companion object {
        private const val TAG = "JsonImporter"
    }

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

    private suspend fun parseTransaction(json: JSONObject): Expense {
        val dateStr = json.optString("date", "")
        val date = parseDateString(dateStr)

        val transactionType = json.getString("type") // "DEBIT" or "CREDIT"
        val amount = json.getDouble("amount")
        val merchant = json.optString("merchant", "Unknown")
        val details = json.optString("details", "")
        val sms = json.optString("sms", details) // Try to get full SMS text for better categorization
        val balance = json.optDouble("balance", 0.0)

        // Use AI to categorize the transaction
        val prediction = try {
            smartCategorizer.categorize(
                merchantName = merchant,
                amount = amount,
                transactionType = transactionType,
                message = sms
            )
        } catch (e: Exception) {
            Log.e(TAG, "AI categorization failed for $merchant: ${e.message}")
            null
        }

        val categoryId = if (prediction != null && prediction.confidence >= 0.6) {
            getCategoryIdByName(prediction.category) ?: 0
        } else {
            0 // Uncategorized if confidence too low
        }

        val needsReview = prediction == null || prediction.confidence < 0.9

        Log.d(TAG, "Imported: $merchant -> ${prediction?.category} (${(prediction?.confidence?.times(100))?.toInt()}% confidence)")

        return Expense(
            amount = amount,
            categoryId = categoryId,
            subCategory = prediction?.subCategory,
            title = merchant,
            description = details,
            notes = "",
            date = date,
            source = "IMPORTED",
            sourceReference = json.optString("no", ""),
            merchantName = merchant,
            transactionType = transactionType,
            needsReview = needsReview,
            isAiCategorized = prediction != null,
            confidenceScore = prediction?.confidence ?: 0.0
        )
    }

    private suspend fun getCategoryIdByName(categoryName: String): Long? {
        return try {
            val categories = categoryRepository.getAllCategories().first()
            // Map ML model category names to app category names
            val mappedName = mapMlCategoryToAppCategory(categoryName)
            categories.find { it.name.equals(mappedName, ignoreCase = true) }?.id
        } catch (e: Exception) {
            Log.e(TAG, "Error getting category ID for '$categoryName': ${e.message}")
            null
        }
    }

    /**
     * Map ML model category names to app category names
     * ML Model: Bills, Entertainment, Food, Groceries, Income, Medical, Shopping, Transport
     * App: Food, Bills & Utilities, etc.
     */
    private fun mapMlCategoryToAppCategory(mlCategory: String): String {
        return when (mlCategory) {
            "Food" -> "Food"
            "Bills" -> "Bills & Utilities"
            "Medical" -> "Health & Fitness"
            "Groceries" -> "Food" // Groceries is a subcategory of Food
            "Income" -> "Other" // Income not in default categories, map to Other
            "Entertainment" -> "Entertainment"
            "Shopping" -> "Shopping"
            "Transport" -> "Transportation"
            else -> "Other" // Fallback to Other for unknown categories
        }
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
