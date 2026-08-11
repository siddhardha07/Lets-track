package com.letstrack.app

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.letstrack.app.domain.model.DefaultCategories
import com.letstrack.app.domain.repository.CategoryRepository
import com.letstrack.app.ml.CommonMerchantsLoader
import com.letstrack.app.service.OverlayService
import com.letstrack.app.sms.SmsPermissionHandler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

    @Inject
    lateinit var categoryRepository: CategoryRepository

    @Inject
    lateinit var smsPermissionHandler: SmsPermissionHandler

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

        // Seeded here (not on first visit to a specific screen) so categories reliably exist
        // before anything -- SMS auto-import included -- ever needs to assign one.
        applicationScope.launch {
            seedDefaultCategoriesIfEmpty()
        }

        startPersistentMonitoring()
    }

    /**
     * Starts OverlayService once at app launch and never stops it reactively (see
     * TransactionReviewService.hideOverlay and SmsIngestPipeline - both used to stopService()
     * it whenever there was nothing to show, which meant the app spent almost all its time as
     * a plain cached background process). Confirmed live on-device via
     * `dumpsys activity broadcasts`: a real SMS_RECEIVED broadcast sat DEFERRED for over 2
     * hours because the process was frozen (state:FRZ|FROZEN, reason: mBroadcastConsumerDefer-
     * ForFrozen) - Android's own background broadcast-freezing, not anything OEM-specific.
     * Processes holding an active foreground service are specifically exempted from that
     * freeze, which is the actual mechanism this is relying on to keep SMS delivery prompt.
     * Also called from BootReceiver so it resumes after a reboot without the user reopening
     * the app first.
     */
    private fun startPersistentMonitoring() {
        if (!smsPermissionHandler.hasReadSmsPermission()) {
            Log.d(TAG, "No SMS permission yet - not starting persistent monitoring service")
            return
        }
        try {
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            Log.d(TAG, "✓ Started persistent OverlayService to keep the process unfrozen for SMS delivery")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start persistent OverlayService: ${e.message}", e)
        }
    }

    private suspend fun seedDefaultCategoriesIfEmpty() {
        if (categoryRepository.getAllCategories().first().isNotEmpty()) return
        DefaultCategories.ALL.forEach { category ->
            categoryRepository.insertCategory(category)
        }
        Log.d(TAG, "✓ Seeded ${DefaultCategories.ALL.size} default categories")
    }
}
