package com.letstrack.app.ui.overlay

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letstrack.app.domain.model.PendingTransaction
import com.letstrack.app.ui.theme.contrastingOnColor
import com.letstrack.app.ui.theme.mixWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OverlayBase = Color(0xFF15151C)
private val OverlaySurfaceVariant = Color(0xFF2C2C2E)
private val OverlayOnSurface = Color(0xFFF2F2F7)
private val OverlayOnSurfaceMuted = Color(0xFF9A9AA0)
private val OverlayDebit = Color(0xFFFF6B5E)
private val OverlayCredit = Color(0xFF4CD97B)
private val OverlayWarning = Color(0xFFFFB454)

/** Opaque tinted-dark gradient (never translucent -- this card floats directly over whatever
 * app the user was in, so it can't let arbitrary content bleed through) that reads as "glassy"
 * from the color shift and the card's own shadow elevation rather than from real transparency. */
private fun overlayGradient(accentPrimary: Color): Brush =
    Brush.linearGradient(listOf(OverlayBase, OverlayBase.mixWith(accentPrimary, 0.4f)))

val defaultOverlayCategories = listOf(
    "Food", "Shopping", "Transportation", "Bills & Utilities",
    "Entertainment", "Health & Fitness", "Groceries", "Investments",
    "Income", "Other"
)

/**
 * Compact, non-modal review card shown by OverlayService above other apps.
 * Editing happens inline (category chips) - confirming never leaves the app the user was in.
 */
@Composable
fun SystemOverlayCard(
    transaction: PendingTransaction,
    accentPrimary: Color,
    availableCategories: List<String> = defaultOverlayCategories,
    onConfirm: (category: String, subCategory: String?, notes: String?) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    showSuccessMessage: Boolean = false,
    successMessage: String = "",
    onEditingChanged: (Boolean) -> Unit = {}
) {
    val onAccent = contrastingOnColor(accentPrimary)
    var selectedCategory by remember(transaction.expenseId) { mutableStateOf(transaction.suggestedCategory) }
    var subCategory by remember(transaction.expenseId) { mutableStateOf(transaction.suggestedSubCategory ?: "") }
    var notes by remember(transaction.expenseId) { mutableStateOf("") }
    var customCategories by remember(transaction.expenseId) { mutableStateOf(listOf<String>()) }
    var isAddingCategory by remember(transaction.expenseId) { mutableStateOf(false) }
    var newCategoryText by remember(transaction.expenseId) { mutableStateOf("") }
    val allCategories = remember(availableCategories, customCategories) { availableCategories + customCategories }

    val isCredit = transaction.transactionType.equals("CREDIT", ignoreCase = true)
    val amountColor = if (isCredit) OverlayCredit else OverlayDebit
    val amountPrefix = if (isCredit) "+" else "-"

    // Capped well under full-screen -- this floats over whatever the user was doing, so it
    // should read as a lightweight card, not something that takes over their screen.
    val maxCardHeight = LocalConfiguration.current.screenHeightDp.dp * 0.55f

    val cardShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxCardHeight)
            .shadow(elevation = 24.dp, shape = cardShape)
            .clip(cardShape)
            .background(overlayGradient(accentPrimary))
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(OverlayOnSurfaceMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header: merchant + time on the left, amount + close on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.merchantName,
                        color = OverlayOnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTime(transaction.date),
                        color = OverlayOnSurfaceMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$amountPrefix₹${formatAmount(transaction.amount)}",
                            color = amountColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = OverlayOnSurfaceMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            ConfidenceBadge(confidence = transaction.confidence)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CATEGORY",
                color = OverlayOnSurfaceMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allCategories) { category ->
                    CategoryChip(
                        label = category,
                        selected = category == selectedCategory,
                        accentPrimary = accentPrimary,
                        onAccent = onAccent,
                        onClick = { selectedCategory = category }
                    )
                }

                // Add new category inline - never leaves the overlay
                item {
                    AddCategoryChip(accentPrimary = accentPrimary, onClick = { isAddingCategory = true })
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
                        placeholder = { Text("New category name", fontSize = 12.sp, color = OverlayOnSurfaceMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = accentPrimary,
                            unfocusedBorderColor = OverlaySurfaceVariant,
                            focusedTextColor = OverlayOnSurface,
                            unfocusedTextColor = OverlayOnSurface,
                            cursorColor = accentPrimary,
                            focusedPlaceholderColor = OverlayOnSurfaceMuted.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = OverlayOnSurfaceMuted.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .onFocusChanged { onEditingChanged(it.isFocused) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val name = newCategoryText.trim()
                            if (name.isNotEmpty()) {
                                customCategories = customCategories + name
                                selectedCategory = name
                            }
                            newCategoryText = ""
                            isAddingCategory = false
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Add", tint = OverlayCredit)
                    }
                    IconButton(
                        onClick = {
                            newCategoryText = ""
                            isAddingCategory = false
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = OverlayOnSurfaceMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subcategory input
            OutlinedTextField(
                value = subCategory,
                onValueChange = { subCategory = it },
                label = { Text("Subcategory (optional)", fontSize = 12.sp, color = OverlayOnSurfaceMuted) },
                placeholder = { Text("e.g., Groceries, Lunch", fontSize = 12.sp, color = OverlayOnSurfaceMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = accentPrimary,
                    unfocusedBorderColor = OverlaySurfaceVariant,
                    focusedLabelColor = accentPrimary,
                    unfocusedLabelColor = OverlayOnSurfaceMuted,
                    focusedTextColor = OverlayOnSurface,
                    unfocusedTextColor = OverlayOnSurface,
                    cursorColor = accentPrimary,
                    focusedPlaceholderColor = OverlayOnSurfaceMuted.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = OverlayOnSurfaceMuted.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .onFocusChanged { onEditingChanged(it.isFocused) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Notes input
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)", fontSize = 12.sp, color = OverlayOnSurfaceMuted) },
                placeholder = { Text("Add details...", fontSize = 12.sp, color = OverlayOnSurfaceMuted) },
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = accentPrimary,
                    unfocusedBorderColor = OverlaySurfaceVariant,
                    focusedLabelColor = accentPrimary,
                    unfocusedLabelColor = OverlayOnSurfaceMuted,
                    focusedTextColor = OverlayOnSurface,
                    unfocusedTextColor = OverlayOnSurface,
                    cursorColor = accentPrimary,
                    focusedPlaceholderColor = OverlayOnSurfaceMuted.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = OverlayOnSurfaceMuted.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .onFocusChanged { onEditingChanged(it.isFocused) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSkip) {
                    Text("Skip", color = OverlayOnSurfaceMuted)
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        onConfirm(
                            selectedCategory,
                            subCategory.ifBlank { null },
                            notes.ifBlank { null }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentPrimary, contentColor = onAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("Confirm", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Success toast overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = showSuccessMessage,
            enter = androidx.compose.animation.slideInVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically() + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = OverlayCredit.copy(alpha = 0.9f),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Text(
                    text = successMessage,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Double) {
    val percent = (confidence * 100).toInt()
    val color = when {
        confidence >= 0.75 -> OverlayCredit
        confidence >= 0.6 -> OverlayWarning
        else -> OverlayDebit
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$percent% match",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    accentPrimary: Color,
    onAccent: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = if (selected) accentPrimary else OverlaySurfaceVariant,
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = if (selected) onAccent else OverlayOnSurface,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun AddCategoryChip(accentPrimary: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = OverlaySurfaceVariant,
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Category",
            tint = accentPrimary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "New",
            color = accentPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatAmount(amount: Double): String {
    return String.format(Locale.getDefault(), "%,.2f", amount)
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
}
