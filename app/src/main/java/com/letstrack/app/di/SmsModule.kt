package com.letstrack.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.letstrack.app.sms.SmsParser
import javax.inject.Singleton

/**
 * Hilt module for SMS-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object SmsModule {
    
    @Provides
    @Singleton
    fun provideSmsParser(): SmsParser {
        return SmsParser()
    }
}
