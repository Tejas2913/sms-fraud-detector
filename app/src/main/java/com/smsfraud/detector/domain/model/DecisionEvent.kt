package com.smsfraud.detector.domain.model

/**
 * Event emitted by the Decision Controller onto the Decision Event Bus.
 *
 * Extended for Milestone 2 to carry the decision reason string — matches the
 * "reason" field in Notebook 04 decision_controller() return dicts.
 *
 * [messageRefId]   — opaque ID linking to the MessageRefEntity in Room
 * [decisionState]  — one of PASS, PASS_WATCH, ESCALATE, BLOCK
 * [riskFinal]      — weighted fusion risk score, range [0, 1]
 * [decisionReason] — human-readable reason string (e.g. "rule:CredentialHarvest",
 *                    "confidence_gate", "low_risk", "mid_risk", "trusted_sender+no_url")
 *
 * The Notification Module subscribes to this — the Decision Controller never touches UI.
 */
data class DecisionEvent(
    val messageRefId: String,
    val decisionState: DecisionState,
    val riskFinal: Float,
    val decisionReason: String
)
