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
    object Placeholder : BottomNavItem("placeholder", Icons.Default.Info, "More")
    object Settings : BottomNavItem("settings", Icons.Default.Settings, "Settings")
}
