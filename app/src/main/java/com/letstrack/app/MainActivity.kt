package com.letstrack.app

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.letstrack.app.sms.SmsPermissionHandler
import com.letstrack.app.ui.accounts.AccountsListScreen
import com.letstrack.app.ui.addexpense.AddExpenseScreen
import com.letstrack.app.ui.categories.CategoryManagementScreen
import com.letstrack.app.ui.expenses.ExpensesScreen
import com.letstrack.app.ui.home.HomeScreen
import com.letstrack.app.ui.imports.AddTransactionBottomSheet
import com.letstrack.app.ui.imports.CsvImportScreen
import com.letstrack.app.ui.imports.PdfImportScreen
import com.letstrack.app.ui.navigation.BottomNavItem
import com.letstrack.app.ui.placeholder.PlaceholderScreen
import com.letstrack.app.ui.profile.ProfileScreen
import com.letstrack.app.ui.settings.SettingsScreen
import com.letstrack.app.ui.sms.setup.AccountSetupScreen
import com.letstrack.app.ui.theme.LetsTrackTheme
import com.letstrack.app.ui.overlay.TransactionReviewOverlay
import com.letstrack.app.ui.TransactionReviewViewModel
import com.letstrack.app.service.TransactionReviewService
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

private const val PREFS_NAME = "app_prefs"
private const val KEY_FIRST_LAUNCH = "first_launch"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LetsTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val transactionReviewService: TransactionReviewService = hiltViewModel<TransactionReviewViewModel>().service
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showFirstLaunchPermissionDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val permissionHandler = remember { SmsPermissionHandler(context) }
    val overlayPermissionHandler = remember { com.letstrack.app.util.OverlayPermissionHandler(context) }

    // Check if first launch
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val isFirstLaunch = remember { prefs.getBoolean(KEY_FIRST_LAUNCH, true) }

    LaunchedEffect(isFirstLaunch) {
        if (isFirstLaunch && !permissionHandler.hasAllPermissions()) {
            showFirstLaunchPermissionDialog = true
        }
    }

    // SMS permission launcher
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }

        // Mark first launch as complete
        prefs.edit {
            putBoolean(KEY_FIRST_LAUNCH, false)
        }

        if (!allGranted) {
            showPermissionDialog = true
        } else {
            // After SMS permissions, check overlay permission
            if (overlayPermissionHandler.isOverlayPermissionRequired() &&
                !overlayPermissionHandler.canDrawOverlays()) {
                showOverlayPermissionDialog = true
            }
        }
    }

    // Function to request permissions
    val requestPermissions = {
        smsPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS
            )
        )
    }

    // Main UI Container
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                BottomNavigationBar(
                    navController = navController,
                    onAddClick = { showAddBottomSheet = true }
                )
            }
        ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen()
            }

            composable(BottomNavItem.Expenses.route) {
                ExpensesScreen(
                    onQuickAddClick = {
                        navController.navigate("manual_add/-1")
                    },
                    onExpenseClick = { expense ->
                        navController.navigate("manual_add/${expense.id}")
                    }
                )
            }

            composable("manual_add/{expenseId}") { backStackEntry ->
                val expenseId = backStackEntry.arguments?.getString("expenseId")?.toLongOrNull() ?: -1L
                AddExpenseScreen(
                    expenseId = expenseId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("pdf_import") {
                PdfImportScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("csv_import") {
                CsvImportScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("account_setup") {
                AccountSetupScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("accounts_list") {
                AccountsListScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToAddAccount = {
                        navController.navigate("account_setup")
                    },
                    onNavigateToEditAccount = { accountId ->
                        navController.navigate("edit_account/$accountId")
                    }
                )
            }

            composable("edit_account/{accountId}") { backStackEntry ->
                val accountId = backStackEntry.arguments?.getString("accountId")?.toLongOrNull() ?: 0L
                AccountSetupScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onRequestPermissions = requestPermissions
                )
            }

            composable("categories") {
                CategoryManagementScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(BottomNavItem.Placeholder.route) {
                PlaceholderScreen()
            }

            composable(BottomNavItem.Profile.route) {
                ProfileScreen(
                    onNavigateToAccounts = {
                        navController.navigate("accounts_list")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
                    onNavigateToCategories = {
                        navController.navigate("categories")
                    }
                )
            }
        }

        // Bottom Sheet for Add Options
        if (showAddBottomSheet) {
            AddTransactionBottomSheet(
                onDismiss = { showAddBottomSheet = false },
                onManualClick = {
                    navController.navigate("manual_add/-1")
                },
                onPdfClick = {
                    navController.navigate("pdf_import")
                },
                onCsvClick = {
                    navController.navigate("csv_import")
                }
            )
        }

        // SMS Permission Denied Dialog
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("SMS Permissions Required") },
                text = {
                    Text("To automatically track transactions from bank SMS, please grant READ_SMS and RECEIVE_SMS permissions in Settings.")
                },
                confirmButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // First Launch Permission Dialog
        if (showFirstLaunchPermissionDialog) {
            AlertDialog(
                onDismissRequest = {
                    showFirstLaunchPermissionDialog = false
                    prefs.edit { putBoolean(KEY_FIRST_LAUNCH, false) }
                },
                title = { Text("📱 Enable SMS Tracking?") },
                text = {
                    Text("Let's Track can automatically import transactions from your bank SMS messages.\n\nWe'll need permission to read SMS. You can change this anytime in Settings.")
                },
                confirmButton = {
                    Button(onClick = {
                        showFirstLaunchPermissionDialog = false
                        requestPermissions()
                    }) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showFirstLaunchPermissionDialog = false
                        prefs.edit { putBoolean(KEY_FIRST_LAUNCH, false) }
                    }) {
                        Text("Not Now")
                    }
                }
            )
        }

        // Overlay Permission Dialog
        if (showOverlayPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showOverlayPermissionDialog = false },
                title = { Text("🎯 Enable Transaction Overlay?") },
                text = {
                    Text("To show quick review popups when new transactions arrive, we need 'Display over other apps' permission.\n\nThis lets you categorize transactions instantly without opening the app.")
                },
                confirmButton = {
                    Button(onClick = {
                        showOverlayPermissionDialog = false
                        overlayPermissionHandler.requestOverlayPermission()
                    }) {
                        Text("Enable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showOverlayPermissionDialog = false
                    }) {
                        Text("Skip")
                    }
                }
            )
        }
    } // End of Scaffold

    // Global Transaction Review Overlay (AI Categorization) - OUTSIDE Scaffold
    val pendingTransaction by transactionReviewService.pendingTransaction.collectAsState()
    val isVisible by transactionReviewService.isOverlayVisible.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    TransactionReviewOverlay(
        pendingTransaction = pendingTransaction,
        isVisible = isVisible,
        onConfirm = { category, subCategory ->
            pendingTransaction?.let { transaction ->
                coroutineScope.launch {
                    transactionReviewService.confirmTransaction(
                        transaction = transaction,
                        selectedCategory = category,
                        selectedSubCategory = subCategory
                    )
                }
            }
            transactionReviewService.dismissReview()
        },
        onReject = {
            pendingTransaction?.let { transaction ->
                coroutineScope.launch {
                    transactionReviewService.rejectTransaction(transaction)
                }
            }
            transactionReviewService.dismissReview()
        },
        onReviewLater = {
            pendingTransaction?.let { transaction ->
                coroutineScope.launch {
                    transactionReviewService.rejectTransaction(transaction)
                }
            }
            transactionReviewService.dismissReview()
        },
        onDismiss = {
            transactionReviewService.dismissReview()
        }
    )
    } // End of Box
}

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    onAddClick: () -> Unit
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Expenses,
        BottomNavItem.AddExpense,
        BottomNavItem.Placeholder,
        BottomNavItem.Profile
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        items.forEach { item ->
            if (item == BottomNavItem.AddExpense) {
                // Special handling for Add button
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title
                        )
                    },
                    label = { Text(item.title) },
                    selected = false,
                    onClick = onAddClick
                )
            } else {
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title
                        )
                    },
                    label = { Text(item.title) },
                    selected = currentRoute == item.route,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}
