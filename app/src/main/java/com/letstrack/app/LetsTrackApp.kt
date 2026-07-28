package com.letstrack.app

import android.app.Application
import android.util.Log
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.ml.CommonMerchantsLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for Let's Track
 * Handles app initialization including pre-populating common merchants
 */
@HiltAndroidApp
class LetsTrackApp : Application() {

    @Inject
    lateinit var commonMerchantsLoader: CommonMerchantsLoader

    @Inject
    lateinit var categoryRepository: CategoryRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "LetsTrackApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✓ App starting...")

        // Load common merchants in background (only once per installation)
        applicationScope.launch {
            commonMerchantsLoader.loadIfNeeded()
        }

        // Seeded here (not on first visit to a specific screen) so categories reliably exist
        // before anything -- SMS auto-import included -- ever needs to assign one.
        applicationScope.launch {
            seedDefaultCategoriesIfEmpty()
        }
    }

    private suspend fun seedDefaultCategoriesIfEmpty() {
        if (categoryRepository.getAllCategories().first().isNotEmpty()) return
        val defaultCategories = listOf(
            Category(name = "Food", icon = "🍔", color = "#FF5722"),
            Category(name = "Shopping", icon = "🛍️", color = "#E91E63"),
            Category(name = "Transportation", icon = "🚗", color = "#9C27B0"),
            Category(name = "Entertainment", icon = "🎬", color = "#673AB7"),
            Category(name = "Bills & Utilities", icon = "💡", color = "#3F51B5"),
            Category(name = "Healthcare", icon = "🏥", color = "#2196F3"),
            Category(name = "Education", icon = "📚", color = "#009688"),
            Category(name = "Groceries", icon = "🛒", color = "#4CAF50"),
            Category(name = "Personal Care", icon = "💆", color = "#8BC34A"),
            Category(name = "Gifts & Donations", icon = "🎁", color = "#FFC107"),
            Category(name = "Travel", icon = "✈️", color = "#FF9800"),
            Category(name = "Other", icon = "📝", color = "#795548")
        )
        defaultCategories.forEach { category ->
            categoryRepository.insertCategory(category)
        }
        Log.d(TAG, "✓ Seeded ${defaultCategories.size} default categories")
    }
}
