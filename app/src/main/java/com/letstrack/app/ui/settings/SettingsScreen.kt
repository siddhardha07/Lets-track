package com.letstrack.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.letstrack.app.sms.SmsPermissionHandler
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.components.ConfirmationDialog
import com.letstrack.app.ui.components.PrimaryButton
import com.letstrack.app.ui.components.SecondaryButton
import com.letstrack.app.ui.components.SectionHeader
import com.letstrack.app.ui.components.SegmentedControl
import com.letstrack.app.ui.theme.AccentTheme
import com.letstrack.app.ui.theme.ShapeFull
import com.letstrack.app.ui.theme.Spacing
import com.letstrack.app.ui.theme.ThemeMode
import com.letstrack.app.ui.theme.contrastingOnColor
import com.letstrack.app.ui.theme.incomeColor
import com.letstrack.app.ui.theme.needsReviewColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onRequestPermissions: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accentTheme: AccentTheme,
    onAccentThemeChange: (AccentTheme) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val permissionHandler = remember { SmsPermissionHandler(context) }
    val overlayPermissionHandler = remember { com.letstrack.app.util.OverlayPermissionHandler(context) }
    val batteryOptimizationHandler = remember { com.letstrack.app.util.BatteryOptimizationHandler(context) }

    var permissionCheckTrigger by remember { mutableStateOf(0) }
    var delayedRecheckTrigger by remember { mutableStateOf(0) }
    val hasPermissions by remember {
        derivedStateOf { permissionCheckTrigger; delayedRecheckTrigger; permissionHandler.hasAllPermissions() }
    }
    val hasOverlayPermission by remember {
        derivedStateOf { permissionCheckTrigger; delayedRecheckTrigger; overlayPermissionHandler.canDrawOverlays() }
    }
    val isIgnoringBatteryOptimizations by remember {
        derivedStateOf { permissionCheckTrigger; delayedRecheckTrigger; batteryOptimizationHandler.isIgnoringBatteryOptimizations() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionCheckTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Some OEMs don't commit the battery-optimization exemption flag to PowerManager at the
    // exact moment the system Settings screen returns control, so the immediate ON_RESUME
    // check above can read a stale value -- re-check once more shortly after.
    LaunchedEffect(permissionCheckTrigger) {
        if (permissionCheckTrigger > 0) {
            delay(700)
            delayedRecheckTrigger++
        }
    }

    val backupState by viewModel.backupState.collectAsState()
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportTo(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    LaunchedEffect(backupState) {
        if (backupState is BackupState.ImportSuccessRestartRequired) {
            delay(1200)
            viewModel.restartApp()
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            item { SectionHeader("Appearance") }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    SegmentedControl(
                        options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                        selected = themeMode,
                        onSelect = onThemeModeChange,
                        label = { it.label() }
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        "Accent Color",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        AccentTheme.entries.forEach { accent ->
                            AccentSwatch(
                                accent = accent,
                                isSelected = accent == accentTheme,
                                onClick = { onAccentThemeChange(accent) }
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Data & Backup") }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Export a full backup of your data, or restore from a previous one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PrimaryButton(
                            text = "Export Data",
                            onClick = {
                                val filename = "letstrack_backup_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip"
                                exportLauncher.launch(filename)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Import Data",
                            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    when (val state = backupState) {
                        is BackupState.Working -> {
                            Spacer(Modifier.height(Spacing.md))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Working…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is BackupState.ExportSuccess -> {
                            Spacer(Modifier.height(Spacing.md))
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = incomeColor())
                        }
                        is BackupState.Failure -> {
                            Spacer(Modifier.height(Spacing.md))
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        is BackupState.ImportSuccessRestartRequired -> {
                            Spacer(Modifier.height(Spacing.md))
                            Text("Data restored. Restarting…", style = MaterialTheme.typography.bodySmall, color = incomeColor())
                        }
                        BackupState.Idle -> {}
                    }
                }
            }

            item { SectionHeader("More") }
            item {
                AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    SettingsNavRow(
                        icon = Icons.Filled.AccountBalance,
                        title = "Bank Accounts",
                        subtitle = "Manage linked accounts & SMS import",
                        onClick = onNavigateToAccounts
                    )
                    HorizontalDivider()
                    SettingsNavRow(
                        icon = Icons.Filled.Category,
                        title = "Categories",
                        subtitle = "Manage spending categories",
                        onClick = onNavigateToCategories
                    )
                }
            }

            item { SectionHeader("Permissions") }
            item {
                PermissionCard(
                    icon = Icons.Filled.Sms,
                    title = "SMS Access",
                    granted = hasPermissions,
                    grantedSubtitle = "Permissions granted",
                    missingSubtitle = "Required for automatic transaction tracking",
                    actionLabel = "Grant SMS Permissions",
                    onAction = onRequestPermissions
                ) {
                    Spacer(Modifier.height(Spacing.sm))
                    PermissionDetailRow("Read existing messages", permissionHandler.hasReadSmsPermission())
                    PermissionDetailRow("Receive new messages", permissionHandler.hasReceiveSmsPermission())
                }
            }
            item {
                PermissionCard(
                    icon = Icons.Filled.Layers,
                    title = "Display Over Other Apps",
                    granted = hasOverlayPermission,
                    grantedSubtitle = "Permission granted",
                    missingSubtitle = "Needed for the quick transaction-review popup",
                    actionLabel = "Enable Overlay Permission",
                    onAction = { overlayPermissionHandler.requestOverlayPermission() }
                )
            }
            item {
                PermissionCard(
                    icon = Icons.Filled.BatteryChargingFull,
                    title = "Background Reliability",
                    granted = isIgnoringBatteryOptimizations,
                    grantedSubtitle = "Exempt from battery optimization",
                    missingSubtitle = "Needed so background tracking isn't killed",
                    actionLabel = "Disable Battery Optimization",
                    onAction = { batteryOptimizationHandler.requestIgnoreBatteryOptimizations() }
                )
            }
        }
    }

    if (showImportConfirm) {
        ConfirmationDialog(
            title = "Restore from backup?",
            message = "This replaces all current data with the backup's contents and can't be undone. The app will restart.",
            confirmLabel = "Restore",
            onConfirm = {
                showImportConfirm = false
                pendingImportUri?.let { viewModel.importFrom(it) }
            },
            onDismiss = {
                showImportConfirm = false
                pendingImportUri = null
            }
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun AccentSwatch(accent: AccentTheme, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(ShapeFull)
                .background(Brush.verticalGradient(listOf(accent.coreAccent, accent.darkAccent)))
                .then(
                    if (isSelected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, ShapeFull)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = contrastingOnColor(accent.coreAccent),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = accent.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsNavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(ShapeFull)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    grantedSubtitle: String,
    missingSubtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    extraContent: @Composable () -> Unit = {}
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ShapeFull)
                    .background((if (granted) incomeColor() else needsReviewColor()).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (granted) incomeColor() else needsReviewColor(),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    text = if (granted) grantedSubtitle else missingSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Granted", tint = incomeColor())
            }
        }
        if (!granted) {
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
        extraContent()
    }
}

@Composable
private fun PermissionDetailRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (granted) "✓" else "✗",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) incomeColor() else MaterialTheme.colorScheme.error
        )
    }
}
