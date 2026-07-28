package com.letstrack.app.ui.addexpense

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.letstrack.app.domain.model.Category
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.components.CategoryAvatar
import com.letstrack.app.ui.components.PrimaryButton
import com.letstrack.app.ui.components.SectionHeader
import com.letstrack.app.ui.components.SegmentedControl
import com.letstrack.app.ui.components.TertiaryButton
import com.letstrack.app.ui.theme.Elevation
import com.letstrack.app.ui.theme.ShapeFull
import com.letstrack.app.ui.theme.ShapeSm
import com.letstrack.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    expenseId: Long = -1,
    onNavigateBack: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isEditMode = expenseId != -1L
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(expenseId) {
        if (expenseId != -1L) {
            viewModel.loadExpense(expenseId)
        }
    }

    val canSave = uiState.amount.toDoubleOrNull() != null &&
        uiState.title.isNotBlank() &&
        uiState.selectedCategory != null

    if (showNewCategoryDialog) {
        NewCategoryDialog(
            onDismiss = { showNewCategoryDialog = false },
            onCreate = { name, icon, color ->
                viewModel.createNewCategory(name = name, icon = icon, color = color)
                showNewCategoryDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Expense" else "Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = Elevation.level2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(Spacing.lg)
                ) {
                    PrimaryButton(
                        text = if (isEditMode) "Update Expense" else "Save Expense",
                        onClick = { viewModel.saveExpense(onSuccess = onNavigateBack) },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    SegmentedControl(
                        options = listOf("DEBIT", "CREDIT"),
                        selected = uiState.transactionType,
                        onSelect = viewModel::onTransactionTypeChange,
                        label = { if (it == "DEBIT") "Expense" else "Income" }
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    OutlinedTextField(
                        value = uiState.amount,
                        onValueChange = viewModel::onAmountChange,
                        label = { Text("Amount *") },
                        placeholder = { Text("0.00") },
                        prefix = { Text("₹", style = MaterialTheme.typography.titleMedium) },
                        textStyle = MaterialTheme.typography.headlineSmall,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    DateTimeField(
                        dateMillis = uiState.dateMillis,
                        onDateTimeChange = viewModel::onDateChange
                    )
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        OutlinedTextField(
                            value = uiState.title,
                            onValueChange = viewModel::onTitleChange,
                            label = { Text("Title *") },
                            placeholder = { Text("E.g., Lunch at Swiggy") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.subCategory,
                            onValueChange = viewModel::onSubCategoryChange,
                            label = { Text("Sub-Category") },
                            placeholder = { Text("E.g., Groceries, Takeout (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.description,
                            onValueChange = viewModel::onDescriptionChange,
                            label = { Text("Description") },
                            placeholder = { Text("Add more details (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = viewModel::onNotesChange,
                            label = { Text("Notes") },
                            placeholder = { Text("Private notes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }
            }

            item { SectionHeader("Category") }

            if (categories.isEmpty()) {
                item {
                    Text(
                        text = "Loading categories…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val gridItems: List<Category?> = categories + listOf(null)
                items(gridItems.chunked(3)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        row.forEach { category ->
                            Box(modifier = Modifier.weight(1f)) {
                                if (category == null) {
                                    AddCategoryTile(onClick = { showNewCategoryDialog = true })
                                } else {
                                    CategoryTile(
                                        category = category,
                                        isSelected = uiState.selectedCategory?.id == category.id,
                                        onClick = { viewModel.onCategorySelect(category) }
                                    )
                                }
                            }
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateTimeField(
    dateMillis: Long,
    onDateTimeChange: (Long) -> Unit
) {
    val context = LocalContext.current
    val formatted = remember(dateMillis) {
        SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.getDefault()).format(Date(dateMillis))
    }

    Surface(
        onClick = {
            val picked = Calendar.getInstance().apply { timeInMillis = dateMillis }
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    picked.set(year, month, dayOfMonth)
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            picked.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            picked.set(Calendar.MINUTE, minute)
                            onDateTimeChange(picked.timeInMillis)
                        },
                        picked.get(Calendar.HOUR_OF_DAY),
                        picked.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                picked.get(Calendar.YEAR),
                picked.get(Calendar.MONTH),
                picked.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        shape = ShapeSm,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Date & Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = formatted, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = "Change date and time",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box {
            CategoryAvatar(
                category = category,
                size = 76.dp,
                modifier = if (isSelected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, ShapeFull)
                } else {
                    Modifier
                }
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddCategoryTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add category",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = "New",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, icon: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("📁") }
    val color = "#4CAF50"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g., Travel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { if (it.length <= 2) icon = it },
                    label = { Text("Icon (emoji)") },
                    placeholder = { Text("🚗") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "This will create a basic category. You can add a custom image or color later in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Create",
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), icon.ifBlank { "📁" }, color) },
                enabled = name.isNotBlank()
            )
        },
        dismissButton = {
            TertiaryButton(text = "Cancel", onClick = onDismiss)
        }
    )
}
