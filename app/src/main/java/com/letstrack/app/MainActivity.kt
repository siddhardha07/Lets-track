package com.letstrack.app

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.letstrack.app.ui.imports.AddActionMenu
import com.letstrack.app.ui.imports.CsvImportScreen
import com.letstrack.app.ui.imports.PdfImportScreen
import com.letstrack.app.ui.navigation.BottomNavItem
import com.letstrack.app.ui.notifications.NotificationsScreen
import com.letstrack.app.ui.placeholder.PlaceholderScreen
import com.letstrack.app.ui.settings.SettingsScreen
import com.letstrack.app.ui.sms.setup.AccountSetupScreen
import com.letstrack.app.ui.theme.AccentTheme
import com.letstrack.app.ui.theme.LetsTrackTheme
import com.letstrack.app.ui.theme.ThemeMode
import com.letstrack.app.ui.theme.accentGradient
import com.letstrack.app.ui.theme.ThemeViewModel
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
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val accentTheme by themeViewModel.accentTheme.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LetsTrackTheme(darkTheme = darkTheme, accentTheme = accentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        themeMode = themeMode,
                        onThemeModeChange = themeViewModel::setThemeMode,
                        accentTheme = accentTheme,
                        onAccentThemeChange = themeViewModel::setAccentTheme
                    )
                }
            }
        }
    }
}

@Composable
fun MainNavigation(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accentTheme: AccentTheme,
    onAccentThemeChange: (AccentTheme) -> Unit
) {
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
                HomeScreen(
                    onSeeAllTransactions = {
                        navController.navigate(BottomNavItem.Expenses.route)
                    },
                    onOpenNotifications = {
                        navController.navigate("notifications")
                    }
                )
            }

            composable("notifications") {
                NotificationsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onExpenseClick = { expense ->
                        navController.navigate("manual_add/${expense.id}")
                    }
                )
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

            composable(BottomNavItem.Settings.route) {
                SettingsScreen(
                    onRequestPermissions = requestPermissions,
                    onNavigateToAccounts = {
                        navController.navigate("accounts_list")
                    },
                    onNavigateToCategories = {
                        navController.navigate("categories")
                    },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    accentTheme = accentTheme,
                    onAccentThemeChange = onAccentThemeChange
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

    // Add Action Menu -- drawn after (on top of) the Scaffold/custom bottom bar, so the scrim
    // covers the whole screen instead of sitting behind the floating nav bar.
    if (showAddBottomSheet) {
        AddActionMenu(
            onDismiss = { showAddBottomSheet = false },
            onManualClick = {
                showAddBottomSheet = false
                navController.navigate("manual_add/-1")
            },
            onPdfClick = {
                showAddBottomSheet = false
                navController.navigate("pdf_import")
            },
            onCsvClick = {
                showAddBottomSheet = false
                navController.navigate("csv_import")
            }
        )
    }

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
        BottomNavItem.Placeholder,
        BottomNavItem.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun navigate(item: BottomNavItem) {
        navController.navigate(item.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.take(2).forEach { item ->
                    BottomNavButton(
                        item = item,
                        selected = currentRoute == item.route,
                        modifier = Modifier.weight(1f),
                        onClick = { navigate(item) }
                    )
                }
                Spacer(modifier = Modifier.width(72.dp))
                items.drop(2).forEach { item ->
                    BottomNavButton(
                        item = item,
                        selected = currentRoute == item.route,
                        modifier = Modifier.weight(1f),
                        onClick = { navigate(item) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-28).dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(accentGradient(MaterialTheme.colorScheme.primary))
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add transaction",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun BottomNavButton(
    item: BottomNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = item.icon, contentDescription = item.title, tint = color)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = item.title, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
