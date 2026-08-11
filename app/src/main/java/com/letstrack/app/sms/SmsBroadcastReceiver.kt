package com.letstrack.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.letstrack.app.service.TransactionReviewService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Broadcast receiver for incoming SMS messages
 * Listens for SMS and processes bank transaction messages
 */
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsProcessor: SmsProcessor

    @Inject
    lateinit var transactionReviewService: TransactionReviewService

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "📱 onReceive called! Action: ${intent?.action}")

        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "⚠️ Not SMS_RECEIVED_ACTION, ignoring")
            return
        }

        Log.d(TAG, "📱 SMS_RECEIVED_ACTION confirmed! Getting messages...")

        if (context == null) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            Log.d(TAG, "📱 Got ${messages?.size ?: 0} SMS messages")

            if (messages.isNullOrEmpty()) return

            // Hold the broadcast open until every message is actually processed.
            // Without goAsync(), Android considers this broadcast "done" the instant
            // onReceive() returns and is free to freeze or kill the app process at any
            // point after that - including mid-way through the DB insert running on
            // Dispatchers.IO below. That race is why some SMS were being received
            // (logged) but never turning into a transaction: the process got killed
            // before the coroutine finished. This is especially aggressive on OEM
            // skins (Vivo/Funtouch included) that impose their own background limits
            // on top of stock Android's.
            val pendingResult = goAsync()
            val finished = java.util.concurrent.atomic.AtomicBoolean(false)
            val finishOnce = {
                if (finished.compareAndSet(false, true)) {
                    pendingResult.finish()
                }
            }
            val remaining = java.util.concurrent.atomic.AtomicInteger(messages.size)
            val onOneDone = {
                if (remaining.decrementAndGet() == 0) {
                    finishOnce()
                }
            }

            try {
                messages.forEach { smsMessage ->
                    processSmsMessage(context, smsMessage, onOneDone)
                }
            } catch (e: Exception) {
                // Make sure we still release the broadcast if something throws
                // synchronously before every message got its onComplete callback wired up.
                Log.e(TAG, "❌ Error dispatching SMS for processing: ${e.message}", e)
                finishOnce()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing SMS: ${e.message}", e)
        }
    }

    private fun processSmsMessage(context: Context, message: SmsMessage, onComplete: () -> Unit) {
        val sender = message.displayOriginatingAddress ?: ""
        val body = message.messageBody ?: ""
        val timestamp = message.timestampMillis

        Log.d(TAG, "📨 ===== SMS RECEIVED =====")
        Log.d(TAG, "📨 Sender: $sender")
        Log.d(TAG, "📨 Body preview: ${body.take(100)}...")

        handleIncomingSms(context, scope, smsProcessor, transactionReviewService, sender, body, timestamp, onComplete)
    }
}
