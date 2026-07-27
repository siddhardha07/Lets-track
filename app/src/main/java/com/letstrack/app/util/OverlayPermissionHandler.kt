package com.letstrack.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Helper class to handle overlay permission (SYSTEM_ALERT_WINDOW)
 * Required for showing transaction review overlay on top of other apps
 */
class OverlayPermissionHandler(private val context: Context) {

    /**
     * Check if overlay permission is granted
     * For Android M+ (API 23+), this requires user approval
     */
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Permission not required on older Android versions
        }
    }

    /**
     * Get intent to open overlay permission settings for this app
     */
    fun getOverlayPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            // Fallback for older versions - open app settings
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Open system settings to grant overlay permission
     */
    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.startActivity(getOverlayPermissionIntent())
        }
    }

    /**
     * Check if this Android version requires overlay permission
     */
    fun isOverlayPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}
