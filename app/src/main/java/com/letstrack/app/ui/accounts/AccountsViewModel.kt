package com.letstrack.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.data.local.dao.BankAccountDao
import com.letstrack.app.data.local.entity.BankAccountEntity
import com.letstrack.app.sms.SmsImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val bankAccountDao: BankAccountDao,
    private val smsImportService: SmsImportService
) : ViewModel() {
    
    val accounts: StateFlow<List<BankAccountEntity>> = 
        bankAccountDao.getAllActiveAccounts()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    
    private val _importProgress = MutableStateFlow<SmsImportService.ImportProgress?>(null)
    val importProgress: StateFlow<SmsImportService.ImportProgress?> = _importProgress.asStateFlow()
    
    private val _importResult = MutableStateFlow<SmsImportService.ImportResult?>(null)
    val importResult: StateFlow<SmsImportService.ImportResult?> = _importResult.asStateFlow()
    
    init {
        // Observe import progress
        viewModelScope.launch {
            smsImportService.importProgress.collect { progress ->
                _importProgress.value = progress
                
                // When completed, set result and clear progress
                if (progress is SmsImportService.ImportProgress.Completed) {
                    _importResult.value = SmsImportService.ImportResult.Success(
                        imported = progress.totalImported,
                        processed = progress.totalProcessed
                    )
                    _importProgress.value = null
                } else if (progress is SmsImportService.ImportProgress.Error) {
                    _importResult.value = SmsImportService.ImportResult.Failure(progress.message)
                    _importProgress.value = null
                }
            }
        }
    }
    
    fun startBulkImport(startDate: Long, endDate: Long) {
        viewModelScope.launch {
            _importProgress.value = SmsImportService.ImportProgress.InProgress(0, 0, "Starting...", "fetching")
            smsImportService.importSmsFromDateRange(startDate, endDate)
        }
    }
    
    fun clearImportResult() {
        _importResult.value = null
    }
    
    fun resetProgress() {
        _importProgress.value = null
        _importResult.value = null
    }
    
    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            bankAccountDao.deleteAccount(accountId)
        }
    }
}
