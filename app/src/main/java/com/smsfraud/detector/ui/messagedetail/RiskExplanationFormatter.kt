package com.smsfraud.detector.ui.messagedetail

import com.smsfraud.detector.data.db.entity.AuditLogEntity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Utility for formatting explainable risk scores, signal labels, mathematical
 * contribution breakdowns, and human-readable decision state explanations.
 */
object RiskExplanationFormatter {

    // Standard weights from fusion_config.json
    private const val WEIGHT_ML     = 0.30f
    private const val WEIGHT_URL    = 0.30f
    private const val WEIGHT_SENDER = 0.40f

    // -------------------------------------------------------------------------
    // Number formatting (no scientific notation)
    // -------------------------------------------------------------------------

    fun formatFloatDecimals(value: Float, decimals: Int = 6): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = "0." + "0".repeat(decimals)
        val df = DecimalFormat(pattern, symbols)
        return df.format(value.toDouble())
    }

    fun formatPercentage(value: Float): String {
        val pct = value * 100f
        return when {
            pct == 0f -> "0%"
            pct >= 1f -> "${pct.toInt()}%"
            pct >= 0.0001f -> String.format(Locale.US, "%.4f%%", pct)
            else -> "<0.0001%"
        }
    }

    // -------------------------------------------------------------------------
    // Signal breakdown labels
    // -------------------------------------------------------------------------

    fun contentAnalysisLabel(mlScore: Float): String = when {
        mlScore < 0.10f -> "Very low risk"
        mlScore < 0.25f -> "Low risk"
        mlScore < 0.65f -> "Moderate risk"
        else -> "High risk"
    }

    fun linkSafetyLabel(urlRisk: Float, urlPresent: Boolean): String = when {
        !urlPresent || urlRisk == 0.0f -> "Safe / No suspicious link"
        urlRisk < 0.60f -> "Low-risk link"
        urlRisk < 0.80f -> "High-risk TLD / Shortener"
        else -> "Malicious link detected"
    }

    fun senderTrustLabel(senderRisk: Float, senderInContacts: Boolean, trusted: Boolean): String = when {
        senderInContacts -> "Known / Verified Contact"
        trusted -> "Trusted Sender"
        senderRisk <= 0.40f -> "Known Sender"
        else -> "Unverified Sender"
    }

    fun ruleAnalysisLabel(ruleLabel: String?): String {
        if (ruleLabel.isNullOrBlank() || ruleLabel.equals("normal", ignoreCase = true)) {
            return "Normal"
        }
        return ruleLabel
    }

    // -------------------------------------------------------------------------
    // Human-readable result explanation
    // -------------------------------------------------------------------------

    fun getHumanExplanation(decisionState: String, decisionReason: String?): String {
        val reason = decisionReason ?: ""
        return when (decisionState.uppercase()) {
            "PASS_WATCH", "PASS-WATCH" -> when {
                reason == "low_risk" ->
                    "Low-risk message from an unverified sender. The message content and links appear safe, but sender confidence is below the high-confidence threshold, so the system placed it in PASS-WATCH."
                else ->
                    "The message content is low risk, but specific signals warrant caution. Monitoring under PASS-WATCH."
            }
            "PASS" -> when {
                reason.startsWith("rule:") ->
                    "Verified as safe by rule engine (${reason.removePrefix("rule:")})."
                reason == "trusted_sender+no_url" ->
                    "Message received from a trusted sender with no links attached."
                else ->
                    "High-confidence safe message. Content and sender passed all safety checks."
            }
            "ESCALATE" -> when {
                reason == "confidence_gate" ->
                    "One or more detection signals had low confidence. Flagged for review under ESCALATE."
                reason == "high_risk_low_confidence" ->
                    "High risk score detected, but confidence is low. Escalated for user verification."
                else ->
                    "Moderate risk detected. Review links and sender carefully before interacting."
            }
            "BLOCK" -> when {
                reason.startsWith("rule:") ->
                    "Flagged as high-risk scam by rule pattern (${reason.removePrefix("rule:")}). Do not click links or share credentials."
                else ->
                    "High-risk fraudulent message detected. Do not click links, reply, or share personal information."
            }
            else -> "Message processed by detection engine."
        }
    }

    // -------------------------------------------------------------------------
    // Mathematical contribution breakdown
    // -------------------------------------------------------------------------

    data class CalculationBreakdown(
        val mlContributionStr: String,
        val urlContributionStr: String,
        val senderContributionStr: String,
        val ruleAdjustmentStr: String,
        val finalRiskStr: String
    )

    fun getCalculationBreakdown(auditLog: AuditLogEntity): CalculationBreakdown {
        val mlScore = auditLog.mlScore
        val urlRisk = auditLog.urlRisk
        val senderRisk = auditLog.senderRisk
        val riskFinal = auditLog.riskFinal

        val mlContrib = WEIGHT_ML * mlScore
        val urlContrib = WEIGHT_URL * urlRisk
        val senderContrib = WEIGHT_SENDER * senderRisk

        // Derive ruleDelta from fused risk minus linear weighted contributions
        val unclippedDelta = riskFinal - (mlContrib + urlContrib + senderContrib)
        val ruleDelta = if (kotlin.math.abs(unclippedDelta) < 0.0001f) 0.0f else unclippedDelta.coerceIn(-0.15f, +0.15f)

        val mlStr = "0.30 × ${formatFloatDecimals(mlScore)} ≈ ${formatFloatDecimals(mlContrib)}"
        val urlStr = "0.30 × ${formatFloatDecimals(urlRisk)} = ${formatFloatDecimals(urlContrib)}"
        val senderStr = "0.40 × ${formatFloatDecimals(senderRisk)} = ${formatFloatDecimals(senderContrib)}"
        val ruleStr = formatFloatDecimals(ruleDelta)
        val finalStr = "≈ ${formatFloatDecimals(riskFinal)} = ${formatPercentage(riskFinal)}"

        return CalculationBreakdown(
            mlContributionStr = mlStr,
            urlContributionStr = urlStr,
            senderContributionStr = senderStr,
            ruleAdjustmentStr = ruleStr,
            finalRiskStr = finalStr
        )
    }
}
