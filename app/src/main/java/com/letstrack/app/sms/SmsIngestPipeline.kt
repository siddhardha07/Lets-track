package com.letstrack.app.sms

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.letstrack.app.service.OverlayService
import com.letstrack.app.service.TransactionReviewService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "SmsIngestPipeline"

// Common bank SMS sender patterns
private val BANK_SENDER_PATTERNS = listOf(
    "VM-", "DM-", "AD-", "AX-", "BP-", "CP-", "BX-", "JX-",
    "HDFCBK", "SBIINB", "ICICI", "AXIS", "KOTAK", "IDFC",
    "YESBNK", "INDUS", "BOBBNK", "CANBNK", "PNBSMS",
    "iMobile", "UNIONB", "FEDBNK", "SCBANK"
)

fun isBankSms(sender: String, body: String): Boolean {
    val hasBankSender = BANK_SENDER_PATTERNS.any { pattern ->
        sender.contains(pattern, ignoreCase = true)
    }

    val hasTransactionKeywords = body.let { msg ->
        msg.contains("debited", ignoreCase = true) ||
        msg.contains("credited", ignoreCase = true) ||
        msg.contains("withdrawn", ignoreCase = true) ||
        msg.contains("deposited", ignoreCase = true) ||
        msg.contains("spent", ignoreCase = true) ||
        msg.contains("UPI", ignoreCase = true) ||
        (msg.contains("account", ignoreCase = true) && msg.contains("Rs", ignoreCase = true))
    }

    return hasBankSender && hasTransactionKeywords
}

/**
 * Shared ingest path used by both the real SmsBroadcastReceiver and the
 * debug-only DebugSmsSimulatorReceiver, so a simulated SMS exercises exactly
 * the same code a real one does.
 */
fun handleIncomingSms(
    context: Context,
    scope: CoroutineScope,
    smsProcessor: SmsProcessor,
    transactionReviewService: TransactionReviewService,
    sender: String,
    body: String,
    timestamp: Long
) {
    if (!isBankSms(sender, body)) {
        Log.d(TAG, "ℹ️ Non-bank SMS, ignoring")
        return
    }

    Log.d(TAG, "🏦 BANK SMS DETECTED! Processing...")

    // Grab the foreground-service exemption SYNCHRONOUSLY, before any async work.
    // Android only allows starting a foreground service from the background for a
    // short window right after a broadcast fires - waiting until after DB/ML work
    // can miss that window and the overlay silently never appears outside the app.
    try {
        ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        Log.d(TAG, "🎯 Pre-started OverlayService to secure foreground exemption")
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to pre-start OverlayService: ${e.message}", e)
    }

    scope.launch {
        try {
            val processed = smsProcessor.processSms(
                sender = sender,
                message = body,
                timestamp = timestamp
            )
            Log.d(
                TAG,
                if (processed) "✅ SMS PROCESSED SUCCESSFULLY" else "⚠️ SMS NOT PROCESSED (duplicate or failed)"
            )
            // If nothing ended up needing review, stop the pre-started service so we
            // don't leave a dangling "monitoring" notification around.
            if (transactionReviewService.pendingTransaction.value == null) {
                context.stopService(Intent(context, OverlayService::class.java))
                Log.d(TAG, "🎯 No overlay needed - stopped pre-started OverlayService")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing bank SMS: ${e.message}", e)
        }
    }
}
