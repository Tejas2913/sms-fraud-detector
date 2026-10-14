package com.smsfraud.detector.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAuditLog: () -> Unit = {},
    onNavigateToDeveloperMode: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setContactLookupEnabled(isGranted)
    }

    // Refresh background protection & notification state whenever the user returns from system Settings
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkBackgroundProtection(context)
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    // Show export snackbar when message is set
    LaunchedEffect(uiState.showExportMessage) {
        uiState.showExportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportMessage()
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all data?") },
            text = { Text("This will delete all messages, audit logs, and feedback. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRetentionDialog) {
        RetentionDialog(
            currentDays = uiState.auditLogRetentionDays,
            onDismiss = { showRetentionDialog = false },
            onSelect = { days ->
                viewModel.setAuditLogRetention(days)
                showRetentionDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // SMS Protection toggle (Scanner feature state)
            SettingsToggleRow(
                title = "SMS Protection",
                subtitle = if (uiState.isProtectionEnabled) "Active" else "Paused",
                checked = uiState.isProtectionEnabled,
                onCheckedChange = { viewModel.setProtectionEnabled(it) }
            )
            HorizontalDivider()

            // Guided Background Protection card (OEM autostart & battery settings)
            BackgroundProtectionCard(
                status = uiState.backgroundProtectionStatus,
                onOpenSettings = {
                    com.smsfraud.detector.util.BackgroundProtectionManager.openBackgroundSettings(context)
                    viewModel.onOpenedBackgroundSettings(context)
                },
                onCheckAgain = {
                    viewModel.checkBackgroundProtection(context)
                },
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()

            // Scan Scope selector (Product Spec v4 §7, Table 7)
            ScanScopeRow(
                currentScope = uiState.scanScope,
                onScopeChanged = { viewModel.setScanScope(it) }
            )
            HorizontalDivider()

            // Contact lookup toggle
            SettingsToggleRow(
                title = "Contact Lookup",
                subtitle = "Use contacts to reduce false positives",
                checked = uiState.isContactLookupEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.setContactLookupEnabled(true)
                        } else {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    } else {
                        viewModel.setContactLookupEnabled(false)
                    }
                }
            )
            HorizontalDivider()

            // Notification status & content (Product Spec v4 §7, Table 7)
            SettingsRow(
                title = "Notification Alerts",
                subtitle = if (notificationsEnabled)
                    "Active — notifications never preview message content"
                else
                    "Permission Required — Tap to enable notifications",
                onClick = if (!notificationsEnabled) {
                    {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    }
                } else null
            )
            HorizontalDivider()

            // Audit log retention (Product Spec v4 §7, Table 7)
            SettingsRow(
                title = "Audit Log Retention",
                subtitle = retentionLabel(uiState.auditLogRetentionDays),
                onClick = { showRetentionDialog = true }
            )
            HorizontalDivider()

            // Audit log viewer
            SettingsRow(
                title = "Audit Log",
                subtitle = "View decision history",
                onClick = onNavigateToAuditLog
            )
            HorizontalDivider()

            // Export audit log (Product Spec v4 §7, Table 7 — privacy-safe CSV share)
            SettingsRow(
                title = "Export Audit Log",
                subtitle = "Export decision history as CSV",
                onClick = { viewModel.exportAuditLog(context) }
            )
            HorizontalDivider()

            // Clear all data
            SettingsRow(
                title = "Clear All Data",
                subtitle = "Delete all messages, logs, and feedback",
                onClick = { showClearDialog = true }
            )
            HorizontalDivider()

            // Developer Mode (hidden until unlocked)
            if (isDeveloperMode) {
                SettingsRow(
                    title = "Developer Mode",
                    subtitle = "Raw engine scores and diagnostics",
                    onClick = onNavigateToDeveloperMode
                )
                HorizontalDivider()
            }

            // Version info (7-tap to unlock Developer Mode)
            SettingsRow(
                title = "Version",
                subtitle = "1.0.0",
                onClick = { viewModel.onVersionTap() }
            )
        }
    }
}

/**
 * Scan scope selector: All Messages vs Unknown Senders Only.
 * Product Spec v4 §7, Table 7: "Scan scope radio".
 */
@Composable
private fun ScanScopeRow(
    currentScope: String,
    onScopeChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(text = "Scan Scope", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Which messages to analyse",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentScope == "all",
                onClick = { onScopeChanged("all") },
                label = { Text("All Messages") }
            )
            FilterChip(
                selected = currentScope == "unknown_only",
                onClick = { onScopeChanged("unknown_only") },
                label = { Text("Unknown Senders") }
            )
        }
    }
}

/**
 * Audit log retention picker dialog.
 * Options: 7, 30, 90 days, or Forever (0).
 */
@Composable
private fun RetentionDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(7, 30, 90, 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audit Log Retention") },
        text = {
            Column {
                options.forEach { days ->
                    val label = retentionLabel(days)
                    val isSelected = days == currentDays
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(days) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSelected) "● $label" else "○ $label",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun retentionLabel(days: Int): String = when (days) {
    0 -> "Forever"
    else -> "$days days"
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
