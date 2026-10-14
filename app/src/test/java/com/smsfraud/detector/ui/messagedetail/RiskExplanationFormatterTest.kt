package com.smsfraud.detector.ui.messagedetail

import com.smsfraud.detector.data.db.entity.AuditLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RiskExplanationFormatter and explainable risk score calculations.
 *
 * Covers requirements 4-9:
 *   - Risk contribution formatting (no scientific notation)
 *   - Final risk explanation
 *   - PASS-WATCH explanation
 *   - PASS explanation
 *   - ESCALATE explanation
 *   - BLOCK explanation
 */
class RiskExplanationFormatterTest {

    // -------------------------------------------------------------------------
    // Formatting tests (No scientific notation)
    // -------------------------------------------------------------------------

    @Test
    fun `formatFloatDecimals formats small floats without scientific notation`() {
        val val1 = 0.0000078f
        val str1 = RiskExplanationFormatter.formatFloatDecimals(val1)
        assertFalse("Formatted float must NOT contain scientific notation 'E'", str1.contains("E"))
        assertFalse("Formatted float must NOT contain scientific notation 'e'", str1.contains("e"))
        assertEquals("0.000008", str1)
    }

    @Test
    fun `formatFloatDecimals formats zero correctly`() {
        val str = RiskExplanationFormatter.formatFloatDecimals(0f)
        assertEquals("0.000000", str)
    }

    @Test
    fun `formatPercentage formats percentages accurately`() {
        assertEquals("22%", RiskExplanationFormatter.formatPercentage(0.22000234f))
        assertEquals("65%", RiskExplanationFormatter.formatPercentage(0.65f))
        assertEquals("0%", RiskExplanationFormatter.formatPercentage(0.0f))
        assertEquals("0.0008%", RiskExplanationFormatter.formatPercentage(0.0000078f))
    }

    // -------------------------------------------------------------------------
    // Signal Labels
    // -------------------------------------------------------------------------

    @Test
    fun `signal labels return human-readable summaries`() {
        assertEquals("Very low risk", RiskExplanationFormatter.contentAnalysisLabel(0.0000078f))
        assertEquals("High risk", RiskExplanationFormatter.contentAnalysisLabel(0.85f))

        assertEquals("Safe / No suspicious link", RiskExplanationFormatter.linkSafetyLabel(0f, false))
        assertEquals("High-risk TLD / Shortener", RiskExplanationFormatter.linkSafetyLabel(0.75f, true))

        assertEquals("Unverified Sender", RiskExplanationFormatter.senderTrustLabel(0.55f, senderInContacts = false, trusted = false))
        assertEquals("Known / Verified Contact", RiskExplanationFormatter.senderTrustLabel(0.55f, senderInContacts = true, trusted = false))

        assertEquals("Normal", RiskExplanationFormatter.ruleAnalysisLabel("normal"))
        assertEquals("CredentialHarvest", RiskExplanationFormatter.ruleAnalysisLabel("CredentialHarvest"))
    }

    // -------------------------------------------------------------------------
    // Decision Explanations
    // -------------------------------------------------------------------------

    @Test
    fun `PASS-WATCH explanation correctly explains low risk with unverified sender`() {
        val explanation = RiskExplanationFormatter.getHumanExplanation("PASS_WATCH", "low_risk")
        assertTrue(explanation.contains("Low-risk message from an unverified sender"))
        assertTrue(explanation.contains("PASS-WATCH"))
    }

    @Test
    fun `PASS explanation explains rule pass and trusted sender`() {
        val passRule = RiskExplanationFormatter.getHumanExplanation("PASS", "rule:OTPDetect+no_url")
        assertTrue(passRule.contains("OTPDetect"))

        val passTrusted = RiskExplanationFormatter.getHumanExplanation("PASS", "trusted_sender+no_url")
        assertTrue(passTrusted.contains("trusted sender"))

        val passGeneral = RiskExplanationFormatter.getHumanExplanation("PASS", "low_risk")
        assertTrue(passGeneral.contains("High-confidence safe message"))
    }

    @Test
    fun `ESCALATE explanation explains confidence gate and mid risk`() {
        val escalateGate = RiskExplanationFormatter.getHumanExplanation("ESCALATE", "confidence_gate")
        assertTrue(escalateGate.contains("low confidence"))

        val escalateMid = RiskExplanationFormatter.getHumanExplanation("ESCALATE", "mid_risk")
        assertTrue(escalateMid.contains("Moderate risk detected"))
    }

    @Test
    fun `BLOCK explanation warns about high-risk scam and rules`() {
        val blockRule = RiskExplanationFormatter.getHumanExplanation("BLOCK", "rule:CredentialHarvest")
        assertTrue(blockRule.contains("CredentialHarvest"))

        val blockHigh = RiskExplanationFormatter.getHumanExplanation("BLOCK", "high_risk")
        assertTrue(blockHigh.contains("High-risk fraudulent message"))
    }

    // -------------------------------------------------------------------------
    // Mathematical Calculation Breakdown
    // -------------------------------------------------------------------------

    @Test
    fun `getCalculationBreakdown produces correct math strings for hi SMS`() {
        val auditLog = AuditLogEntity(
            logId = "log-1",
            messageId = "msg-1",
            ruleAction = "SCORE",
            ruleLabel = "normal",
            mlScore = 0.0000078f,
            urlRisk = 0.0f,
            senderRisk = 0.55f,
            riskFinal = 0.22000234f,
            decisionState = "PASS_WATCH",
            urlPresent = false,
            senderInContacts = false,
            trustedSenderRecommendation = false,
            decisionReason = "low_risk",
            inferenceLatencyMs = 145,
            modelVersion = "INT8",
            createdAt = System.currentTimeMillis()
        )

        val breakdown = RiskExplanationFormatter.getCalculationBreakdown(auditLog)

        assertTrue(breakdown.mlContributionStr.contains("0.30 × 0.000008 ≈ 0.000002"))
        assertTrue(breakdown.urlContributionStr.contains("0.30 × 0.000000 = 0.000000"))
        assertTrue(breakdown.senderContributionStr.contains("0.40 × 0.550000 = 0.220000"))
        assertEquals("0.000000", breakdown.ruleAdjustmentStr)
        assertTrue(breakdown.finalRiskStr.contains("≈ 0.220002 = 22%"))
    }
}
