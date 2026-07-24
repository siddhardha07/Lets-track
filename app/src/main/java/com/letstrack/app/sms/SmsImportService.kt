package com.letstrack.app.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bulk SMS Import Service - Imports existing SMS from phone
 */
@Singleton
class SmsImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsProcessor: SmsProcessor
) {
    
    companion object {
        private const val TAG = "SmsImportService"
        
        // Common bank SMS sender patterns to filter
        private val BANK_SENDER_PATTERNS = listOf(
            "VM-", "DM-", "AD-", "AX-", "BP-", "CP-", "BX-", "JX-",
            "HDFCBK", "SBIINB", "ICICI", "AXIS", "KOTAK", "IDFC",
            "YESBNK", "INDUS", "BOBBNK", "CANBNK", "PNBSMS",
            "iMobile", "UNIONB", "FEDBNK", "SCBANK"
        )
    }
    
    private val _importProgress = MutableStateFlow<ImportProgress>(ImportProgress.Idle)
    val importProgress: StateFlow<ImportProgress> = _importProgress
    
    sealed class ImportProgress {
        object Idle : ImportProgress()
        data class InProgress(
            val current: Int,
            val total: Int,
            val message: String,
            val phase: String // "fetching", "parsing", "saving"
        ) : ImportProgress()
        data class Completed(val totalImported: Int, val totalProcessed: Int) : ImportProgress()
        data class Error(val message: String) : ImportProgress()
    }
    
    /**
     * Import SMS from last N months
     */
    suspend fun importSmsFromLastMonths(months: Int = 6): ImportResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting bulk SMS import for last $months months")
            _importProgress.value = ImportProgress.InProgress(0, 0, "Checking SMS permissions...", "fetching")
            
            // Check SMS permission
            if (!hasReadSmsPermission()) {
                val errorMsg = "SMS read permission not granted"
                Log.e(TAG, errorMsg)
                _importProgress.value = ImportProgress.Error(errorMsg)
                return@withContext ImportResult.Failure(errorMsg)
            }
            
            _importProgress.value = ImportProgress.InProgress(0, 0, "Preparing import...", "fetching")
            
            // Calculate date range
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.MONTH, -months)
            val startTime = calendar.timeInMillis
            
            Log.d(TAG, "Date range: ${startTime} to ${endTime}")
            
            // Query SMS inbox
            _importProgress.value = ImportProgress.InProgress(0, 0, "Reading messages...", "fetching")
            val smsMessages = querySmsInbox(startTime, endTime)
            
            Log.d(TAG, "Found ${smsMessages.size} SMS messages in inbox")
            
            // If no messages found, complete immediately
            if (smsMessages.isEmpty()) {
                Log.w(TAG, "No bank SMS found in the specified date range")
                _importProgress.value = ImportProgress.Completed(0, 0)
                return@withContext ImportResult.Success(0, 0)
            }
            
            _importProgress.value = ImportProgress.InProgress(0, smsMessages.size, "Parsing transactions...", "parsing")
            
            var processedCount = 0
            var importedCount = 0
            
            // Process each SMS
            smsMessages.forEachIndexed { index, sms ->
                try {
                    // Update phase to saving when actually processing
                    val phase = if (index < smsMessages.size / 2) "parsing" else "saving"
                    
                    val imported = smsProcessor.processSms(
                        sender = sms.sender,
                        message = sms.message,
                        timestamp = sms.timestamp
                    )
                    
                    if (imported) importedCount++
                    processedCount++
                    
                    // Update progress every 5 messages
                    if (index % 5 == 0 || index == smsMessages.size - 1) {
                        _importProgress.value = ImportProgress.InProgress(
                            current = processedCount,
                            total = smsMessages.size,
                            message = "Processing...",
                            phase = phase
                        )
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing SMS ${index + 1}: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "Import completed: $importedCount imported out of $processedCount processed")
            _importProgress.value = ImportProgress.Completed(importedCount, processedCount)
            
            ImportResult.Success(importedCount, processedCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during bulk import: ${e.message}", e)
            _importProgress.value = ImportProgress.Error(e.message ?: "Unknown error")
            ImportResult.Failure(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Query SMS inbox from ContentProvider
     */
    private fun querySmsInbox(startTime: Long, endTime: Long): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        
        try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,    // Sender
                Telephony.Sms.BODY,       // Message
                Telephony.Sms.DATE        // Timestamp
            )
            
            val selection = "${Telephony.Sms.DATE} BETWEEN ? AND ?"
            val selectionArgs = arrayOf(startTime.toString(), endTime.toString())
            val sortOrder = "${Telephony.Sms.DATE} DESC"
            
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressIndex) ?: ""
                    val body = cursor.getString(bodyIndex) ?: ""
                    val timestamp = cursor.getLong(dateIndex)
                    
                    // Filter only bank SMS
                    if (isBankSms(sender, body)) {
                        messages.add(
                            SmsMessage(
                                sender = sender,
                                message = body,
                                timestamp = timestamp
                            )
                        )
                    }
                }
            }
            
            Log.d(TAG, "Filtered ${messages.size} bank SMS from query")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SMS inbox: ${e.message}", e)
        }
        
        return messages
    }
    
    /**
     * Check if SMS is from a bank
     */
    private fun isBankSms(sender: String, body: String): Boolean {
        val hasBankSender = BANK_SENDER_PATTERNS.any { pattern ->
            sender.contains(pattern, ignoreCase = true)
        }
        
        val hasTransactionKeywords = body.let { msg ->
            msg.contains("debited", ignoreCase = true) ||
            msg.contains("credited", ignoreCase = true) ||
            msg.contains("withdrawn", ignoreCase = true) ||
            msg.contains("deposited", ignoreCase = true) ||
            msg.contains("UPI", ignoreCase = true) ||
            (msg.contains("account", ignoreCase = true) && msg.contains("Rs", ignoreCase = true))
        }
        
        return hasBankSender && hasTransactionKeywords
    }
    
    /**
     * Check if READ_SMS permission is granted
     */
    private fun hasReadSmsPermission(): Boolean {
        return try {
            context.checkSelfPermission(android.Manifest.permission.READ_SMS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SMS permission: ${e.message}", e)
            false
        }
    }
    
    data class SmsMessage(
        val sender: String,
        val message: String,
        val timestamp: Long
    )
    
    sealed class ImportResult {
        data class Success(val imported: Int, val processed: Int) : ImportResult()
        data class Failure(val error: String) : ImportResult()
    }
}
