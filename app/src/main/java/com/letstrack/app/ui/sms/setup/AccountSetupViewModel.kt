package com.letstrack.app.ui.sms.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.data.sms.SmsPatternParser
import com.letstrack.app.domain.model.BankAccount
import com.letstrack.app.domain.repository.BankAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountSetupViewModel @Inject constructor(
    private val bankAccountRepository: BankAccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSetupUiState())
    val uiState: StateFlow<AccountSetupUiState> = _uiState.asStateFlow()
    
    private val smsPatternParser = SmsPatternParser()

    fun onDebitSmsChange(sms: String) {
        _uiState.update { it.copy(debitSms = sms, errorMessage = null) }
    }

    fun onCreditSmsChange(sms: String) {
        _uiState.update { it.copy(creditSms = sms, errorMessage = null) }
    }
    
    fun onAccountNicknameChange(nickname: String) {
        _uiState.update { it.copy(accountNickname = nickname) }
    }

    fun parseAndSaveAccount(onSuccess: () -> Unit) {
        val state = _uiState.value
        
        if (state.debitSms.isBlank()) {
            _uiState.update { 
                it.copy(errorMessage = "❌ Please paste a debit transaction SMS") 
            }
            return
        }
        
        if (state.creditSms.isBlank()) {
            _uiState.update { 
                it.copy(errorMessage = "❌ Please paste a credit transaction SMS") 
            }
            return
        }
        
        // Basic validation - check if SMS contains transaction keywords
        val hasDebitKeyword = state.debitSms.lowercase().let { sms ->
            sms.contains("debit") || sms.contains("withdrawn") || sms.contains("spent") || sms.contains("paid")
        }
        
        val hasCreditKeyword = state.creditSms.lowercase().let { sms ->
            sms.contains("credit") || sms.contains("deposit") || sms.contains("received")
        }
        
        if (!hasDebitKeyword) {
            _uiState.update { 
                it.copy(errorMessage = "⚠️ Debit SMS doesn't look like a transaction. Make sure it contains words like 'debited' or 'spent'") 
            }
            return
        }
        
        if (!hasCreditKeyword) {
            _uiState.update { 
                it.copy(errorMessage = "⚠️ Credit SMS doesn't look like a transaction. Make sure it contains words like 'credited' or 'deposited'") 
            }
            return
        }
        
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                // Parse the SMS samples
                val parsedPattern = smsPatternParser.parseAccountFromSamples(
                    state.debitSms,
                    state.creditSms
                )
                
                if (parsedPattern == null) {
                    _uiState.update { 
                        it.copy(
                            isProcessing = false,
                            errorMessage = "❌ Failed to parse SMS format. Please check the SMS text and try again."
                        ) 
                    }
                    return@launch
                }
                
                // Show preview for user confirmation
                _uiState.update { 
                    it.copy(
                        isProcessing = false,
                        showPreview = true,
                        parsedAccountNumber = parsedPattern.accountNumber,
                        parsedBankName = parsedPattern.bankName,
                        parsedSenders = parsedPattern.senderHints
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isProcessing = false,
                        errorMessage = "❌ Error: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun confirmAndSave(onSuccess: () -> Unit) {
        val state = _uiState.value
        
        _uiState.update { it.copy(isProcessing = true) }
        
        viewModelScope.launch {
            try {
                val parsedPattern = smsPatternParser.parseAccountFromSamples(
                    state.debitSms,
                    state.creditSms
                ) ?: return@launch
                
                // Create BankAccount object
                val bankAccount = BankAccount(
                    accountNumber = parsedPattern.accountNumber,
                    bankName = parsedPattern.bankName,
                    accountNickname = state.accountNickname.ifBlank { 
                        "${parsedPattern.bankName} (...${parsedPattern.accountNumber})" 
                    },
                    senderPatterns = parsedPattern.senderHints,
                    sampleDebitSms = state.debitSms,
                    sampleCreditSms = state.creditSms,
                    debitKeywords = parsedPattern.debitKeywords,
                    creditKeywords = parsedPattern.creditKeywords,
                    isActive = true
                )
                
                // Save to database
                bankAccountRepository.insertAccount(bankAccount)
                
                _uiState.update { 
                    it.copy(
                        isProcessing = false,
                        successMessage = "Account added successfully!"
                    )
                }
                
                // Call success callback after a short delay
                kotlinx.coroutines.delay(500)
                onSuccess()
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Failed to save: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    fun dismissPreview() {
        _uiState.update { it.copy(showPreview = false) }
    }
    
    fun clearForm() {
        _uiState.value = AccountSetupUiState()
    }
}

data class AccountSetupUiState(
    val debitSms: String = "",
    val creditSms: String = "",
    val accountNickname: String = "",
    val isProcessing: Boolean = false,
    val showPreview: Boolean = false,
    val parsedAccountNumber: String = "",
    val parsedBankName: String = "",
    val parsedSenders: List<String> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)
