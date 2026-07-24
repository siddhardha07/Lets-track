package com.letstrack.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val title: String
) {
    object Home : BottomNavItem("home", Icons.Default.Home, "Home")
    object Expenses : BottomNavItem("expenses", Icons.Default.List, "Expenses")
    object AddExpense : BottomNavItem("add_expense", Icons.Default.Add, "Add")
    object Placeholder : BottomNavItem("placeholder", Icons.Default.Info, "More")
    object Profile : BottomNavItem("profile", Icons.Default.Person, "Profile")
}
