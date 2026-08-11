package com.letstrack.app.ml

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.letstrack.app.data.local.dao.MerchantCategoryDao
import com.letstrack.app.data.local.entity.MerchantCategoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        // Bumped from "common_merchants_loaded" to "_v2" so installs that already ran the
        // loader once (back when the bundled file had ~150 entries) run it again now that it
        // has ~10,000 - the old flag would otherwise permanently skip loading the new ones.
        // Bump again (_v3, _v4...) any time the asset file grows with genuinely new merchants.
        private const val KEY_LOADED = "common_merchants_loaded_v2"
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

            // At ~10k entries, checking "does this exist?" one row at a time (as this used to)
            // means ~10k individual suspend DB round-trips. Fetch the existing names once as a
            // Set instead, filter in memory, and insert everything new in a single batched call.
            val existingNames = merchantCategoryDao.getAllMerchants().first().map { it.merchantName }.toSet()
            val now = System.currentTimeMillis()
            val toInsert = commonMerchants.merchants.mapNotNull { (merchantName, data) ->
                val upperName = merchantName.uppercase()
                if (upperName in existingNames) return@mapNotNull null
                MerchantCategoryEntity(
                    merchantName = upperName,
                    mainCategory = data.category,
                    subCategory = null,
                    confidence = data.confidence,
                    source = "pre-populated",
                    lastUsed = 0,
                    usageCount = 0,
                    createdAt = now
                )
            }
            if (toInsert.isNotEmpty()) {
                merchantCategoryDao.insertAll(toInsert)
            }

            // Mark as loaded
            prefs.edit().putBoolean(KEY_LOADED, true).apply()

            Log.d(TAG, "✓ Loaded ${toInsert.size} common merchants into database (${commonMerchants.merchants.size - toInsert.size} already present, skipped)")
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
