package com.smsfraud.detector.ui.developermode

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsfraud.detector.data.db.entity.AuditLogEntity
import com.smsfraud.detector.data.db.entity.MessageRefEntity
import com.smsfraud.detector.data.repository.AuditLogRepository
import com.smsfraud.detector.data.repository.MessageRepository
import com.smsfraud.detector.domain.model.DecisionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DeveloperModeViewModel @Inject constructor(
    private val auditLogRepository: AuditLogRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _logs = MutableStateFlow<List<AuditLogEntity>>(emptyList())
    val logs: StateFlow<List<AuditLogEntity>> = _logs.asStateFlow()

    private val _simulationMessage = MutableStateFlow<String?>(null)
    val simulationMessage: StateFlow<String?> = _simulationMessage.asStateFlow()

    init {
        viewModelScope.launch {
            auditLogRepository.getAll().collect {
                _logs.value = it
            }
        }
    }

    /**
     * DEBUG ONLY — Inserts a synthetic PASS_WATCH record with realistic engine values.
     *
     * Purpose: allows physical verification of the PASS_WATCH UI/navigation/notification
     * path without requiring an unsaved sender number.
     *
     * Values used:
     *   riskFinal = 0.22 (below PASS_MAX=0.25)
     *   mlScore = 0.05, urlRisk = 0.0, senderRisk = 0.55
     *   minConfidence = 0.55 (below HIGH_CONF_PASS=0.85 → PASS_WATCH branch)
     *   decisionReason = "low_risk"
     *
     * The detection engine is NOT invoked. No real SMS is used.
     * No frozen thresholds or weights are modified.
     */
    fun simulatePassWatch() {
        viewModelScope.launch {
            try {
                val refId = "SIM-PW-${UUID.randomUUID()}"
                val nowMs = System.currentTimeMillis()
                // Synthetic hash for the simulated "unsaved sender"
                val syntheticSenderHash = "sim_unverified_sender"

                // Insert MessageRef (no SMS body, no raw sender)
                val messageRef = MessageRefEntity(
                    messageRefId  = refId,
                    senderHash    = syntheticSenderHash,
                    systemSmsUri  = "",
                    receivedAt    = nowMs,
                    decisionState = DecisionState.PASS_WATCH.name
                )
                messageRepository.insert(messageRef)

                // Insert matching AuditLog with realistic PASS_WATCH engine values
                val auditLog = AuditLogEntity(
                    logId                       = UUID.randomUUID().toString(),
                    messageId                   = refId,
                    ruleAction                  = "SCORE",
                    ruleLabel                   = "normal",
                    mlScore                     = 0.05f,
                    urlRisk                     = 0.0f,
                    senderRisk                  = 0.55f,
                    riskFinal                   = 0.22f,
                    decisionState               = DecisionState.PASS_WATCH.name,
                    urlPresent                  = false,
                    senderInContacts            = false,
                    trustedSenderRecommendation = false,
                    decisionReason              = "low_risk",
                    inferenceLatencyMs          = 12,
                    modelVersion                = "sim-passwatch",
                    createdAt                   = nowMs
                )
                auditLogRepository.insert(auditLog)

                _simulationMessage.value = "PASS-WATCH record inserted (ref: ${refId.takeLast(8)})"
                Log.d(TAG, "[DEV] Simulated PASS_WATCH inserted: refId=$refId")
            } catch (e: Exception) {
                Log.e(TAG, "[DEV] simulatePassWatch failed", e)
                _simulationMessage.value = "Simulation failed: ${e.message}"
            }
        }
    }

    fun clearSimulationMessage() {
        _simulationMessage.value = null
    }

    companion object {
        private const val TAG = "DeveloperModeViewModel"
    }
}
