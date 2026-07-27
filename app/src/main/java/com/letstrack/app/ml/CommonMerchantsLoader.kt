package com.letstrack.app.ml

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.letstrack.app.data.local.dao.MerchantCategoryDao
import com.letstrack.app.data.local.entity.MerchantCategoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-populates merchant database with common Indian merchants
 * Run once on first app launch
 */
@Singleton
class CommonMerchantsLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val merchantCategoryDao: MerchantCategoryDao
) {

    companion object {
        private const val TAG = "CommonMerchantsLoader"
        private const val COMMON_MERCHANTS_FILE = "common_merchants.json"
        private const val PREFS_NAME = "merchant_prefs"
        private const val KEY_LOADED = "common_merchants_loaded"
    }

    data class CommonMerchants(
        val merchants: Map<String, MerchantData>
    )

    data class MerchantData(
        val category: String,
        val confidence: Double
    )

    /**
     * Load common merchants into database (only once)
     */
    suspend fun loadIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyLoaded = prefs.getBoolean(KEY_LOADED, false)

        if (alreadyLoaded) {
            Log.d(TAG, "Common merchants already loaded")
            return@withContext
        }

        try {
            Log.d(TAG, "Loading common merchants...")

            // Read JSON file
            val json = context.assets.open(COMMON_MERCHANTS_FILE).bufferedReader().use { it.readText() }
            val type = object : TypeToken<CommonMerchants>() {}.type
            val commonMerchants: CommonMerchants = Gson().fromJson(json, type)

            // Insert into database
            var count = 0
            commonMerchants.merchants.forEach { (merchantName, data) ->
                val entity = MerchantCategoryEntity(
                    merchantName = merchantName.uppercase(),
                    mainCategory = data.category,
                    subCategory = null,
                    confidence = data.confidence,
                    source = "pre-populated",
                    lastUsed = 0,
                    usageCount = 0,
                    createdAt = System.currentTimeMillis()
                )

                // Only insert if not already exists
                val existing = merchantCategoryDao.getMerchant(merchantName.uppercase())
                if (existing == null) {
                    merchantCategoryDao.insert(entity)
                    count++
                }
            }

            // Mark as loaded
            prefs.edit().putBoolean(KEY_LOADED, true).apply()

            Log.d(TAG, "✓ Loaded $count common merchants into database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load common merchants: ${e.message}", e)
        }
    }

    /**
     * Reset (for testing)
     */
    suspend fun reset() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOADED, false).apply()
        Log.d(TAG, "Reset common merchants flag")
    }
}
