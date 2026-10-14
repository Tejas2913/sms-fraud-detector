package com.smsfraud.detector.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AuditLog entity — UNION schema of Architecture v2 §2 + Product Spec v4 §9.2.
 *
 * Includes all fields from both documents:
 * - Architecture v2: url_present, sender_in_contacts, trusted_sender_recommendation,
 *   user_action (errata A1), lime_result_hash (errata A2)
 * - Product Spec v4: inference_latency_ms, model_version, rule_version
 *
 * Permanent record that survives MessageRef deletion.
 * No raw SMS text, no feature vectors, no PII.
 */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "log_id")
    val logId: String,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "rule_action")
    val ruleAction: String?,

    @ColumnInfo(name = "rule_label")
    val ruleLabel: String?,

    @ColumnInfo(name = "ml_score")
    val mlScore: Float,

    @ColumnInfo(name = "url_risk")
    val urlRisk: Float,

    @ColumnInfo(name = "sender_risk")
    val senderRisk: Float,

    @ColumnInfo(name = "risk_final")
    val riskFinal: Float,

    @ColumnInfo(name = "decision_state")
    val decisionState: String,

    // Cross-engine booleans (Architecture v2, resolved in Product Spec v4)
    @ColumnInfo(name = "url_present")
    val urlPresent: Boolean,

    @ColumnInfo(name = "sender_in_contacts")
    val senderInContacts: Boolean,

    @ColumnInfo(name = "trusted_sender_recommendation")
    val trustedSenderRecommendation: Boolean,

    // Errata A1: referenced in Product Spec v4 Table 8 Row 1 but missing from Table 10
    @ColumnInfo(name = "user_action")
    val userAction: String? = null,

    // Errata A2: Architecture v2 field, null for all MVP records
    @ColumnInfo(name = "lime_result_hash")
    val limeResultHash: String? = null,

    // Product Spec v4 additions
    @ColumnInfo(name = "inference_latency_ms")
    val inferenceLatencyMs: Int? = null,

    @ColumnInfo(name = "model_version")
    val modelVersion: String? = null,

    @ColumnInfo(name = "rule_version")
    val ruleVersion: String? = null,

    // Milestone 3: pipeline decision reason (e.g. "rule:CredentialHarvest", "confidence_gate")
    @ColumnInfo(name = "decision_reason")
    val decisionReason: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
