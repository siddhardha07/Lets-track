package com.letstrack.app.di

import android.app.Application
import androidx.room.Room
import com.letstrack.app.data.local.DatabaseCallback
import com.letstrack.app.data.local.LetsTrackDatabase
import com.letstrack.app.data.local.dao.BankAccountDao
import com.letstrack.app.data.local.dao.CategoryDao
import com.letstrack.app.data.local.dao.ExpenseDao
import com.letstrack.app.data.local.dao.SmsTransactionDao
import com.letstrack.app.data.repository.BankAccountRepositoryImpl
import com.letstrack.app.data.repository.CategoryRepositoryImpl
import com.letstrack.app.data.repository.ExpenseRepositoryImpl
import com.letstrack.app.domain.repository.BankAccountRepository
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

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

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
            .fallbackToDestructiveMigration() // For development - remove in production
            .build()
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
