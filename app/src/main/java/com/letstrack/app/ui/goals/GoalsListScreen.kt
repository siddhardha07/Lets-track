package com.letstrack.app.ui.goals

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.letstrack.app.domain.goal.GoalProgress
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.components.EmptyState
import com.letstrack.app.ui.components.PrimaryButton
import com.letstrack.app.ui.components.SegmentedControl
import com.letstrack.app.ui.home.formatCurrency
import com.letstrack.app.ui.theme.Elevation
import com.letstrack.app.ui.theme.ShapeFull
import com.letstrack.app.ui.theme.Spacing
import java.io.File

private enum class GoalListTab { ACTIVE, ACHIEVED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsListScreen(
    onNavigateBack: () -> Unit,
    onAddGoal: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val goalProgress by viewModel.goalProgress.collectAsState()
    var tab by remember { mutableStateOf(GoalListTab.ACTIVE) }

    val visible = remember(goalProgress, tab) {
        goalProgress
            .filter { it.goal.isAchieved == (tab == GoalListTab.ACHIEVED) }
            .sortedByDescending { if (tab == GoalListTab.ACHIEVED) (it.goal.achievedAt ?: 0L).toDouble() else it.percent.toDouble() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saving Goals") },
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
                Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.lg)) {
                    PrimaryButton(text = "Add Goal", onClick = onAddGoal, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
                SegmentedControl(
                    options = listOf(GoalListTab.ACTIVE, GoalListTab.ACHIEVED),
                    selected = tab,
                    onSelect = { tab = it },
                    label = { if (it == GoalListTab.ACTIVE) "Active" else "Achieved" }
                )
            }

            if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = if (tab == GoalListTab.ACTIVE) "No active goals" else "Nothing achieved yet",
                        subtitle = if (tab == GoalListTab.ACTIVE) "Add a goal to start tracking it here." else "Fully-funded goals will show up here."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(visible, key = { it.goal.id }) { progress ->
                        GoalListRow(progress = progress, onClick = { onOpenGoal(progress.goal.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalListRow(progress: GoalProgress, onClick: () -> Unit) {
    val goal = progress.goal
    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        contentPadding = PaddingValues(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (goal.photoUri != null) {
                val model = if (goal.photoUri.startsWith("file://")) File(goal.photoUri.removePrefix("file://")) else goal.photoUri
                Image(
                    painter = rememberAsyncImagePainter(model),
                    contentDescription = goal.name,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(ShapeFull)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    "${formatCurrency(progress.savedAmount)} of ${formatCurrency(goal.targetAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (goal.isAchieved) {
                    Text("🎉 Achieved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    LinearProgressIndicator(
                        progress = { progress.percent.coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}
