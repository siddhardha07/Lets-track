package com.letstrack.app.data.repository

import com.letstrack.app.data.local.dao.SubCategoryDao
import com.letstrack.app.data.local.entity.SubCategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sub-category repository with default initializations
 */
@Singleton
class SubCategoryRepository @Inject constructor(
    private val subCategoryDao: SubCategoryDao
) {
    
    /**
     * Initialize default sub-categories
     * User can add custom ones anytime
     */
    suspend fun initializeDefaultSubCategories() = withContext(Dispatchers.IO) {
        val defaults = getDefaultSubCategories()
        subCategoryDao.insertAll(defaults)
    }
    
    /**
     * Get default sub-categories (flexible, user can add more)
     */
    private fun getDefaultSubCategories(): List<SubCategoryEntity> {
        return listOf(
            // Food
            SubCategoryEntity(mainCategory = "Food", subCategoryName = "Groceries", icon = "🛒", isDefault = true, displayOrder = 1),
            SubCategoryEntity(mainCategory = "Food", subCategoryName = "Restaurants", icon = "🍽️", isDefault = true, displayOrder = 2),
            SubCategoryEntity(mainCategory = "Food", subCategoryName = "Food Delivery", icon = "🛵", isDefault = true, displayOrder = 3),
            SubCategoryEntity(mainCategory = "Food", subCategoryName = "Coffee & Snacks", icon = "☕", isDefault = true, displayOrder = 4),
            
            // Shopping
            SubCategoryEntity(mainCategory = "Shopping", subCategoryName = "Clothing", icon = "👕", isDefault = true, displayOrder = 1),
            SubCategoryEntity(mainCategory = "Shopping", subCategoryName = "Electronics", icon = "📱", isDefault = true, displayOrder = 2),
            SubCategoryEntity(mainCategory = "Shopping", subCategoryName = "Home & Furniture", icon = "🏠", isDefault = true, displayOrder = 3),
            SubCategoryEntity(mainCategory = "Shopping", subCategoryName = "Books & Stationery", icon = "📚", isDefault = true, displayOrder = 4),
            
            // Transport
            SubCategoryEntity(mainCategory = "Transport", subCategoryName = "Fuel", icon = "⛽", isDefault = true, displayOrder = 1),
            SubCategoryEntity(mainCategory = "Transport", subCategoryName = "Ride Sharing", icon = "🚗", isDefault = true, displayOrder = 2),
            SubCategoryEntity(mainCategory = "Transport", subCategoryName = "Public Transport", icon = "🚌", isDefault = true, displayOrder = 3),
            SubCategoryEntity(mainCategory = "Transport", subCategoryName = "Parking & Tolls", icon = "🅿️", isDefault = true, displayOrder = 4),
            
            // Bills & Utilities
            SubCategoryEntity(mainCategory = "Bills & Utilities", subCategoryName = "Electricity", icon = "💡", isDefault = true, displayOrder = 1),
            SubCategoryEntity(mainCategory = "Bills & Utilities", subCategoryName = "Water", icon = "💧", isDefault = true, displayOrder = 2),
            SubCategoryEntity(mainCategory = "Bills & Utilities", subCategoryName = "Internet & Mobile", icon = "📶", isDefault = true, displayOrder = 3),
            SubCategoryEntity(mainCategory = "Bills & Utilities", subCategoryName = "Subscriptions", icon = "📺", isDefault = true, displayOrder = 4),
            
            // Entertainment
            SubCategoryEntity(mainCategory = "Entertainment", subCategoryName = "Streaming Services", icon = "🎬", isDefault = true, displayOrder = 1),
            SubCategoryEntity(mainCategory = "Entertainment", subCategoryName = "Movies & Cinema", icon = "🎞️", isDefault = true, displayOrder = 2),
            SubCategoryEntity(mainCategory = "Entertainment", subCategoryName = "Gaming", icon = "🎮", isDefault = true, displayOrder = 3),
            SubCategoryEntity(mainCategory = "Entertainment", subCategoryName = "Events & Concerts", icon = "🎵", isDefault = true, displayOrder = 4),
            
            // Health & Fitness
            SubCategoryEntity(mainCategory = "Health & Fitness", subCategoryName = "Gym & Fitness", icon = "💪", isDefault = true, displayOrder = 1),
            SubCategoryEntity(mainCategory = "Health & Fitness", subCategoryName = "Medical & Doctor", icon = "⚕️", isDefault = true, displayOrder = 2),
            SubCategoryEntity(mainCategory = "Health & Fitness", subCategoryName = "Pharmacy & Medicine", icon = "💊", isDefault = true, displayOrder = 3),
            SubCategoryEntity(mainCategory = "Health & Fitness", subCategoryName = "Wellness & Spa", icon = "🧘", isDefault = true, displayOrder = 4),
            
            // Travel
            SubCategoryEntity(mainCategory = "Travel", subCategoryName = "Hotels", icon = "🏨", isDefault = true, displayOrder = 1),
            SubCategoryEntity(mainCategory = "Travel", subCategoryName = "Flights", icon = "✈️", isDefault = true, displayOrder = 2),
            SubCategoryEntity(mainCategory = "Travel", subCategoryName = "Vacation Packages", icon = "🌴", isDefault = true, displayOrder = 3),
            
            // Education
            SubCategoryEntity(mainCategory = "Education", subCategoryName = "Courses & Training", icon = "🎓", isDefault = true, displayOrder = 1),
            SubCategoryEntity(mainCategory = "Education", subCategoryName = "Books & Materials", icon = "📖", isDefault = true, displayOrder = 2),
            SubCategoryEntity(mainCategory = "Education", subCategoryName = "Tuition Fees", icon = "🏫", isDefault = true, displayOrder = 3),
            
            // Personal Care
            SubCategoryEntity(mainCategory = "Personal Care", subCategoryName = "Salon & Grooming", icon = "💇", isDefault = true, displayOrder = 1),
            SubCategoryEntity(mainCategory = "Personal Care", subCategoryName = "Cosmetics", icon = "💄", isDefault = true, displayOrder = 2)
        )
    }
    
    /**
     * Add custom sub-category (user-defined)
     */
    suspend fun addCustomSubCategory(
        mainCategory: String,
        subCategoryName: String,
        icon: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val existing = subCategoryDao.getSubCategoriesByMainCategory(mainCategory)
        val maxOrder = existing.maxOfOrNull { it.displayOrder } ?: 0
        
        subCategoryDao.insert(
            SubCategoryEntity(
                mainCategory = mainCategory,
                subCategoryName = subCategoryName,
                icon = icon,
                isDefault = false,
                displayOrder = maxOrder + 1
            )
        )
    }
    
    /**
     * Get sub-categories for a main category
     */
    suspend fun getSubCategories(mainCategory: String): List<SubCategoryEntity> = withContext(Dispatchers.IO) {
        subCategoryDao.getSubCategoriesByMainCategory(mainCategory)
    }
}
