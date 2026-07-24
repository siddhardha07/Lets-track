package com.letstrack.app.ui.sms.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccountSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Bank Account") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (!uiState.showPreview) {
                FloatingActionButton(
                    onClick = { 
                        viewModel.parseAndSaveAccount(onSuccess = {})
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Parse SMS")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📱 How to set up SMS tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Open your messaging app",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "2. Find a DEBIT transaction SMS from your bank",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "3. Copy and paste it below",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "4. Do the same for a CREDIT transaction SMS",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "💡 We'll learn your bank's SMS format from these samples",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Account Nickname (Optional)
            OutlinedTextField(
                value = uiState.accountNickname,
                onValueChange = { viewModel.onAccountNicknameChange(it) },
                label = { Text("Account Nickname (Optional)") },
                placeholder = { Text("e.g., Salary Account") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Debit SMS Input
            Text(
                text = "Debit Transaction SMS *",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = uiState.debitSms,
                onValueChange = { viewModel.onDebitSmsChange(it) },
                placeholder = { 
                    Text("Paste debit SMS here\n\nExample:\nYour A/c XX3937 debited by Rs. 172.72 on 16/07/26...") 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                minLines = 6,
                maxLines = 10
            )

            // Credit SMS Input
            Text(
                text = "Credit Transaction SMS *",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = uiState.creditSms,
                onValueChange = { viewModel.onCreditSmsChange(it) },
                placeholder = { 
                    Text("Paste credit SMS here\n\nExample:\nYour A/C XXXXX023937 is credited with INR 25.14...") 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                minLines = 6,
                maxLines = 10
            )

            // Error Message
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Success Message
            if (uiState.successMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = uiState.successMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Loading Indicator
            if (uiState.isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Preview Dialog
        if (uiState.showPreview) {
            PreviewDialog(
                accountNumber = uiState.parsedAccountNumber,
                bankName = uiState.parsedBankName,
                senders = uiState.parsedSenders,
                onConfirm = {
                    viewModel.confirmAndSave(onSuccess = onNavigateBack)
                },
                onDismiss = {
                    viewModel.dismissPreview()
                }
            )
        }
    }
}

@Composable
fun PreviewDialog(
    accountNumber: String,
    bankName: String,
    senders: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Confirm Account Details")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "We found these details from your SMS samples:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                DetailRow("Bank", bankName)
                DetailRow("Account Number (last digits)", accountNumber)
                DetailRow("SMS Senders", senders.joinToString(", "))
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "✓ Your account is ready to track transactions!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Save Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
