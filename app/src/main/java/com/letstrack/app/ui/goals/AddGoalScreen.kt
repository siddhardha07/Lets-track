package com.letstrack.app.ui.goals

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.letstrack.app.ui.components.CategoryFilterChip
import com.letstrack.app.ui.components.PrimaryButton
import com.letstrack.app.ui.theme.Elevation
import com.letstrack.app.ui.theme.Spacing
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalScreen(
    goalId: Long = -1,
    onNavigateBack: () -> Unit,
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val isEditMode = goalId != -1L
    val formState by viewModel.formState.collectAsState()
    val bankAccounts by viewModel.bankAccounts.collectAsState()

    LaunchedEffect(goalId) {
        if (isEditMode) viewModel.loadGoalForEdit(goalId) else viewModel.resetForm()
    }

    // Pick + crop in one flow (the library's own picker, then its own crop UI) rather than a
    // bare GetContent -- "user should be able to crop" per feedback, and the cropped result is
    // what actually gets saved as the goal's photo.
    val cropImageLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { viewModel.onPhotoPicked(it.toString()) }
        }
    }

    val canSave = formState.name.isNotBlank() && formState.targetAmount.toDoubleOrNull() != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Goal" else "Add Saving Goal") },
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
                        text = if (isEditMode) "Save Changes" else "Create Goal",
                        onClick = {
                            if (isEditMode) {
                                viewModel.updateGoal(goalId, onSuccess = onNavigateBack)
                            } else {
                                viewModel.saveNewGoal(onSuccess = onNavigateBack)
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GoalPhotoPicker(
                        photoUri = formState.photoUri,
                        onClick = {
                            cropImageLauncher.launch(
                                CropImageContractOptions(
                                    uri = null,
                                    cropImageOptions = CropImageOptions(
                                        imageSourceIncludeGallery = true,
                                        imageSourceIncludeCamera = true,
                                        fixAspectRatio = false
                                    )
                                )
                            )
                        }
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = formState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("What are you saving for? *") },
                    placeholder = { Text("E.g., New bicycle") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = formState.targetAmount,
                    onValueChange = viewModel::onTargetAmountChange,
                    label = { Text("Price *") },
                    placeholder = { Text("0.00") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (!isEditMode) {
                item {
                    OutlinedTextField(
                        value = formState.alreadySaved,
                        onValueChange = viewModel::onAlreadySavedChange,
                        label = { Text("Already saved (optional)") },
                        placeholder = { Text("0.00") },
                        prefix = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = formState.link,
                    onValueChange = viewModel::onLinkChange,
                    label = { Text("Link (optional)") },
                    placeholder = { Text("Where to buy it") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (bankAccounts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            "Track from a bank account (optional)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Shows that account's balance as a reference -- you can still add or edit savings manually either way.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            item {
                                CategoryFilterChip(
                                    label = "None",
                                    accent = MaterialTheme.colorScheme.primary,
                                    selected = formState.linkedAccountId == null,
                                    onClick = { viewModel.onLinkedAccountChange(null) }
                                )
                            }
                            items(bankAccounts, key = { it.id }) { account ->
                                CategoryFilterChip(
                                    label = account.accountNickname.ifBlank { account.bankName },
                                    accent = MaterialTheme.colorScheme.primary,
                                    selected = formState.linkedAccountId == account.id,
                                    onClick = { viewModel.onLinkedAccountChange(account.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalPhotoPicker(photoUri: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (photoUri != null) {
            // Freshly picked (not yet persisted) photos are content:// Uris; once saved and
            // reloaded for edit they're file:// paths -- same distinction CategoryAvatar makes.
            val model = if (photoUri.startsWith("file://")) File(photoUri.removePrefix("file://")) else android.net.Uri.parse(photoUri)
            Image(
                painter = rememberAsyncImagePainter(model),
                contentDescription = "Goal photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Filled.AddAPhoto,
                contentDescription = "Add photo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
