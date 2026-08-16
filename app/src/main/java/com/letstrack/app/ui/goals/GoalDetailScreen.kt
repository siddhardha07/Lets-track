package com.letstrack.app.ui.goals

import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.letstrack.app.domain.goal.GoalProgress
import com.letstrack.app.domain.model.GoalContribution
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.components.ConfirmationDialog
import com.letstrack.app.ui.components.PrimaryButton
import com.letstrack.app.ui.home.formatCurrency
import com.letstrack.app.ui.home.formatDate
import com.letstrack.app.ui.theme.Elevation
import com.letstrack.app.ui.theme.ShapeFull
import com.letstrack.app.ui.theme.Spacing
import com.letstrack.app.ui.theme.expenseColor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: Long,
    onNavigateBack: () -> Unit,
    onEditGoal: (Long) -> Unit,
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // goalProgress's StateFlow starts on an emptyList() seed before its first real DB read
    // arrives -- deciding "goal not found" off that directly bounced the user straight back out
    // the instant they opened this screen, every time, regardless of whether the goal existed.
    // This one-shot check runs first and gates whether "not found" is even a possibility yet.
    var initialCheckDone by remember { mutableStateOf(false) }
    var confirmedMissing by remember { mutableStateOf(false) }
    LaunchedEffect(goalId) {
        confirmedMissing = viewModel.loadInitialProgress(goalId) == null
        initialCheckDone = true
    }

    if (!initialCheckDone) return

    val goalProgressList by viewModel.goalProgress.collectAsState()
    val progress = goalProgressList.find { it.goal.id == goalId }
    val contributions by viewModel.getContributionsForGoal(goalId).collectAsState(initial = emptyList())

    var showAddMoneySheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (progress == null) {
        if (confirmedMissing) {
            // Genuinely gone (deleted from another tab while this screen was still open, or the
            // one-shot check above already confirmed it doesn't exist) -- the reactive StateFlow
            // just hasn't caught up yet in every other case, so only bounce back here.
            LaunchedEffect(Unit) { onNavigateBack() }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(progress.goal.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditGoal(goalId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = Elevation.level2) {
                Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.lg)) {
                    PrimaryButton(
                        text = "Add Money",
                        onClick = { showAddMoneySheet = true },
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
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            item { GoalDetailHeader(progress) }

            if (progress.goal.link != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, progress.goal.link.toUri())
                                context.startActivity(intent)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            progress.goal.link,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            if (progress.linkedAccountBalance != null) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                "Linked account balance (included automatically)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(formatCurrency(progress.linkedAccountBalance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Text("History", style = MaterialTheme.typography.titleSmall) }

            if (contributions.isEmpty()) {
                item {
                    Text(
                        "No contributions logged yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(contributions, key = { it.id }) { contribution ->
                    ContributionRow(contribution)
                }
            }
        }
    }

    if (showAddMoneySheet) {
        AddMoneySheet(
            onConfirm = { amount, note ->
                viewModel.addContribution(goalId, amount, note)
                showAddMoneySheet = false
            },
            onDismiss = { showAddMoneySheet = false }
        )
    }

    if (showDeleteConfirm) {
        ConfirmationDialog(
            title = "Delete this goal?",
            message = "\"${progress.goal.name}\" and its contribution history will be permanently removed.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteGoal(progress.goal, onSuccess = onNavigateBack)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun GoalDetailHeader(progress: GoalProgress) {
    val goal = progress.goal
    val isOver = progress.isFullyFunded
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (goal.photoUri != null) {
            val model = if (goal.photoUri.startsWith("file://")) File(goal.photoUri.removePrefix("file://")) else goal.photoUri
            Image(
                painter = rememberAsyncImagePainter(model),
                contentDescription = goal.name,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(
            "${formatCurrency(progress.savedAmount)} of ${formatCurrency(goal.targetAmount)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.sm))
        LinearProgressIndicator(
            progress = { progress.percent.coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(ShapeFull),
            color = if (isOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (goal.isAchieved) {
            Spacer(Modifier.height(Spacing.sm))
            Text("🎉 Achieved!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ContributionRow(contribution: GoalContribution) {
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(formatDate(contribution.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!contribution.note.isNullOrBlank()) {
                    Text(contribution.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                (if (contribution.amount >= 0) "+" else "") + formatCurrency(contribution.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (contribution.amount >= 0) MaterialTheme.colorScheme.primary else expenseColor()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMoneySheet(onConfirm: (Double, String?) -> Unit, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Text("Add money", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount") },
                prefix = { Text("₹") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            PrimaryButton(
                text = "Add",
                onClick = { if (amount != null) onConfirm(amount, note.ifBlank { null }) },
                enabled = amount != null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
