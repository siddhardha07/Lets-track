package com.letstrack.app.ui.categories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.letstrack.app.domain.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CategoryManagementViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val enabledCategories by viewModel.enabledCategoryIds.collectAsState()
    var showCustomDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Categories") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCustomDialog = true },
                icon = { Icon(Icons.Default.Add, "Add") },
                text = { Text("Custom Category") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Select categories you want to use",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(categories) { category ->
                CategoryItem(
                    category = category,
                    isEnabled = enabledCategories.contains(category.id),
                    onToggle = { viewModel.toggleCategory(category.id) },
                    onEdit = { editingCategory = category },
                    onDelete = { viewModel.deleteCategory(category.id) }
                )
            }
        }
    }

    if (showCustomDialog) {
        CustomCategoryDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { name, icon, color, imageUri ->
                viewModel.createCustomCategory(name, icon, color, imageUri)
                showCustomDialog = false
            }
        )
    }

    editingCategory?.let { category ->
        CustomCategoryDialog(
            initialCategory = category,
            onDismiss = { editingCategory = null },
            onConfirm = { name, icon, color, imageUri ->
                viewModel.updateCategory(category.id, name, icon, color, imageUri)
                editingCategory = null
            }
        )
    }
}

@Composable
fun CategoryItem(
    category: Category,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon
                if (category.iconUri != null) {
                    // Custom image from gallery - convert file:// path to File
                    val imagePath = category.iconUri.removePrefix("file://")
                    Image(
                        painter = rememberAsyncImagePainter(java.io.File(imagePath)),
                        contentDescription = category.name,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(category.color))),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Emoji icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(category.color))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category.icon,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }

                // Category Name
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Show delete button for custom categories
                if (!category.isDefault) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                            contentDescription = "Delete ${category.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit ${category.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Checkmark if enabled
                if (isEnabled) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Enabled",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, color: String, imageUri: String?) -> Unit,
    initialCategory: Category? = null
) {
    val isEditing = initialCategory != null
    var categoryName by remember { mutableStateOf(initialCategory?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(initialCategory?.icon ?: "💰") }
    var selectedColor by remember { mutableStateOf(initialCategory?.color ?: "#4CAF50") }
    var customImageUri by remember {
        mutableStateOf(initialCategory?.iconUri?.let { Uri.parse(it) })
    }
    var showIconPicker by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        customImageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Category" else "Create Custom Category") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category Name
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Icon Section
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current Icon/Image Display
                    if (customImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(customImageUri),
                            contentDescription = "Custom Icon",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { showIconPicker = true },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(selectedColor)))
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { showIconPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedIcon,
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { showIconPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Choose Emoji")
                        }

                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, "Upload", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Image")
                        }
                    }
                }

                // Color Picker
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(predefinedColors) { color ->
                        ColorOption(
                            color = color,
                            isSelected = selectedColor == color,
                            onClick = { selectedColor = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onConfirm(
                            categoryName,
                            selectedIcon,
                            selectedColor,
                            customImageUri?.toString()
                        )
                    }
                },
                enabled = categoryName.isNotBlank()
            ) {
                Text(if (isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showIconPicker) {
        IconPickerDialog(
            onDismiss = { showIconPicker = false },
            onSelect = { icon ->
                selectedIcon = icon
                customImageUri = null // Clear custom image if emoji selected
                showIconPicker = false
            }
        )
    }
}

@Composable
fun ColorOption(
    color: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(android.graphics.Color.parseColor(color)))
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Icon") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(predefinedIcons) { icon ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { onSelect(icon) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = icon,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Predefined colors for category selection
private val predefinedColors = listOf(
    "#4CAF50", // Green
    "#2196F3", // Blue
    "#FF9800", // Orange
    "#F44336", // Red
    "#9C27B0", // Purple
    "#00BCD4", // Cyan
    "#FFEB3B", // Yellow
    "#795548", // Brown
    "#607D8B", // Blue Grey
    "#E91E63"  // Pink
)

// Predefined emoji icons for categories
private val predefinedIcons = listOf(
    "💰", "🛒", "🍔", "🚗", "🏠", "💊", "🎮", "📱", "✈️", "🎬",
    "🏋️", "📚", "👕", "⛽", "💡", "🍕", "☕", "🎵", "🏦", "💳",
    "🎁", "🌮", "🍜", "🚌", "🚕", "🏥", "💻", "📺", "🎧", "🎸",
    "⚽", "🏀", "🎾", "🏊", "🚲", "🛍️", "🎨", "📷", "✈️", "🏨",
    "🍺", "🍷", "🎂", "🍰", "🌹", "💐", "🎈", "🎉", "💼", "📊"
)
