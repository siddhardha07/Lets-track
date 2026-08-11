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

// DLT sender-ID codes for Indian banks, i.e. the stable middle segment of a sender like
// "AD-HDFCBK-S" (the "AD-"/"AX-"/"BZ-"/"JM-"/etc circle prefix varies by telecom
// operator/route and isn't part of a bank's identity - matching on it caused real gaps:
// e.g. SBI's own list only had "SBIINB" (internet banking), so its UPI-service sender
// "SBIUPI" - and every other bank not on this list at all (Bank of Baroda, HSBC, RBL,
// Karnataka Bank, and a dozen more) - never matched and got silently dropped before
// content was even looked at. "BOBBNK" here used to be backwards too: Bank of Baroda's
// real code is "BOB", so `sender.contains("BOBBNK")` could never be true.
private val BANK_SENDER_PATTERNS = listOf(
    "HDFCBK", "SBIINB", "SBIUPI", "SBI", "ICICI", "AXISBK", "AXIS", "KOTAKB", "KOTAK", "IDFCFB", "IDFC",
    "YESBNK", "INDUSB", "INDUS", "BOB", "CANBNK", "PNBSMS", "PNB",
    "UNIONB", "FEDBNK", "SCBANK", "AUBANK", "BANDHN", "BOIIND", "CBI", "CUBANK",
    "DBSBNK", "EQUITAS", "HSBCIN", "HSBC", "IDBIBK", "INDBNK", "IOBCHN", "KBL",
    "PSBIND", "RBLBNK", "SIBLTD", "UCOBNK", "CITI", "iMobile"
)

/**
 * Sender-based classification requires a bank code to be on BANK_SENDER_PATTERNS above,
 * which can never be a complete list - new banks, new DLT codes, or a code we just haven't
 * seen yet will always be possible. As a fallback for senders that don't match anything on
 * the list, fall back to content alone: a real transaction SMS reliably has an amount AND
 * a debit/credit-type keyword AND some account/payment-context word. Requiring all three
 * (rather than just debit/credit keywords, which show up in plenty of non-bank text) keeps
 * this fallback from misfiring on unrelated personal or promotional messages.
 */
private fun looksLikeBankSmsByContentAlone(body: String): Boolean {
    val hasAmount = Regex("""(?:Rs\.?|INR|₹)\s*[\d,]+(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE).containsMatchIn(body)
    val hasTransactionKeyword = listOf(
        "debited", "credited", "withdrawn", "deposited", "spent", "debit", "credit"
    ).any { body.contains(it, ignoreCase = true) }
    val hasAccountContext = listOf(
        "a/c", "account", "upi", "bank", "card", "avl bal", "available balance", "vpa"
    ).any { body.contains(it, ignoreCase = true) }

    return hasAmount && hasTransactionKeyword && hasAccountContext
}

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

    if (hasBankSender && hasTransactionKeywords) return true

    // Unknown sender - fall back to judging the message on its own content instead of
    // rejecting it outright, so a bank we haven't listed above isn't silently dropped.
    return looksLikeBankSmsByContentAlone(body)
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
    timestamp: Long,
    // Called once the message is fully handled (or immediately if it's skipped).
    // The real SmsBroadcastReceiver uses this to know when it's safe to release
    // its goAsync() hold - without it, Android is free to freeze/kill the process
    // the instant onReceive() returns, before our launched coroutine finishes the
    // DB write, which is why some messages were silently getting dropped.
    onComplete: () -> Unit = {}
) {
    if (!isBankSms(sender, body)) {
        Log.d(TAG, "ℹ️ Non-bank SMS, ignoring")
        onComplete()
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
            // Used to stop OverlayService here whenever nothing needed review - but that meant
            // the service (and its foreground-service process priority) only existed for the
            // few seconds around handling one SMS. The rest of the time, the app was a plain
            // cached background process, eligible for Android's broadcast-freezing ("Cached
            // Apps Freezer") - confirmed live on-device: a real SMS_RECEIVED broadcast sat
            // deferred for over 2 hours before delivery because the process was frozen.
            // OverlayService now runs continuously (started once from LetsTrackApp.onCreate
            // and BootReceiver) specifically so the process stays in the foreground-service
            // priority class, which is exempt from that freeze - see LetsTrackApp for the
            // persistent start.
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing bank SMS: ${e.message}", e)
        } finally {
            onComplete()
        }
    }
}
