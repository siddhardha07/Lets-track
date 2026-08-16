package com.letstrack.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.components.NavRow
import com.letstrack.app.ui.components.SectionHeader
import com.letstrack.app.ui.theme.Spacing

/**
 * The bottom-nav "More" tab -- used to be a bare "Coming Soon" placeholder. Budget and Saving
 * Goals both need a home outside Home's own graph cards (an always-reachable place to manage
 * them, see active/achieved goals, etc.), and this tab was otherwise empty, so it's the natural
 * spot rather than overloading Settings' own separate "More" section (that one's for Bank
 * Accounts/Categories management specifically).
 */
@Composable
fun MoreScreen(
    onOpenBudget: () -> Unit,
    onOpenGoals: () -> Unit
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            item {
                Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            item { SectionHeader("Budget") }
            item {
                AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    NavRow(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = "Monthly Budget",
                        subtitle = "Set overall & per-category limits",
                        onClick = onOpenBudget
                    )
                }
            }

            item { SectionHeader("Saving Goals") }
            item {
                AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    NavRow(
                        icon = Icons.Filled.Savings,
                        title = "Your Goals",
                        subtitle = "Active and achieved goals",
                        onClick = onOpenGoals
                    )
                }
            }
        }
    }
}
