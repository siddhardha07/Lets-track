package com.letstrack.app

import android.app.Application
import android.util.Log
import com.letstrack.app.ml.CommonMerchantsLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    }
}
