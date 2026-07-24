package com.letstrack.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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
    var showResultDialog by remember { mutableStateOf(false) }
    var selectedAccountForImport by remember { mutableStateOf<Long?>(null) }
    
    // Reset progress when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetProgress()
        }
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
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (accounts.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "🏦",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = "No Bank Accounts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add a bank account to start tracking SMS transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateToAddAccount) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Account")
                        }
                    }
                }
            } else {
                // List of accounts
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(accounts) { account ->
                        AccountCard(
                            account = account,
                            onEditClick = { onNavigateToEditAccount(account.id) },
                            onImportClick = {
                                selectedAccountForImport = account.id
                                showImportDialog = true
                            },
                            onDeleteClick = {
                                // TODO: Add delete confirmation dialog
                            }
                        )
                    }
                }
            }
        }
        
        // Import confirmation dialog
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import SMS Transactions?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This will import bank transaction SMS from the last 6 months.")
                        Text(
                            text = "• Reads all bank transaction SMS\n• Duplicates will be skipped\n• New transactions added to Expenses\n• Process may take a few minutes",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showImportDialog = false
                        viewModel.startBulkImport()
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
        
        // Import progress dialog - only show when actually importing (not Idle)
        if (importProgress != null && importProgress !is com.letstrack.app.sms.SmsImportService.ImportProgress.Idle) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Importing SMS Transactions") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (val progress = importProgress) {
                            is com.letstrack.app.sms.SmsImportService.ImportProgress.InProgress -> {
                                // Show preparing message if total is 0
                                if (progress.total == 0 && progress.phase == "fetching") {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(progress.message)
                                } else {
                                    // Phase indicators
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
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = if (progress.total > 0) 
                                        progress.current.toFloat() / progress.total.toFloat() 
                                    else 0f,
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
                                Text("❌ Error: ${progress.message}")
                            }
                            is com.letstrack.app.sms.SmsImportService.ImportProgress.Completed -> {
                                Text("✅ Import completed!")
                            }
                            is com.letstrack.app.sms.SmsImportService.ImportProgress.Idle,
                            null -> {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Preparing import...")
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
        
        // Import result dialog
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
                title = { Text("Import Complete!") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (val result = importResult) {
                            is com.letstrack.app.sms.SmsImportService.ImportResult.Success -> {
                                Text("✅ Successfully imported ${result.imported} transactions")
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
                                Text("❌ Import failed: ${result.error}")
                            }
                            null -> {
                                Text("No result available")
                            }
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                isCompleted -> MaterialTheme.colorScheme.primary
                isActive -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isCompleted -> MaterialTheme.colorScheme.primary
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bank name and nickname
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
                
                // Status badge
                Surface(
                    color = if (account.isActive) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (account.isActive) "Active" else "Inactive",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (account.isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                
                // Menu button
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import SMS") },
                            onClick = {
                                showMenu = false
                                onImportClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FileDownload, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEditClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
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
}
