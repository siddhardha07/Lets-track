package com.letstrack.app.ui.sms.setup

import androidx.lifecycle.SavedStateHandle
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
    private val bankAccountRepository: BankAccountRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSetupUiState())
    val uiState: StateFlow<AccountSetupUiState> = _uiState.asStateFlow()

    private val smsPatternParser = SmsPatternParser()

    private val accountId: Long? = savedStateHandle.get<String>("accountId")?.toLongOrNull()

    init {
        // If editing an existing account, load its data
        accountId?.let { id ->
            if (id > 0) {
                loadAccountForEditing(id)
            }
        }
    }

    /**
     * Load existing account data for editing
     */
    private fun loadAccountForEditing(id: Long) {
        viewModelScope.launch {
            try {
                val account = bankAccountRepository.getAccountById(id)
                if (account != null) {
                    _uiState.update {
                        it.copy(
                            accountId = id,
                            isEditMode = true,
                            accountNickname = account.accountNickname,
                            debitSms = account.sampleDebitSms,
                            creditSms = account.sampleCreditSms,
                            parsedAccountNumber = account.accountNumber,
                            parsedBankName = account.bankName,
                            parsedSenders = account.senderPatterns
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Failed to load account: ${e.message}")
                }
            }
        }
    }

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

                if (state.isEditMode && state.accountId != null) {
                    // Update existing account
                    val bankAccount = BankAccount(
                        id = state.accountId,
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

                    bankAccountRepository.updateAccount(bankAccount)

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            successMessage = "Account updated successfully!"
                        )
                    }
                } else {
                    // Create new account
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
    val accountId: Long? = null,
    val isEditMode: Boolean = false,
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
