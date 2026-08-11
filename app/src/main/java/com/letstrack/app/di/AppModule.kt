package com.letstrack.app.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.letstrack.app.data.local.DatabaseCallback
import com.letstrack.app.data.local.LetsTrackDatabase
import com.letstrack.app.data.local.dao.BankAccountDao
import com.letstrack.app.data.local.dao.BudgetDao
import com.letstrack.app.data.local.dao.CategoryDao
import com.letstrack.app.data.local.dao.ExpenseDao
import com.letstrack.app.data.local.dao.SmsTransactionDao
import com.letstrack.app.data.local.dao.MerchantCategoryDao
import com.letstrack.app.data.local.dao.UserCorrectionDao
import com.letstrack.app.data.local.dao.PendingReviewDao
import com.letstrack.app.data.local.dao.SubCategoryDao
import com.letstrack.app.data.repository.BankAccountRepositoryImpl
import com.letstrack.app.data.repository.BudgetRepositoryImpl
import com.letstrack.app.data.repository.CategoryRepositoryImpl
import com.letstrack.app.data.repository.ExpenseRepositoryImpl
import com.letstrack.app.domain.repository.BankAccountRepository
import com.letstrack.app.domain.repository.BudgetRepository
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }

    @Provides
    @Singleton
    fun provideThemeDataStore(app: Application): DataStore<Preferences> {
        return app.themeDataStore
    }

    @Provides
    @Singleton
    fun provideDatabase(
        app: Application,
        callback: DatabaseCallback
    ): LetsTrackDatabase {
        return Room.databaseBuilder(
            app,
            LetsTrackDatabase::class.java,
            "letstrack_database"
        )
            .addCallback(callback)
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration() // For development - remove in production
            .build()
    }

    // Migration from version 5 to 6 (AI features)
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create merchant_categories table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS merchant_categories (
                    merchantName TEXT PRIMARY KEY NOT NULL,
                    mainCategory TEXT NOT NULL,
                    subCategory TEXT,
                    confidence REAL NOT NULL,
                    source TEXT NOT NULL,
                    lastUsed INTEGER NOT NULL,
                    usageCount INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL
                )
            """)

            // Create user_category_corrections table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS user_category_corrections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    merchantName TEXT NOT NULL,
                    transactionAmount REAL NOT NULL,
                    originalCategory TEXT NOT NULL,
                    originalSubCategory TEXT,
                    originalConfidence REAL NOT NULL,
                    correctedCategory TEXT NOT NULL,
                    correctedSubCategory TEXT,
                    wasAccepted INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """)

            // Create pending_review_transactions table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS pending_review_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    expenseId INTEGER NOT NULL,
                    merchantName TEXT NOT NULL,
                    amount REAL NOT NULL,
                    suggestedCategory TEXT NOT NULL,
                    suggestedSubCategory TEXT,
                    confidence REAL NOT NULL,
                    fullSmsMessage TEXT,
                    createdAt INTEGER NOT NULL
                )
            """)

            // Create sub_categories table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS sub_categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    mainCategory TEXT NOT NULL,
                    subCategoryName TEXT NOT NULL,
                    icon TEXT,
                    isDefault INTEGER NOT NULL DEFAULT 0,
                    displayOrder INTEGER NOT NULL DEFAULT 0
                )
            """)

            // Add new columns to expenses table
            database.execSQL("ALTER TABLE expenses ADD COLUMN subCategory TEXT")
            database.execSQL("ALTER TABLE expenses ADD COLUMN categorizationSource TEXT NOT NULL DEFAULT 'manual'")
            database.execSQL("ALTER TABLE expenses ADD COLUMN isPendingReview INTEGER NOT NULL DEFAULT 0")
        }
    }

    // Migration from version 6 to 7 (monthly budgets: overall + per-category)
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS budgets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    categoryId INTEGER,
                    amount REAL NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """)
        }
    }

    @Provides
    fun provideBudgetDao(database: LetsTrackDatabase): BudgetDao {
        return database.budgetDao()
    }

    @Provides
    @Singleton
    fun provideBudgetRepository(budgetDao: BudgetDao): BudgetRepository {
        return BudgetRepositoryImpl(budgetDao)
    }

    @Provides
    fun provideMerchantCategoryDao(database: LetsTrackDatabase): MerchantCategoryDao {
        return database.merchantCategoryDao()
    }

    @Provides
    fun provideUserCorrectionDao(database: LetsTrackDatabase): UserCorrectionDao {
        return database.userCorrectionDao()
    }

    @Provides
    fun providePendingReviewDao(database: LetsTrackDatabase): PendingReviewDao {
        return database.pendingReviewDao()
    }

    @Provides
    fun provideSubCategoryDao(database: LetsTrackDatabase): SubCategoryDao {
        return database.subCategoryDao()
    }

    @Provides
    fun provideExpenseDao(database: LetsTrackDatabase): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    fun provideCategoryDao(database: LetsTrackDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideBankAccountDao(database: LetsTrackDatabase): BankAccountDao {
        return database.bankAccountDao()
    }

    @Provides
    fun provideSmsTransactionDao(database: LetsTrackDatabase): SmsTransactionDao {
        return database.smsTransactionDao()
    }

    @Provides
    @Singleton
    fun provideExpenseRepository(expenseDao: ExpenseDao): ExpenseRepository {
        return ExpenseRepositoryImpl(expenseDao)
    }

    @Provides
    @Singleton
    fun provideCategoryRepository(categoryDao: CategoryDao): CategoryRepository {
        return CategoryRepositoryImpl(categoryDao)
    }

    @Provides
    @Singleton
    fun provideBankAccountRepository(bankAccountDao: BankAccountDao): BankAccountRepository {
        return BankAccountRepositoryImpl(bankAccountDao)
    }
}
