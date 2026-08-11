package com.letstrack.app.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.ui.components.AccentDot
import com.letstrack.app.ui.components.AmountText
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.components.CategoryAvatar
import com.letstrack.app.ui.components.EmptyState
import com.letstrack.app.ui.home.formatDate
import com.letstrack.app.ui.home.signedAmount
import com.letstrack.app.ui.theme.Spacing
import com.letstrack.app.ui.theme.listItemCardBrush
import com.letstrack.app.ui.theme.needsReviewColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onExpenseClick: (Expense) -> Unit = {}
) {
    val transactions by viewModel.needsReviewTransactions.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Needs Review") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (transactions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyState(
                    title = "All caught up",
                    subtitle = "Transactions that need a category or amount double-checked will show up here."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    // Explicit action, not something that pops up on its own when the app opens
                    // (that used to happen automatically and was reported as intrusive) - tap
                    // this to go through everything needing review one at a time, with a
                    // "Clear all" option if you'd rather not.
                    Button(
                        onClick = { viewModel.reviewAllNow() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Review ${transactions.size} transaction${if (transactions.size == 1) "" else "s"} now")
                    }
                }
                items(transactions, key = { it.id }) { expense ->
                    NeedsReviewRow(
                        expense = expense,
                        category = viewModel.getCategoryById(expense.categoryId),
                        onClick = { onExpenseClick(expense) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NeedsReviewRow(
    expense: Expense,
    category: Category?,
    onClick: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        contentPadding = PaddingValues(Spacing.md),
        backgroundBrush = listItemCardBrush(MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f)
            ) {
                CategoryAvatar(category = category, size = 48.dp)
                Column {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        AccentDot(color = needsReviewColor(), size = 6.dp)
                        Text(
                            text = formatDate(expense.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            AmountText(amount = expense.signedAmount(), style = MaterialTheme.typography.titleMedium)
        }
    }
}
