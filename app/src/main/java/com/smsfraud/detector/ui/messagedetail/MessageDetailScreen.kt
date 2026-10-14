package com.smsfraud.detector.ui.messagedetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smsfraud.detector.ui.theme.BlockRed
import com.smsfraud.detector.ui.theme.EscalateAmber
import com.smsfraud.detector.ui.theme.PassGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: MessageDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.messageRef == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Message not found")
                }
            }
            else -> {
                val messageRef = uiState.messageRef!!
                val auditLog = uiState.auditLog
                var isCalculationExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // 1. Decision badge
                    Text(
                        text = messageRef.decisionState,
                        style = MaterialTheme.typography.headlineMedium,
                        color = when (messageRef.decisionState) {
                            "PASS" -> PassGreen
                            "PASS_WATCH", "PASS-WATCH" -> PassGreen
                            "ESCALATE" -> EscalateAmber
                            "BLOCK" -> BlockRed
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Actual SMS body
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = uiState.messageBody ?: "This message is no longer available.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Risk Score bar
                    if (auditLog != null) {
                        Text(
                            text = "Risk Score: ${RiskExplanationFormatter.formatPercentage(auditLog.riskFinal)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { auditLog.riskFinal },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = when (messageRef.decisionState.uppercase()) {
                                "BLOCK" -> BlockRed
                                "ESCALATE" -> EscalateAmber
                                "PASS_WATCH", "PASS-WATCH" -> PassGreen
                                "PASS" -> PassGreen
                                else -> when {
                                    auditLog.riskFinal >= 0.65f -> BlockRed
                                    auditLog.riskFinal >= 0.25f -> EscalateAmber
                                    else -> PassGreen
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 4. Signal Breakdown
                        Text("Signal Breakdown", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        SignalRow("Content Analysis", RiskExplanationFormatter.contentAnalysisLabel(auditLog.mlScore))
                        SignalRow("Link Safety", RiskExplanationFormatter.linkSafetyLabel(auditLog.urlRisk, auditLog.urlPresent))
                        SignalRow("Sender Trust", RiskExplanationFormatter.senderTrustLabel(auditLog.senderRisk, auditLog.senderInContacts, auditLog.trustedSenderRecommendation))
                        SignalRow("Rule Analysis", RiskExplanationFormatter.ruleAnalysisLabel(auditLog.ruleLabel))

                        Spacer(modifier = Modifier.height(20.dp))

                        // 5. Why this result?
                        Text("Why this result?", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                text = RiskExplanationFormatter.getHumanExplanation(messageRef.decisionState, auditLog.decisionReason),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 6. How was this score calculated? (Expandable)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isCalculationExpanded = !isCalculationExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "How was this score calculated?",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Icon(
                                        imageVector = if (isCalculationExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isCalculationExpanded) "Collapse" else "Expand"
                                    )
                                }

                                AnimatedVisibility(visible = isCalculationExpanded) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        val math = RiskExplanationFormatter.getCalculationBreakdown(auditLog)

                                        MathRow("ML contribution (30%)", math.mlContributionStr)
                                        MathRow("URL contribution (30%)", math.urlContributionStr)
                                        MathRow("Sender contribution (40%)", math.senderContributionStr)
                                        MathRow("Rule adjustment", math.ruleAdjustmentStr)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        MathRow("Final fused risk", math.finalRiskStr, isBold = true)

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Metadata", style = MaterialTheme.typography.labelLarge)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Model: ${auditLog.modelVersion ?: "INT8"}\n" +
                                                    "Inference Latency: ${auditLog.inferenceLatencyMs ?: "—"} ms\n" +
                                                    "Decision Reason: ${auditLog.decisionReason ?: "—"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MathRow(label: String, mathStr: String, isBold: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = if (isBold) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall)
        Text(
            text = mathStr,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
