package com.letstrack.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Broadcast receiver for incoming SMS messages
 * Listens for SMS and processes bank transaction messages
 */
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var smsProcessor: SmsProcessor
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        
        // Common bank SMS sender patterns
        private val BANK_SENDER_PATTERNS = listOf(
            "VM-", "DM-", "AD-", "AX-", "BP-", "CP-", "BX-", "JX-",
            "HDFCBK", "SBIINB", "ICICI", "AXIS", "KOTAK", "IDFC",
            "YESBNK", "INDUS", "BOBBNK", "CANBNK", "PNBSMS",
            "iMobile", "UNIONB", "FEDBNK", "SCBANK"
        )
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            messages?.forEach { smsMessage ->
                processSmsMessage(smsMessage)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS: ${e.message}", e)
        }
    }
    
    private fun processSmsMessage(message: SmsMessage) {
        val sender = message.displayOriginatingAddress ?: ""
        val body = message.messageBody ?: ""
        val timestamp = message.timestampMillis
        
        Log.d(TAG, "Received SMS from: $sender")
        
        // Check if this is a bank SMS
        if (isBankSms(sender, body)) {
            Log.d(TAG, "Bank SMS detected: ${body.take(50)}...")
            
            // Process asynchronously
            scope.launch {
                try {
                    smsProcessor.processSms(
                        sender = sender,
                        message = body,
                        timestamp = timestamp
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing bank SMS: ${e.message}", e)
                }
            }
        } else {
            Log.d(TAG, "Non-bank SMS, ignoring")
        }
    }
    
    private fun isBankSms(sender: String, body: String): Boolean {
        // Check sender pattern
        val hasBankSender = BANK_SENDER_PATTERNS.any { pattern ->
            sender.contains(pattern, ignoreCase = true)
        }
        
        // Check for transaction keywords
        val hasTransactionKeywords = body.let { msg ->
            msg.contains("debited", ignoreCase = true) ||
            msg.contains("credited", ignoreCase = true) ||
            msg.contains("withdrawn", ignoreCase = true) ||
            msg.contains("deposited", ignoreCase = true) ||
            msg.contains("spent", ignoreCase = true) ||
            msg.contains("UPI", ignoreCase = true) ||
            msg.contains("account", ignoreCase = true) && msg.contains("Rs", ignoreCase = true)
        }
        
        return hasBankSender && hasTransactionKeywords
    }
}
