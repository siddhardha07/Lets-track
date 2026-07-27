package com.letstrack.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.letstrack.app.service.TransactionReviewService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Debug-only receiver for simulating an incoming bank SMS without a real payment.
 * Registered only in the debug manifest (src/debug/AndroidManifest.xml) - not part
 * of release builds. Lets us test the exact same code path a real SMS takes
 * (including the background/foreground-service exemption timing) without needing
 * a real SMS or spending real money.
 *
 * Trigger from a shell with the app in the background:
 * adb shell am broadcast -a com.letstrack.app.DEBUG_SIMULATE_SMS \
 *   --es sender "AD-IDFCFB-S" \
 *   --es body "Your A/c XX3937 debited by Rs.249.00 on 26-07-26 at TEST MERCHANT UPI Ref 123456. Avl Bal Rs.90000.00"
 */
@AndroidEntryPoint
class DebugSmsSimulatorReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsProcessor: SmsProcessor

    @Inject
    lateinit var transactionReviewService: TransactionReviewService

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "DebugSmsSimulator"
        const val ACTION_SIMULATE_SMS = "com.letstrack.app.DEBUG_SIMULATE_SMS"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != ACTION_SIMULATE_SMS) return

        val sender = intent.getStringExtra("sender")
        val body = intent.getStringExtra("body")
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

        if (sender == null || body == null) {
            Log.e(TAG, "❌ Missing 'sender' or 'body' extra")
            return
        }

        Log.d(TAG, "🧪 Simulated SMS from $sender: ${body.take(100)}")
        handleIncomingSms(context, scope, smsProcessor, transactionReviewService, sender, body, timestamp)
    }
}
