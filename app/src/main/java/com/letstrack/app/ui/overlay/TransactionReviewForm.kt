package com.letstrack.app.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.letstrack.app.domain.model.PendingTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val defaultOverlayCategories = listOf(
    "Food", "Shopping", "Transportation", "Bills & Utilities",
    "Entertainment", "Health & Fitness", "Groceries", "Investments",
    "Income", "Other"
)

/**
 * The one review form, used both by the in-app stack (TransactionReviewOverlay, inside a
 * ModalBottomSheet) and the system overlay (OverlayService, inside a bare Card floating over
 * other apps). These used to be two separately hand-built composables (SystemOverlayCard vs
 * TransactionReviewOverlay's private TransactionReviewContent) with a hardcoded dark palette on
 * one side and MaterialTheme colors on the other, duplicating the same amount/merchant/category/
 * confirm-skip logic twice. Now there's one, styled with MaterialTheme.colorScheme.* on both
 * sides - see OverlayCardTheme for why that's safe to do from a Service context too.
 */
@Composable
fun TransactionReviewForm(
    transaction: PendingTransaction,
    availableCategories: List<String> = defaultOverlayCategories,
    // > 1 shows the "N waiting for review" header + Clear all button - the in-app stack passes
    // the real backlog size; the system overlay never shows more than one card at a time (see
    // TransactionReviewService.systemOverlayTransaction's doc comment for why), so it just omits
    // these by leaving pendingCount at its default.
    pendingCount: Int = 1,
    onClearAll: () -> Unit = {},
    showSuccessMessage: Boolean = false,
    successMessage: String = "",
    onConfirm: (category: String, subCategory: String?, notes: String?) -> Unit,
    // Closing via the X (below) is now the one way to leave without confirming - it already
    // guarantees the expense is flagged needsReview (see TransactionReviewService.
    // skipCurrentToReview/dismissSystemOverlayCard), so a separate "Skip" button offered nothing
    // this didn't already do.
    onDismiss: () -> Unit,
    // For spam/misparsed SMS that were never a real transaction -- deletes the expense outright
    // instead of just flagging needsReview (see TransactionReviewService.deleteCurrentToReview).
    onDelete: () -> Unit,
    // The system overlay's window starts non-focusable (so touches pass through to the app
    // underneath) and needs to know when a text field is actually being edited to flip that -
    // see OverlayService. Not needed inside the app's own bottom sheet, hence the no-op default.
    onEditingChanged: (Boolean) -> Unit = {}
) {
    var amount by remember(transaction.expenseId) { mutableStateOf(transaction.amount.toString()) }
    var selectedCategory by remember(transaction.expenseId) { mutableStateOf(transaction.suggestedCategory) }
    var subCategory by remember(transaction.expenseId) { mutableStateOf(transaction.suggestedSubCategory ?: "") }
    var notes by remember(transaction.expenseId) { mutableStateOf("") }
    var customCategories by remember(transaction.expenseId) { mutableStateOf(listOf<String>()) }
    var isAddingCategory by remember(transaction.expenseId) { mutableStateOf(false) }
    var newCategoryText by remember(transaction.expenseId) { mutableStateOf("") }
    var isSearchingCategory by remember(transaction.expenseId) { mutableStateOf(false) }
    var categorySearchQuery by remember(transaction.expenseId) { mutableStateOf("") }
    val allCategories = remember(availableCategories, customCategories) { availableCategories + customCategories }
    val filteredCategories = remember(allCategories, categorySearchQuery) {
        if (categorySearchQuery.isBlank()) allCategories
        else allCategories.filter { it.contains(categorySearchQuery, ignoreCase = true) }
    }
    val categorySearchHasExactMatch = remember(allCategories, categorySearchQuery) {
        allCategories.any { it.equals(categorySearchQuery, ignoreCase = true) }
    }

    val isCredit = transaction.transactionType.equals("CREDIT", ignoreCase = true)
    val amountColor = if (isCredit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "New Transaction",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (pendingCount > 1) {
                        Text(
                            text = "1 of $pendingCount waiting for review",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (pendingCount > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear all $pendingCount to review")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ConfidenceBar(confidence = transaction.confidence)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                prefix = { Text(if (isCredit) "+₹" else "-₹") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = amountColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onEditingChanged(it.isFocused) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = transaction.merchantName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatTime(transaction.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isSearchingCategory) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = categorySearchQuery,
                        onValueChange = { categorySearchQuery = it },
                        placeholder = { Text("Search categories") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { onEditingChanged(it.isFocused) },
                        trailingIcon = {
                            IconButton(onClick = {
                                isSearchingCategory = false
                                categorySearchQuery = ""
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        }
                    )
                    // Only offered once there's a query with no existing category matching it -
                    // otherwise this would just be a confusing second way to select something
                    // already selectable as a chip below.
                    if (categorySearchQuery.isNotBlank() && !categorySearchHasExactMatch) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            val name = categorySearchQuery.trim()
                            customCategories = customCategories + name
                            selectedCategory = name
                            isSearchingCategory = false
                            categorySearchQuery = ""
                        }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Create \"${categorySearchQuery.trim()}\"",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isSearchingCategory) {
                    item {
                        IconButton(onClick = { isSearchingCategory = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search categories")
                        }
                    }
                }
                items(filteredCategories) { category ->
                    CategoryPickerChip(
                        label = category,
                        selected = category == selectedCategory,
                        onClick = { selectedCategory = category }
                    )
                }
                if (!isSearchingCategory) {
                    item {
                        AddCategoryChip(onClick = { isAddingCategory = true })
                    }
                }
            }

            if (isAddingCategory) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryText,
                        onValueChange = { newCategoryText = it },
                        placeholder = { Text("New category name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { onEditingChanged(it.isFocused) }
                    )
                    IconButton(onClick = {
                        val name = newCategoryText.trim()
                        if (name.isNotEmpty()) {
                            customCategories = customCategories + name
                            selectedCategory = name
                        }
                        newCategoryText = ""
                        isAddingCategory = false
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        newCategoryText = ""
                        isAddingCategory = false
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = subCategory,
                onValueChange = { subCategory = it },
                label = { Text("Subcategory (optional)") },
                placeholder = { Text("e.g., Groceries, Lunch") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onEditingChanged(it.isFocused) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Add details...") },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onEditingChanged(it.isFocused) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDelete,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        onConfirm(selectedCategory, subCategory.ifBlank { null }, notes.ifBlank { null })
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Confirm", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(
            visible = showSuccessMessage,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Text(
                    text = successMessage,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBar(confidence: Double) {
    val (color, label) = when {
        confidence >= 0.75 -> MaterialTheme.colorScheme.primary to "High Confidence"
        confidence >= 0.6 -> Color3(0xFFFFB454) to "Medium Confidence"
        else -> MaterialTheme.colorScheme.error to "Low Confidence - Please Review"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { confidence.toFloat() },
            modifier = Modifier.weight(1f).height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CategoryPickerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun AddCategoryChip(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Category",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "New", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))

// Small local helper so ConfidenceBar's medium-confidence color doesn't need a new import cycle.
private fun Color3(value: Long) = androidx.compose.ui.graphics.Color(value)
