package com.letstrack.app.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.letstrack.app.domain.model.PendingTransaction

/**
 * Global transaction review overlay - 50% bottom sheet
 * Shows for new transactions requiring user confirmation/review
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReviewOverlay(
    pendingTransaction: PendingTransaction?,
    isVisible: Boolean,
    availableCategories: List<String> = listOf(
        "Food", "Shopping", "Transportation", "Bills & Utilities",
        "Entertainment", "Health & Fitness", "Groceries", "Investments",
        "Income", "Other"
    ),
    onConfirm: (String, String?) -> Unit,
    onReject: () -> Unit,
    onReviewLater: () -> Unit,
    onDismiss: () -> Unit
) {
    android.util.Log.d("TransactionReviewOverlay", "🎨 Composing overlay - isVisible: $isVisible, transaction: ${pendingTransaction?.merchantName}")

    if (!isVisible || pendingTransaction == null) {
        android.util.Log.d("TransactionReviewOverlay", "⚠️ NOT showing overlay - isVisible: $isVisible, hasTransaction: ${pendingTransaction != null}")
        return
    }

    android.util.Log.d("TransactionReviewOverlay", "✅ SHOWING ModalBottomSheet for: ${pendingTransaction.merchantName}")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        modifier = Modifier.fillMaxHeight(0.6f)
    ) {
        TransactionReviewContent(
            transaction = pendingTransaction,
            availableCategories = availableCategories,
            onConfirm = onConfirm,
            onReject = onReject,
            onReviewLater = onReviewLater,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun TransactionReviewContent(
    transaction: PendingTransaction,
    availableCategories: List<String>,
    onConfirm: (String, String?) -> Unit,
    onReject: () -> Unit,
    onReviewLater: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(transaction.suggestedCategory) }
    var selectedSubCategory by remember { mutableStateOf(transaction.suggestedSubCategory ?: "") }
    var amount by remember { mutableStateOf(transaction.amount.toString()) }
    var notes by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf(false) }

    // Category Picker Dialog
    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Select Category") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableCategories.forEach { category ->
                        Surface(
                            onClick = {
                                selectedCategory = category
                                showCategoryPicker = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (category == selectedCategory)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = category,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header with X button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "New Transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Confidence indicator
        ConfidenceIndicator(confidence = transaction.confidence)

        Spacer(modifier = Modifier.height(16.dp))

        // Transaction details (editable)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            prefix = { Text("₹") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = transaction.merchantName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )

        if (transaction.fullSmsMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = transaction.fullSmsMessage.take(100) + "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category selection
        Text(
            text = "Category:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        CategoryChip(
            category = selectedCategory,
            subCategory = selectedSubCategory.ifBlank { null },
            onClick = { showCategoryPicker = true }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Subcategory input
        OutlinedTextField(
            value = selectedSubCategory,
            onValueChange = { selectedSubCategory = it },
            label = { Text("Subcategory (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g., Groceries, Lunch, etc.") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Notes input
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            placeholder = { Text("Add any additional details...") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons based on confidence
        if (transaction.confidence >= 0.6) {
            // High/Medium confidence: Show Yes/No
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text("No", color = MaterialTheme.colorScheme.onErrorContainer)
                }

                Button(
                    onClick = { onConfirm(selectedCategory, selectedSubCategory) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Yes, Save")
                }
            }
        } else {
            // Low confidence: Just save button
            Button(
                onClick = { onConfirm(selectedCategory, selectedSubCategory) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Transaction")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Review later button
        TextButton(
            onClick = onReviewLater,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Review Later")
        }
    }
}

@Composable
private fun ConfidenceIndicator(confidence: Double) {
    val (color, label) = when {
        confidence >= 0.9 -> Pair(Color(0xFF4CAF50), "High Confidence")
        confidence >= 0.6 -> Pair(Color(0xFFFFC107), "Medium Confidence")
        else -> Pair(Color(0xFFF44336), "Low Confidence - Please Review")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { confidence.toFloat() },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CategoryChip(
    category: String,
    subCategory: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (subCategory != null) {
                    Text(
                        text = subCategory,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                text = "Change",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
