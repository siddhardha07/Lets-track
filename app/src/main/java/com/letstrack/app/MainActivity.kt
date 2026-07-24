package com.letstrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.letstrack.app.ui.addexpense.AddExpenseScreen
import com.letstrack.app.ui.expenses.ExpensesScreen
import com.letstrack.app.ui.home.HomeScreen
import com.letstrack.app.ui.imports.AddTransactionBottomSheet
import com.letstrack.app.ui.imports.CsvImportScreen
import com.letstrack.app.ui.imports.PdfImportScreen
import com.letstrack.app.ui.navigation.BottomNavItem
import com.letstrack.app.ui.placeholder.PlaceholderScreen
import com.letstrack.app.ui.profile.ProfileScreen
import com.letstrack.app.ui.theme.LetsTrackTheme
import dagger.hilt.android.AndroidEntryPoint

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
    var showAddBottomSheet by remember { mutableStateOf(false) }

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

            composable(BottomNavItem.Placeholder.route) {
                PlaceholderScreen()
            }

            composable(BottomNavItem.Profile.route) {
                ProfileScreen()
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
    }
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
