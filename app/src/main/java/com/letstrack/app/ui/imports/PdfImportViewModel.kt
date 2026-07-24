package com.letstrack.app.ui.imports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.data.importer.JsonImporter
import com.letstrack.app.data.local.dao.ExpenseDao
import com.letstrack.app.data.local.entity.ExpenseEntity
import com.letstrack.app.data.parser.ParsedTransaction
import com.letstrack.app.data.parser.PdfParser
import com.letstrack.app.data.parser.PdfParseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PdfImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonImporter: JsonImporter,
    private val expenseDao: ExpenseDao
) : ViewModel() {

    private val pdfParser = PdfParser(context)

    private val _uiState = MutableStateFlow(PdfImportUiState())
    val uiState: StateFlow<PdfImportUiState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            selectedFileUri = uri,
            fileName = uri.lastPathSegment ?: "Unknown file"
        )
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }
    
    fun importJsonFile(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            processingStep = "Reading JSON file...",
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open file")

                val result = jsonImporter.importFromJson(inputStream)

                if (result.success > 0) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        successMessage = "✓ Imported ${result.success} transactions successfully!",
                        errorMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = result.errors.firstOrNull() ?: "Failed to import transactions"
                    )
                }

                inputStream.close()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Error reading JSON: ${e.message}"
                )
            }
        }
    }

    fun startParsing() {
        val uri = _uiState.value.selectedFileUri ?: return
        val password = _uiState.value.password.takeIf { it.isNotEmpty() }

        android.util.Log.d("PdfImportViewModel", "startParsing called with URI: $uri")
        
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            processingStep = "Opening PDF...",
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                android.util.Log.d("PdfImportViewModel", "Calling pdfParser.parsePdf...")
                val result = pdfParser.parsePdf(uri, password)
                
                android.util.Log.d("PdfImportViewModel", "Parse result: success=${result.success}, transactions=${result.transactions.size}, error=${result.errorMessage}")
                
                if (result.success && result.transactions.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        processingStep = "Saving transactions to database...",
                        parseResult = result
                    )

                    // Save transactions to database
                    val savedCount = saveTransactionsToDatabase(result.transactions)
                    
                    // Export to JSON
                    _uiState.value = _uiState.value.copy(
                        processingStep = "Exporting to JSON..."
                    )
                    val jsonFile = File(context.getExternalFilesDir(null), "transactions_${System.currentTimeMillis()}.json")
                    val exported = pdfParser.exportToJson(result.transactions, jsonFile)

                    if (exported) {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            processingStep = "Completed",
                            csvFilePath = jsonFile.absolutePath,
                            successMessage = "✓ Imported $savedCount transactions to your expense tracker!"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            successMessage = "✓ Imported $savedCount transactions (JSON export failed)"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = result.errorMessage ?: "No transactions found in PDF"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("PdfImportViewModel", "Exception in startParsing", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Error: ${e.message}\n${e.stackTraceToString()}"
                )
            }
        }
    }
    
    private suspend fun saveTransactionsToDatabase(transactions: List<ParsedTransaction>): Int {
        var savedCount = 0
        val dateFormat = SimpleDateFormat("dd MMM yy HH:mm", Locale.ENGLISH)
        
        transactions.forEach { tx ->
            try {
                val date = try {
                    dateFormat.parse(tx.dateTime)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                
                val expense = ExpenseEntity(
                    amount = tx.amount,
                    categoryId = 1, // Default category - user can recategorize later
                    title = tx.merchantName.ifEmpty { "Transaction" },
                    description = tx.transactionDetails,
                    notes = "",
                    date = date,
                    source = "PDF",
                    sourceReference = "Bank Statement Import",
                    merchantName = tx.merchantName,
                    upiId = tx.upiId,
                    bankReference = tx.refChequeNo,
                    transactionType = if (tx.isDebit) "DEBIT" else "CREDIT"
                )
                
                expenseDao.insertExpense(expense)
                savedCount++
            } catch (e: Exception) {
                android.util.Log.e("PdfImportViewModel", "Failed to save transaction: ${tx.merchantName}", e)
            }
        }
        
        return savedCount
    }

    fun clearState() {
        _uiState.value = PdfImportUiState()
    }
}

data class PdfImportUiState(
    val selectedFileUri: Uri? = null,
    val fileName: String = "",
    val password: String = "",
    val isProcessing: Boolean = false,
    val processingStep: String = "",
    val parseResult: PdfParseResult? = null,
    val csvFilePath: String? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)
