package com.letstrack.app.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.model.Expense
import com.letstrack.app.ui.components.AmountText
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.components.CategoryAvatar
import com.letstrack.app.ui.components.CategoryFilterChip
import com.letstrack.app.ui.components.ConfirmationDialog
import com.letstrack.app.ui.components.DateRangePicker
import com.letstrack.app.ui.components.EmptyState
import com.letstrack.app.ui.components.HideableBalance
import com.letstrack.app.ui.components.MiniStat
import com.letstrack.app.ui.components.PrimaryButton
import com.letstrack.app.ui.components.SegmentedControl
import com.letstrack.app.ui.components.TertiaryButton
import com.letstrack.app.ui.home.formatCurrency
import com.letstrack.app.ui.home.signedAmount
import com.letstrack.app.ui.theme.expenseColor
import com.letstrack.app.ui.theme.heroCardBorderColor
import com.letstrack.app.ui.theme.heroCardBrush
import com.letstrack.app.ui.theme.incomeColor
import com.letstrack.app.ui.theme.isDarkTheme
import com.letstrack.app.ui.theme.listItemCardBrush
import com.letstrack.app.ui.theme.needsReviewColor
import com.letstrack.app.ui.theme.ShapeFull
import com.letstrack.app.ui.theme.ShapeMd
import com.letstrack.app.ui.theme.ShapeXs
import com.letstrack.app.ui.theme.Spacing
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onQuickAddClick: () -> Unit = {},
    onExpenseClick: (Expense) -> Unit = {},
    viewModel: ExpensesViewModel = hiltViewModel()
) {
    val expenses by viewModel.filteredExpenses.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val totalBalance by viewModel.totalBalance.collectAsState()
    val totalCredited by viewModel.totalCredited.collectAsState()
    val totalDebited by viewModel.totalDebited.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()
    val selectedAccountIds by viewModel.selectedAccountIds.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var expensePendingDelete by remember { mutableStateOf<Expense?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    val pullState = rememberPullToRefreshState()
    LaunchedEffect(pullState.isRefreshing) {
        if (pullState.isRefreshing) viewModel.refresh()
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && pullState.isRefreshing) pullState.endRefresh()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Hero balance card + search live outside the pull-to-refresh region on purpose --
            // pulling down is a gesture on the *transaction list*, and a refresh indicator
            // overlaid on top of the hero card's own controls (segmented filter, calendar/tune
            // icons) reads as broken rather than as a loading state.
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                ExpensesHeroCard(
                    dateFilter = dateFilter,
                    onDateFilterChange = { viewModel.onDateFilterChange(it) },
                    onOpenCustomRange = { showDatePicker = true },
                    totalBalance = totalBalance,
                    totalCredited = totalCredited,
                    totalDebited = totalDebited,
                    accounts = bankAccounts,
                    selectedAccountIds = selectedAccountIds,
                    onAccountToggle = viewModel::toggleAccountFilter,
                    onClearAccounts = viewModel::clearAccountFilter
                )
                SearchAndActionsRow(
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onQuickAddClick = onQuickAddClick,
                    onDeleteAllClick = { showDeleteAllConfirm = true }
                )
            }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                if (expenses.isEmpty()) {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            EmptyState(
                                title = if (searchQuery.isEmpty()) "No transactions yet" else "No matches for \"$searchQuery\"",
                                subtitle = if (searchQuery.isEmpty()) {
                                    "Transactions from SMS or manual entry will show up here."
                                } else {
                                    "Try a different search term."
                                }
                            )
                        }
                    }
                } else {
                    val grouped = expenses.sortedByDescending { it.date }.groupBy { dayGroupLabel(it.date) }
                    grouped.forEach { (label, transactions) ->
                        item(key = "header_$label") {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(transactions, key = { it.id }) { expense ->
                            TransactionListItem(
                                expense = expense,
                                category = viewModel.getCategoryById(expense.categoryId),
                                onClick = { onExpenseClick(expense) },
                                onRequestDelete = { expensePendingDelete = expense }
                            )
                        }
                    }
                }
            }

            if (isRefreshing) {
                PullToRefreshContainer(
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
        }
    }

    if (showDatePicker) {
        DateRangePicker(
            selectedRange = viewModel.customDateRange.collectAsState().value,
            onRangeSelected = { range ->
                viewModel.setCustomDateRange(range)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    expensePendingDelete?.let { expense ->
        ConfirmationDialog(
            title = "Delete transaction?",
            message = "\"${expense.title}\" will be permanently deleted.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteExpense(expense)
                expensePendingDelete = null
            },
            onDismiss = { expensePendingDelete = null }
        )
    }

    if (showDeleteAllConfirm) {
        ConfirmationDialog(
            title = "Delete all transactions?",
            message = "This permanently deletes every transaction and can't be undone.",
            confirmLabel = "Delete all",
            onConfirm = {
                viewModel.deleteAllExpenses()
                showDeleteAllConfirm = false
            },
            onDismiss = { showDeleteAllConfirm = false }
        )
    }
}

@Composable
private fun ExpensesHeroCard(
    dateFilter: DateFilter,
    onDateFilterChange: (DateFilter) -> Unit,
    onOpenCustomRange: () -> Unit,
    totalBalance: Double,
    totalCredited: Double,
    totalDebited: Double,
    accounts: List<com.letstrack.app.domain.model.BankAccount> = emptyList(),
    selectedAccountIds: Set<Long> = emptySet(),
    onAccountToggle: (Long) -> Unit = {},
    onClearAccounts: () -> Unit = {}
) {
    val quickFilters = listOf(DateFilter.ALL, DateFilter.YEAR, DateFilter.MONTH, DateFilter.DAY)
    val primary = MaterialTheme.colorScheme.primary
    var showAccountMenu by remember { mutableStateOf(false) }
    AppCard(
        backgroundBrush = heroCardBrush(primary),
        borderColor = heroCardBorderColor(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SegmentedControl(
                options = quickFilters,
                selected = if (dateFilter in quickFilters) dateFilter else DateFilter.ALL,
                onSelect = onDateFilterChange,
                label = { it.shortLabel() },
                modifier = Modifier.weight(1f)
            )
            if (accounts.isNotEmpty()) {
                IconButton(onClick = { showAccountMenu = true }) {
                    Icon(
                        Icons.Filled.AccountBalance,
                        contentDescription = "Filter by account",
                        tint = if (selectedAccountIds.isNotEmpty()) primary else LocalContentColor.current
                    )
                }
            }
            IconButton(onClick = onOpenCustomRange) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Custom date range")
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = "Current balance",
            style = MaterialTheme.typography.labelLarge,
            color = LocalContentColor.current.copy(alpha = 0.7f)
        )
        // Hidden by default, tap to reveal, auto-hides again a few seconds later -- see
        // HideableBalance. Used to be a manual show/hide toggle that defaulted to visible,
        // which is backwards for a number you generally want masked until you ask for it.
        HideableBalance(
            amount = totalBalance,
            style = MaterialTheme.typography.displaySmall,
            showIcon = false,
            positiveColor = primary
        )

        Spacer(Modifier.height(Spacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            MiniStat(label = "Credited", value = formatCurrency(totalCredited), color = incomeColor(), icon = Icons.Filled.ArrowUpward)
            MiniStat(label = "Debited", value = formatCurrency(totalDebited), color = expenseColor(), icon = Icons.Filled.ArrowDownward)
        }
    }

    if (showAccountMenu) {
        AccountFilterSheet(
            accounts = accounts,
            selectedAccountIds = selectedAccountIds,
            onAccountToggle = onAccountToggle,
            onClearAccounts = onClearAccounts,
            onDismiss = { showAccountMenu = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFilterSheet(
    accounts: List<com.letstrack.app.domain.model.BankAccount>,
    selectedAccountIds: Set<Long>,
    onAccountToggle: (Long) -> Unit,
    onClearAccounts: () -> Unit,
    onDismiss: () -> Unit
) {
    // Used to be a plain Material3 DropdownMenu with checkboxes - visually out of step with
    // the rest of the app's own filter sheets (Home's Filters sheet uses this exact
    // AppCard/CategoryFilterChip look for its own Accounts section). Matching that here instead.
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filter by account", style = MaterialTheme.typography.titleLarge)
                if (selectedAccountIds.isNotEmpty()) {
                    TertiaryButton(text = "Clear", onClick = onClearAccounts)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(accounts, key = { it.id }) { account ->
                    CategoryFilterChip(
                        label = account.accountNickname.ifBlank { account.bankName },
                        accent = MaterialTheme.colorScheme.primary,
                        selected = account.id in selectedAccountIds,
                        onClick = { onAccountToggle(account.id) }
                    )
                }
            }
            PrimaryButton(text = "Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun DateFilter.shortLabel(): String = when (this) {
    DateFilter.ALL -> "All"
    DateFilter.YEAR -> "Year"
    DateFilter.MONTH -> "Month"
    DateFilter.DAY -> "Today"
    DateFilter.CUSTOM -> "Custom"
}

@Composable
private fun SearchAndActionsRow(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onQuickAddClick: () -> Unit,
    onDeleteAllClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search by name, amount...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            singleLine = true,
            shape = ShapeFull
        )

        IconButton(
            onClick = onQuickAddClick,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary, ShapeFull)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add transaction", tint = MaterialTheme.colorScheme.onPrimary)
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Delete All Transactions") },
                    onClick = {
                        showMenu = false
                        onDeleteAllClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionListItem(
    expense: Expense,
    category: Category?,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val contentColor = if (isDarkTheme()) Color(0xFFF2F2F7) else Color(0xFF15151F)

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(listItemCardBrush(primary))
                .clickable(onClick = onClick)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryAvatar(category = category, size = 52.dp)
            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (expense.needsReview) {
                        Surface(shape = ShapeXs, color = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                text = "Review",
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Text(
                    text = categoryLine(category, expense.subCategory),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (category == null) needsReviewColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(horizontalAlignment = Alignment.End) {
                AmountText(amount = expense.signedAmount(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatTime(expense.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onRequestDelete()
                        }
                    )
                }
            }
        }
    }
}

private fun categoryLine(category: Category?, subCategory: String?): String {
    val categoryName = category?.name ?: "Uncategorized"
    return if (!subCategory.isNullOrBlank()) "$categoryName · $subCategory" else categoryName
}

private fun dayGroupLabel(dateMillis: Long): String {
    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateMillis), ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("MMM d"))
        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}

private fun formatTime(dateMillis: Long): String {
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateMillis), ZoneId.systemDefault())
    return dateTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
}
