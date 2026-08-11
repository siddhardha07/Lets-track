package com.letstrack.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.letstrack.app.data.local.entity.CategoryEntity
import com.letstrack.app.di.ApplicationScope
import com.letstrack.app.domain.model.DefaultCategories
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

        // Sourced from DefaultCategories so this list can't drift from the one
        // LetsTrackApp's fallback seeder uses (see DefaultCategories for why that matters).
        val defaultCategories = DefaultCategories.ALL.map { category ->
            CategoryEntity(
                name = category.name,
                icon = category.icon,
                color = category.color,
                isDefault = true
            )
        }

        categoryDao.insertCategories(defaultCategories)
    }
}
