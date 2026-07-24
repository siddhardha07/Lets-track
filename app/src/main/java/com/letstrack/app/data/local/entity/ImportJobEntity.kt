package com.letstrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Import Job Entity - Track PDF/CSV import jobs
 */
@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val fileType: String, // PDF, CSV
    val fileUri: String,
    
    // Import status
    val status: String, // PENDING, PROCESSING, COMPLETED, FAILED
    val totalTransactions: Int = 0,
    val processedTransactions: Int = 0,
    val skippedTransactions: Int = 0, // Duplicates
    val errorMessage: String = "",
    
    // Metadata
    val bankName: String = "",
    val accountNumber: String = "",
    val statementPeriod: String = "",
    
    val createdAt: Long,
    val completedAt: Long? = null
)
