package com.letstrack.app.ui.imports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.data.importer.CsvImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val csvImporter: CsvImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(CsvImportUiState())
    val uiState: StateFlow<CsvImportUiState> = _uiState.asStateFlow()

    fun importCsvFile(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            fileName = uri.lastPathSegment ?: "Unknown file",
            errorMessage = null,
            successMessage = null
        )

        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open file")

                val result = csvImporter.importFromCsv(inputStream)
                inputStream.close()

                if (result.success > 0) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        successMessage = "✓ Imported ${result.success} transaction${if (result.success == 1) "" else "s"} successfully!",
                        failedCount = result.failed,
                        rowErrors = result.errors
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = result.errors.firstOrNull() ?: "No transactions could be imported",
                        rowErrors = result.errors
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Error reading CSV: ${e.message}"
                )
            }
        }
    }

    fun clearState() {
        _uiState.value = CsvImportUiState()
    }
}

data class CsvImportUiState(
    val fileName: String = "",
    val isProcessing: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val failedCount: Int = 0,
    val rowErrors: List<String> = emptyList()
)
