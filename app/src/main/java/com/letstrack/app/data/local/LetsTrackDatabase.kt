package com.letstrack.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.letstrack.app.data.local.dao.BankAccountDao
import com.letstrack.app.data.local.dao.CategoryDao
import com.letstrack.app.data.local.dao.ExpenseDao
import com.letstrack.app.data.local.dao.SmsTransactionDao
import com.letstrack.app.data.local.dao.MerchantCategoryDao
import com.letstrack.app.data.local.dao.UserCorrectionDao
import com.letstrack.app.data.local.dao.PendingReviewDao
import com.letstrack.app.data.local.dao.SubCategoryDao
import com.letstrack.app.data.local.entity.*

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        SmsTransactionEntity::class,
        LearningHistoryEntity::class,
        ImportJobEntity::class,
        BankAccountEntity::class,
        MerchantCategoryEntity::class,
        UserCorrectionEntity::class,
        PendingReviewTransactionEntity::class,
        SubCategoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class LetsTrackDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun smsTransactionDao(): SmsTransactionDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao
    abstract fun userCorrectionDao(): UserCorrectionDao
    abstract fun pendingReviewDao(): PendingReviewDao
    abstract fun subCategoryDao(): SubCategoryDao
}
