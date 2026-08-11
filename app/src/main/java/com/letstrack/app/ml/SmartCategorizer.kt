package com.letstrack.app.ml

import android.util.Log
import com.letstrack.app.data.local.dao.MerchantCategoryDao
import com.letstrack.app.data.local.dao.UserCorrectionDao
import com.letstrack.app.data.local.entity.MerchantCategoryEntity
import com.letstrack.app.data.local.entity.UserCorrectionEntity
import com.letstrack.app.domain.model.CategoryPrediction
import com.letstrack.app.domain.model.UserCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Smart Categorizer - Database-based transaction categorization
 *
 * Simple, effective approach:
 * 1. Check Common Merchants (150+ pre-loaded) - Instant
 * 2. Check Learned Merchants (user's history) - Instant
 * 3. Ask user if unknown → Save and learn forever
 *
 * No AI/ML needed - learns from user corrections
 */
@Singleton
class SmartCategorizer @Inject constructor(
    private val merchantCategoryDao: MerchantCategoryDao,
    private val userCorrectionDao: UserCorrectionDao
) {

    companion object {
        private const val TAG = "SmartCategorizer"
        private const val LEARNED_CONFIDENCE_THRESHOLD = 0.85
        // Below this, a string is too short/generic to safely fuzzy-match (e.g. matching on
        // "OLA" shouldn't also catch some unrelated merchant that happens to start with it).
        private const val MIN_FUZZY_MATCH_LENGTH = 4
    }

    /**
     * Finds a known merchant, exact match first, then a fuzzy prefix match for cases like a
     * bank SMS extracting "AMAZON PAY" or "AMAZON PAY INDIA PVT LTD" for a DB entry of just
     * "AMAZON" - or the reverse, an extraction truncated to "AMAZON P" against a longer DB
     * entry. A plain substring/prefix check alone would also match unrelated merchants that
     * happen to share a prefix (e.g. "AMAZONAS"), so this requires a word boundary right after
     * the shorter string - the next character must not be a letter or digit - before trusting
     * a fuzzy candidate.
     */
    private suspend fun findKnownMerchant(normalizedMerchant: String): MerchantCategoryEntity? {
        merchantCategoryDao.getMerchant(normalizedMerchant)?.let { return it }
        if (normalizedMerchant.length < MIN_FUZZY_MATCH_LENGTH) return null

        val candidates = merchantCategoryDao.findPrefixMatches(normalizedMerchant)
        return candidates.firstOrNull { candidate ->
            candidate.merchantName.length >= MIN_FUZZY_MATCH_LENGTH &&
                (hasWordBoundaryPrefix(candidate.merchantName, normalizedMerchant) ||
                    hasWordBoundaryPrefix(normalizedMerchant, candidate.merchantName))
        }
    }

    /** True if [longer] starts with [shorter] and either they're equal or the very next
     *  character in [longer] isn't a letter/digit (i.e. [shorter] ends on a real word boundary,
     *  not mid-word). */
    private fun hasWordBoundaryPrefix(shorter: String, longer: String): Boolean {
        if (shorter.length > longer.length || !longer.startsWith(shorter)) return false
        if (shorter.length == longer.length) return true
        return !longer[shorter.length].isLetterOrDigit()
    }


    /**
     * Main categorization method
     * Flow: Check Common/Learned Merchants → Return "Other" if unknown
     */
    suspend fun categorize(
        merchantName: String,
        amount: Double,
        transactionType: String,
        message: String
    ): CategoryPrediction = withContext(Dispatchers.IO) {
        Log.d(TAG, "📊 Categorizing merchant: '$merchantName'")

        // Check if we've learned this merchant before (common + user's)
        val normalizedMerchant = merchantName.uppercase().trim()
        Log.d(TAG, "🔍 Looking up normalized merchant: '$normalizedMerchant'")
        val knownMerchant = findKnownMerchant(normalizedMerchant)
        if (knownMerchant != null && knownMerchant.confidence > LEARNED_CONFIDENCE_THRESHOLD) {
            // Update usage stats - against the matched entry's own stored name, not the raw
            // input, since a fuzzy match means these can differ (e.g. "AMAZON PAY" matched
            // "AMAZON") and incrementUsage's exact-match WHERE clause would silently touch 0
            // rows otherwise.
            merchantCategoryDao.incrementUsage(knownMerchant.merchantName, System.currentTimeMillis())

            Log.d(TAG, "✓ KNOWN merchant: $merchantName → ${knownMerchant.mainCategory} (${(knownMerchant.confidence * 100).toInt()}% confidence)")
            return@withContext CategoryPrediction(
                category = knownMerchant.mainCategory,
                subCategory = knownMerchant.subCategory,
                confidence = knownMerchant.confidence,
                source = knownMerchant.source,
                reasoning = "Previously learned from ${knownMerchant.source}"
            )
        }

        // Unknown merchant - needs user categorization
        Log.d(TAG, "❓ UNKNOWN merchant: '$merchantName' → will trigger overlay for user to categorize")
        CategoryPrediction(
            category = "Other",
            subCategory = null,
            confidence = 0.0,
            source = "unknown",
            reasoning = "New merchant - please categorize"
        )
    }

    /**
     * Learn from user correction/confirmation
     */
    suspend fun learnFromCorrection(
        merchantName: String,
        amount: Double,
        originalPrediction: CategoryPrediction,
        userCorrection: UserCorrection
    ) = withContext(Dispatchers.IO) {
        try {
            // Save user correction for training data
            val correctionEntity = UserCorrectionEntity(
                merchantName = merchantName.uppercase(),
                transactionAmount = amount,
                originalCategory = originalPrediction.category,
                originalSubCategory = originalPrediction.subCategory,
                originalConfidence = originalPrediction.confidence,
                correctedCategory = userCorrection.category,
                correctedSubCategory = userCorrection.subCategory,
                wasAccepted = userCorrection.isCorrect
            )
            userCorrectionDao.insert(correctionEntity)

            // Update/create merchant category
            val normalizedMerchant = merchantName.uppercase().trim()
            val existingMerchant = merchantCategoryDao.getMerchant(normalizedMerchant)

            if (existingMerchant != null) {
                // Update existing merchant
                val newConfidence = if (userCorrection.isCorrect) {
                    // User confirmed - boost confidence
                    min(existingMerchant.confidence + 0.1, 1.0)
                } else {
                    // User corrected - reduce old confidence, set new category
                    0.95 // High confidence for user corrections
                }

                val updated = existingMerchant.copy(
                    mainCategory = userCorrection.category,
                    subCategory = userCorrection.subCategory,
                    confidence = newConfidence,
                    source = "user-correction",
                    lastUsed = System.currentTimeMillis(),
                    usageCount = existingMerchant.usageCount + 1
                )
                merchantCategoryDao.update(updated)
                Log.d(TAG, "📝 UPDATED merchant: '$normalizedMerchant' → ${updated.mainCategory} (${(updated.confidence * 100).toInt()}% conf)")
            } else {
                // Create new merchant entry
                val newEntry = MerchantCategoryEntity(
                    merchantName = normalizedMerchant,
                    mainCategory = userCorrection.category,
                    subCategory = userCorrection.subCategory,
                    confidence = 0.95, // User-confirmed starts very high
                    source = "user-correction",
                    lastUsed = System.currentTimeMillis(),
                    usageCount = 1
                )
                merchantCategoryDao.insert(newEntry)
                Log.d(TAG, "📝 NEW merchant saved: '$normalizedMerchant' → ${newEntry.mainCategory} (${(newEntry.confidence * 100).toInt()}% conf)")
            }

            Log.d(TAG, "✓ Learned from user: $merchantName → ${userCorrection.category}")
        } catch (e: Exception) {
            Log.e(TAG, "Error learning from correction: ${e.message}", e)
        }
    }

    /**
     * Save merchant category to database (from Wikipedia or rules)
     */
    private suspend fun saveMerchantCategory(
        merchantName: String,
        prediction: CategoryPrediction
    ) {
        try {
            val existing = merchantCategoryDao.getMerchant(merchantName.uppercase())
            if (existing == null) {
                merchantCategoryDao.insert(
                    MerchantCategoryEntity(
                        merchantName = merchantName.uppercase(),
                        mainCategory = prediction.category,
                        subCategory = prediction.subCategory,
                        confidence = prediction.confidence,
                        source = prediction.source,
                        lastUsed = System.currentTimeMillis(),
                        usageCount = 1
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving merchant category: ${e.message}", e)
        }
    }
}
