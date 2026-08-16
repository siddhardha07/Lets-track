package com.letstrack.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.letstrack.app.data.local.dao.BankAccountDao
import com.letstrack.app.data.local.dao.BudgetDao
import com.letstrack.app.data.local.dao.CategoryDao
import com.letstrack.app.data.local.dao.ChatMessageDao
import com.letstrack.app.data.local.dao.ChatSessionDao
import com.letstrack.app.data.local.dao.ExpenseDao
import com.letstrack.app.data.local.dao.GoalContributionDao
import com.letstrack.app.data.local.dao.GoalDao
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
        SubCategoryEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 11,
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
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun goalContributionDao(): GoalContributionDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
}
