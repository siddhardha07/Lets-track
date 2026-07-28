package com.letstrack.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.components.ConfirmationDialog
import com.letstrack.app.ui.components.DateRange
import com.letstrack.app.ui.components.DateRangePicker
import com.letstrack.app.ui.components.EmptyState
import com.letstrack.app.ui.components.PrimaryButton
import com.letstrack.app.ui.theme.Spacing
import com.letstrack.app.ui.theme.incomeColor
import com.letstrack.app.ui.theme.needsReviewColor
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToEditAccount: (Long) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var importRange by remember { mutableStateOf(defaultImportRange()) }
    var accountPendingDelete by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetProgress() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bank Accounts") },
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
            FloatingActionButton(
                onClick = onNavigateToAddAccount,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Account", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (accounts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            title = "No bank accounts yet",
                            subtitle = "Add a bank account to start tracking SMS transactions."
                        )
                        Spacer(Modifier.height(Spacing.md))
                        PrimaryButton(text = "Add Account", onClick = onNavigateToAddAccount)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            onEditClick = { onNavigateToEditAccount(account.id) },
                            onImportClick = {
                                importRange = defaultImportRange()
                                showImportDialog = true
                            },
                            onDeleteClick = { accountPendingDelete = account.id }
                        )
                    }
                }
            }
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import SMS Transactions?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("This will import bank transaction SMS from ${importRange.format()}.")
                        TextButton(onClick = {
                            // Hide the confirm dialog while the picker is up so there's never
                            // two stacked dialogs at once -- confusing which "Apply"/"Import"
                            // belongs to which, and easy to tap the wrong one.
                            showImportDialog = false
                            showRangePicker = true
                        }) {
                            Text("Change date range")
                        }
                        Text(
                            text = "• Reads all bank transaction SMS\n• Duplicates will be skipped\n• New transactions added to Expenses\n• Process may take a few minutes",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showImportDialog = false
                        viewModel.startBulkImport(importRange.startDate, importRange.endDate)
                    }) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRangePicker) {
            DateRangePicker(
                selectedRange = importRange,
                onRangeSelected = {
                    importRange = it
                    showRangePicker = false
                    showImportDialog = true
                },
                onDismiss = {
                    showRangePicker = false
                    showImportDialog = true
                }
            )
        }

        if (importProgress != null && importProgress !is com.letstrack.app.sms.SmsImportService.ImportProgress.Idle) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Importing SMS Transactions") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        when (val progress = importProgress) {
                            is com.letstrack.app.sms.SmsImportService.ImportProgress.InProgress -> {
                                if (progress.total == 0 && progress.phase == "fetching") {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(Spacing.sm))
                                    Text(progress.message)
                                } else {
                                    ProgressPhaseRow(
                                        icon = if (progress.phase == "parsing" || progress.phase == "saving") "✓" else "↻",
                                        label = "Reading messages",
                                        isActive = progress.phase == "fetching",
                                        isCompleted = progress.phase in listOf("parsing", "saving")
                                    )
                                    ProgressPhaseRow(
                                        icon = if (progress.phase == "parsing" || progress.phase == "saving") "✓" else if (progress.phase == "fetching") "⋯" else "↻",
                                        label = "Parsing transactions",
                                        isActive = progress.phase == "parsing",
                                        isCompleted = progress.phase == "saving"
                                    )
                                    ProgressPhaseRow(
                                        icon = if (progress.phase == "saving") "↻" else "⋯",
                                        label = "Adding to expenses",
                                        isActive = progress.phase == "saving",
                                        isCompleted = false
                                    )

                                    Spacer(modifier = Modifier.height(Spacing.sm))
                                    LinearProgressIndicator(
                                        progress = {
                                            if (progress.total > 0) progress.current.toFloat() / progress.total.toFloat() else 0f
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "${progress.current} / ${progress.total} messages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is com.letstrack.app.sms.SmsImportService.ImportProgress.Error -> {
                                Text("Error: ${progress.message}", color = MaterialTheme.colorScheme.error)
                            }
                            is com.letstrack.app.sms.SmsImportService.ImportProgress.Completed -> {
                                Text("Import completed!", color = incomeColor())
                            }
                            is com.letstrack.app.sms.SmsImportService.ImportProgress.Idle, null -> {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text("Preparing import...")
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (importResult != null) {
            LaunchedEffect(importResult) {
                showResultDialog = true
            }
        }

        if (showResultDialog && importResult != null) {
            AlertDialog(
                onDismissRequest = {
                    showResultDialog = false
                    viewModel.clearImportResult()
                },
                title = { Text("Import Complete") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        when (val result = importResult) {
                            is com.letstrack.app.sms.SmsImportService.ImportResult.Success -> {
                                Text("Successfully imported ${result.imported} transactions", color = incomeColor())
                                Text(
                                    "Processed ${result.processed} SMS messages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (result.imported < result.processed) {
                                    Text(
                                        "${result.processed - result.imported} were duplicates or already imported",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is com.letstrack.app.sms.SmsImportService.ImportResult.Failure -> {
                                Text("Import failed: ${result.error}", color = MaterialTheme.colorScheme.error)
                            }
                            null -> Text("No result available")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showResultDialog = false
                        viewModel.clearImportResult()
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }

    accountPendingDelete?.let { accountId ->
        ConfirmationDialog(
            title = "Delete this account?",
            message = "This removes the account and its SMS-parsing setup. Existing transactions are kept.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteAccount(accountId)
                accountPendingDelete = null
            },
            onDismiss = { accountPendingDelete = null }
        )
    }
}

private fun defaultImportRange(): DateRange {
    val calendar = Calendar.getInstance()
    val end = calendar.timeInMillis
    calendar.add(Calendar.MONTH, -6)
    val start = calendar.timeInMillis
    return DateRange(start, end)
}

@Composable
fun ProgressPhaseRow(
    icon: String,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                isCompleted -> incomeColor()
                isActive -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isCompleted -> incomeColor()
                isActive -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun AccountCard(
    account: com.letstrack.app.data.local.entity.BankAccountEntity,
    onEditClick: () -> Unit,
    onImportClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.bankName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (account.accountNickname.isNotBlank()) {
                    Text(
                        text = account.accountNickname,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = (if (account.isActive) incomeColor() else needsReviewColor()).copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (account.isActive) "Active" else "Inactive",
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (account.isActive) incomeColor() else needsReviewColor()
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Import SMS") },
                        onClick = {
                            showMenu = false
                            onImportClick()
                        },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEditClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}
