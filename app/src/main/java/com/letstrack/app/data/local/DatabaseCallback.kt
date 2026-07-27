package com.letstrack.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.letstrack.app.data.local.entity.CategoryEntity
import com.letstrack.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class DatabaseCallback @Inject constructor(
    private val database: Provider<LetsTrackDatabase>,
    @ApplicationScope private val applicationScope: CoroutineScope
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        applicationScope.launch {
            populateDatabase()
        }
    }

    private suspend fun populateDatabase() {
        val categoryDao = database.get().categoryDao()
        
        // Insert default categories
        val defaultCategories = listOf(
            CategoryEntity(name = "Food", icon = "🍔", color = "#FF6B6B", isDefault = true),
            CategoryEntity(name = "Transportation", icon = "🚗", color = "#4ECDC4", isDefault = true),
            CategoryEntity(name = "Shopping", icon = "🛍️", color = "#FFE66D", isDefault = true),
            CategoryEntity(name = "Entertainment", icon = "🎬", color = "#A8E6CF", isDefault = true),
            CategoryEntity(name = "Bills & Utilities", icon = "💡", color = "#FF8B94", isDefault = true),
            CategoryEntity(name = "Health & Fitness", icon = "🏥", color = "#C7CEEA", isDefault = true),
            CategoryEntity(name = "Education", icon = "📚", color = "#B4A7D6", isDefault = true),
            CategoryEntity(name = "Personal Care", icon = "💇", color = "#FFDFD3", isDefault = true),
            CategoryEntity(name = "Travel", icon = "✈️", color = "#95E1D3", isDefault = true),
            CategoryEntity(name = "Other", icon = "📦", color = "#BDC3C7", isDefault = true)
        )
        
        categoryDao.insertCategories(defaultCategories)
    }
}
