package com.smsfraud.detector.ui.developermode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Developer Mode per Product Spec v4 §13.
 * Hidden until 7-tap unlock. Shows raw per-engine scores,
 * fusion math, latency, and version metadata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperModeScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DeveloperModeViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val simulationMessage by viewModel.simulationMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(simulationMessage) {
        simulationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSimulationMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Mode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // DEBUG ONLY: PASS-WATCH simulation button (always visible in developer mode)
            item {
                Button(
                    onClick = { viewModel.simulatePassWatch() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7B5EA7)
                    )
                ) {
                    Text("[DEBUG] Simulate PASS-WATCH")
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            if (logs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No decisions recorded yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(logs, key = { it.logId }) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Decision: ${log.decisionState}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DevRow("Rule", "${log.ruleAction ?: "—"} / ${log.ruleLabel ?: "—"}")
                            DevRow("ML Score", "${log.mlScore}")
                            DevRow("URL Risk", "${log.urlRisk}")
                            DevRow("Sender Risk", "${log.senderRisk}")
                            DevRow("url_present", "${log.urlPresent}")
                            DevRow("sender_in_contacts", "${log.senderInContacts}")
                            DevRow("trusted_sender_rec", "${log.trustedSenderRecommendation}")
                            DevRow("Final Risk", "${log.riskFinal}")
                            DevRow("Latency", "${log.inferenceLatencyMs ?: "—"} ms")
                            DevRow("Model Version", log.modelVersion ?: "mock")
                            DevRow("Rule Version", log.ruleVersion ?: "—")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}
