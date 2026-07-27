package com.letstrack.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper class to handle battery optimization exemption.
 * Standard Android Doze whitelisting is requestable from code via a system dialog.
 * OEM-specific background restrictions (Vivo Autostart, etc.) have no official API -
 * we can only best-effort deep-link to the OEM settings screen.
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

    /**
     * Best-effort deep link into Vivo/iQOO's OriginOS "Autostart" or background power
     * management screen. There is no official API for this - these component names are
     * undocumented and can change between OriginOS versions, so this may silently fail
     * on some devices, in which case the caller should fall back to manual instructions.
     */
    fun openOemAutostartSettings(): Boolean {
        if (!isVivoDevice()) return false

        val candidates = listOf(
            Intent().setClassName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ),
            Intent().setClassName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManagerNoStart"
            ),
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        )

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Autostart screen not available via ${intent.component}: ${e.message}")
            }
        }
        return false
    }

    fun isVivoDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("vivo")
    }
}
