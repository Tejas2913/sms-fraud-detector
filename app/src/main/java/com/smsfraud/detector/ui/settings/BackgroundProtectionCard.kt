package com.smsfraud.detector.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smsfraud.detector.ui.theme.EscalateAmber
import com.smsfraud.detector.ui.theme.PassGreen
import com.smsfraud.detector.util.BackgroundProtectionManager
import com.smsfraud.detector.util.BackgroundProtectionStatus

/**
 * Composable card for guided OEM Background Protection setup.
 * Handles verified states (stock Android) and unverifiable states (Xiaomi/MIUI/HyperOS).
 */
@Composable
fun BackgroundProtectionCard(
    status: BackgroundProtectionStatus,
    onOpenSettings: () -> Unit,
    onCheckAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUnverifiable = status == BackgroundProtectionStatus.UNVERIFIABLE || status == BackgroundProtectionStatus.SETTINGS_OPENED_UNVERIFIABLE

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Background Protection",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                val badgeText = when (status) {
                    BackgroundProtectionStatus.VERIFIED_CONFIGURED -> "✓ Configured"
                    BackgroundProtectionStatus.VERIFIED_RESTRICTED -> "Action Required"
                    BackgroundProtectionStatus.UNVERIFIABLE -> "Setup Recommended"
                    BackgroundProtectionStatus.SETTINGS_OPENED_UNVERIFIABLE -> "Settings Opened"
                }

                val badgeColor = when (status) {
                    BackgroundProtectionStatus.VERIFIED_CONFIGURED,
                    BackgroundProtectionStatus.SETTINGS_OPENED_UNVERIFIABLE -> PassGreen
                    else -> EscalateAmber
                }

                val badgeIcon = when (status) {
                    BackgroundProtectionStatus.VERIFIED_CONFIGURED -> Icons.Default.CheckCircle
                    BackgroundProtectionStatus.SETTINGS_OPENED_UNVERIFIABLE -> Icons.Default.Info
                    else -> Icons.Default.Warning
                }

                Row(
                    modifier = Modifier
                        .background(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body text according to status
            when (status) {
                BackgroundProtectionStatus.SETTINGS_OPENED_UNVERIFIABLE -> {
                    Text(
                        text = "Settings opened. Background protection status cannot be verified automatically on this device.\n\n" +
                               "Please confirm Autostart is enabled and Battery Saver is set to No restrictions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BackgroundProtectionStatus.UNVERIFIABLE -> {
                    Text(
                        text = "Background protection status cannot be verified automatically on this device.\n\n" +
                               "Make sure Autostart is enabled and Battery Saver is set to No restrictions " +
                               "so SMS Guard can scan messages when the app is closed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BackgroundProtectionStatus.VERIFIED_CONFIGURED -> {
                    Text(
                        text = "SMS Guard is configured to run in the background without battery restrictions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BackgroundProtectionStatus.VERIFIED_RESTRICTED -> {
                    Text(
                        text = BackgroundProtectionManager.getGuidanceText(context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Explicit note if state cannot be automatically verified on OEM device
            if (isUnverifiable) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = BackgroundProtectionManager.UNVERIFIABLE_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            if (status == BackgroundProtectionStatus.SETTINGS_OPENED_UNVERIFIABLE) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onCheckAgain,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Check Again")
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Re-open Settings")
                    }
                }
            } else {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when (status) {
                            BackgroundProtectionStatus.VERIFIED_CONFIGURED -> "Manage Background Settings"
                            BackgroundProtectionStatus.UNVERIFIABLE -> "Manage Background Settings"
                            else -> "Enable Background Protection"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Force Stop Clarification Note
            Text(
                text = BackgroundProtectionManager.FORCE_STOP_WARNING,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
