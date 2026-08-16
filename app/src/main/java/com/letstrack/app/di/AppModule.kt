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
import com.letstrack.app.data.repository.BankAccountRepositoryImpl
import com.letstrack.app.data.repository.BudgetRepositoryImpl
import com.letstrack.app.data.repository.CategoryRepositoryImpl
import com.letstrack.app.data.repository.ChatRepositoryImpl
import com.letstrack.app.data.repository.ExpenseRepositoryImpl
import com.letstrack.app.data.repository.GoalContributionRepositoryImpl
import com.letstrack.app.data.repository.GoalRepositoryImpl
import com.letstrack.app.domain.repository.BankAccountRepository
import com.letstrack.app.domain.repository.BudgetRepository
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.domain.repository.ChatRepository
import com.letstrack.app.domain.repository.ExpenseRepository
import com.letstrack.app.domain.repository.GoalContributionRepository
import com.letstrack.app.domain.repository.GoalRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AiDataStoreQualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UpdateDataStoreQualifier

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")
private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_prefs")
private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_prefs")

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
    @AiDataStoreQualifier
    fun provideAiDataStore(app: Application): DataStore<Preferences> {
        return app.aiDataStore
    }

    @Provides
    @Singleton
    @UpdateDataStoreQualifier
    fun provideUpdateDataStore(app: Application): DataStore<Preferences> {
        return app.updateDataStore
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

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
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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

    // Migration from version 7 to 8 (savings goals: goal + manual contribution log)
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS goals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    targetAmount REAL NOT NULL,
                    photoUri TEXT,
                    link TEXT,
                    linkedAccountId INTEGER,
                    sortOrder INTEGER,
                    isAchieved INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    achievedAt INTEGER
                )
            """)
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS goal_contributions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    goalId INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    date INTEGER NOT NULL,
                    note TEXT
                )
            """)
        }
    }

    @Provides
    fun provideGoalDao(database: LetsTrackDatabase): GoalDao {
        return database.goalDao()
    }

    @Provides
    fun provideGoalContributionDao(database: LetsTrackDatabase): GoalContributionDao {
        return database.goalContributionDao()
    }

    @Provides
    @Singleton
    fun provideGoalRepository(goalDao: GoalDao): GoalRepository {
        return GoalRepositoryImpl(goalDao)
    }

    @Provides
    @Singleton
    fun provideGoalContributionRepository(dao: GoalContributionDao): GoalContributionRepository {
        return GoalContributionRepositoryImpl(dao)
    }

    // Migration from version 8 to 9 (AI chat: sessions + messages)
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """)
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """)
        }
    }

    @Provides
    fun provideChatSessionDao(database: LetsTrackDatabase): ChatSessionDao {
        return database.chatSessionDao()
    }

    @Provides
    fun provideChatMessageDao(database: LetsTrackDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(sessionDao: ChatSessionDao, messageDao: ChatMessageDao): ChatRepository {
        return ChatRepositoryImpl(sessionDao, messageDao)
    }

    // Migration from version 9 to 10 (merchant seed cleanup) -- the bundled common_merchants.json
    // used to have ~10,000 entries, all with an identical, made-up confidence of 0.95, which is
    // exactly what "fake data" looks like, not a real signal. Deletes only source='pre-populated'
    // rows (the seeded ones) -- genuinely learned rows (source='user-correction', from real
    // corrections the user made) are untouched. CommonMerchantsLoader's own "already loaded" flag
    // is bumped separately so it re-seeds from the new, much smaller, curated asset file
    // afterward.
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DELETE FROM merchant_categories WHERE source = 'pre-populated'")
        }
    }

    // Migration from version 10 to 11 -- CommonMerchantsLoader only inserts merchant names not
    // already present (deliberately: it must never overwrite a real user-correction row sharing
    // that name), so bumping its "already loaded" flag alone wouldn't apply corrected
    // categories/confidence for merchants that were already seeded under MIGRATION_9_10. Wiping
    // pre-populated rows again forces a clean reseed from the current asset file, same pattern
    // as that migration.
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DELETE FROM merchant_categories WHERE source = 'pre-populated'")
        }
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
