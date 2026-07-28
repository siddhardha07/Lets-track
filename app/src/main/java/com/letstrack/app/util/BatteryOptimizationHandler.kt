package com.letstrack.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper class to handle battery optimization exemption via the standard Android Doze
 * whitelisting API.
 */
class BatteryOptimizationHandler(private val context: Context) {

    companion object {
        private const val TAG = "BatteryOptimizationHandler"
    }

    /**
     * Check if the app is already exempt from standard Android battery optimization
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launch the system dialog asking the user to exempt this app from battery optimization.
     * This is an official Android API - the user still has to tap "Allow" on the system dialog.
     */
    fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request battery optimization exemption: ${e.message}", e)
            openBatteryOptimizationSettings()
        }
    }

    /**
     * Fallback: open the general battery optimization list screen
     */
    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery optimization settings: ${e.message}", e)
        }
    }

}
