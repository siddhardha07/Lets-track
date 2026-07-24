package com.letstrack.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.letstrack.app.data.local.dao.CategoryDao
import com.letstrack.app.data.local.dao.ExpenseDao
import com.letstrack.app.data.local.entity.*

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        SmsTransactionEntity::class,
        LearningHistoryEntity::class,
        ImportJobEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LetsTrackDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
}
