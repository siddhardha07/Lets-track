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

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            Log.d(TAG, "📱 Got ${messages?.size ?: 0} SMS messages")

            messages?.forEach { smsMessage ->
                processSmsMessage(context, smsMessage)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing SMS: ${e.message}", e)
        }
    }

    private fun processSmsMessage(context: Context?, message: SmsMessage) {
        val sender = message.displayOriginatingAddress ?: ""
        val body = message.messageBody ?: ""
        val timestamp = message.timestampMillis

        Log.d(TAG, "📨 ===== SMS RECEIVED =====")
        Log.d(TAG, "📨 Sender: $sender")
        Log.d(TAG, "📨 Body preview: ${body.take(100)}...")

        if (context == null) return
        handleIncomingSms(context, scope, smsProcessor, transactionReviewService, sender, body, timestamp)
    }
}
