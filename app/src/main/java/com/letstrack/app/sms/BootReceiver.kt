package com.letstrack.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.letstrack.app.service.OverlayService

/**
 * Boot receiver to re-initialize SMS monitoring after device restart
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Log.d(TAG, "Device booted, SMS receiver will be automatically registered")
            // The SmsBroadcastReceiver is registered in manifest, so it will automatically work

            // LetsTrackApp.onCreate() already starts this when the process spins up to handle
            // this exact broadcast, but starting it explicitly here too is cheap and removes
            // any dependency on that ordering - the persistent foreground service is what keeps
            // the process out of Android's background-broadcast-freeze after boot, the same way
            // it does during normal use (see LetsTrackApp.startPersistentMonitoring).
            try {
                ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
                Log.d(TAG, "✓ Started persistent OverlayService after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start OverlayService after boot: ${e.message}", e)
            }
        }
    }
}
