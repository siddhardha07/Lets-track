package com.letstrack.app.data.local.dao

import androidx.room.*
import com.letstrack.app.data.local.entity.MerchantCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantCategoryDao {

    @Query("SELECT * FROM merchant_categories WHERE merchantName = :merchantName LIMIT 1")
    suspend fun getMerchant(merchantName: String): MerchantCategoryEntity?

    // Candidates for fuzzy merchant matching (see SmartCategorizer.findKnownMerchant): a bank's
    // extracted merchant text is often the canonical name plus noise ("AMAZON PAY", "AMAZON.IN",
    // "AMAZON PAY INDIA PVT LTD" for a DB entry of just "AMAZON"), or occasionally truncated the
    // other way. Matches in either direction; ordered longest-name-first so the caller can prefer
    // the most specific match and apply a word-boundary check before trusting a result.
    @Query("""
        SELECT * FROM merchant_categories
        WHERE :merchant LIKE merchantName || '%' OR merchantName LIKE :merchant || '%'
        ORDER BY LENGTH(merchantName) DESC
    """)
    suspend fun findPrefixMatches(merchant: String): List<MerchantCategoryEntity>

    @Query("SELECT * FROM merchant_categories ORDER BY usageCount DESC, lastUsed DESC")
    fun getAllMerchants(): Flow<List<MerchantCategoryEntity>>

    @Query("SELECT * FROM merchant_categories WHERE mainCategory = :category")
    suspend fun getMerchantsByCategory(category: String): List<MerchantCategoryEntity>

    @Query("SELECT * FROM merchant_categories WHERE source = :source")
    suspend fun getMerchantsBySource(source: String): List<MerchantCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(merchant: MerchantCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(merchants: List<MerchantCategoryEntity>)

    @Update
    suspend fun update(merchant: MerchantCategoryEntity)

    @Query("UPDATE merchant_categories SET usageCount = usageCount + 1, lastUsed = :timestamp WHERE merchantName = :merchantName")
    suspend fun incrementUsage(merchantName: String, timestamp: Long)

    @Query("DELETE FROM merchant_categories WHERE merchantName = :merchantName")
    suspend fun delete(merchantName: String)

    @Query("DELETE FROM merchant_categories")
    suspend fun deleteAll()
}
