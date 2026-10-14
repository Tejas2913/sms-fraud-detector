package com.smsfraud.detector.engine

import com.smsfraud.detector.domain.model.DecisionState
import com.smsfraud.detector.domain.model.FusionResult
import com.smsfraud.detector.domain.model.RuleAction
import com.smsfraud.detector.domain.model.RuleResult
import com.smsfraud.detector.domain.model.SenderResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DecisionControllerImpl — exact Notebook 04 Cell 25 cascade.
 *
 * Covers all 8 cascade steps plus all 12 cases from the Milestone 2 spec:
 *   A. Normal low-risk message
 *   B. Credential-harvesting message
 *   C. Malicious URL
 *   D. Trusted sender
 *   E. FORCE_BLOCK
 *   F. FORCE_PASS
 *   G. Low confidence + low risk
 *   H. Low confidence + medium/high risk
 *   I. Risk exactly at 0.25
 *   J. Risk exactly at 0.65
 *   K. High risk + high confidence
 *   L. High risk + low confidence
 */
class DecisionControllerImplTest {

    private lateinit var controller: DecisionControllerImpl

    @Before fun setUp() { controller = DecisionControllerImpl() }

    // -------------------------------------------------------------------------
    // Builder helpers
    // -------------------------------------------------------------------------

    private fun fusion(
        risk: Float, minConf: Float,
        confidenceMet: Boolean = minConf >= 0.50f,
        highUncertainty: Boolean = false,
        ruleDelta: Float = 0f
    ) = FusionResult(risk, ruleDelta, confidenceMet, minConf, highUncertainty)

    private fun rule(action: RuleAction, label: String = "test") =
        RuleResult(action, label, 0.9f, 0.5f)

    private fun sender(trusted: Boolean) =
        SenderResult(if (trusted) 0.10f else 0.40f, 0.85f, "test", trusted, trusted)

    // -------------------------------------------------------------------------
    // Step 1 — FORCE_BLOCK
    // -------------------------------------------------------------------------

    @Test fun `E - FORCE_BLOCK always gives BLOCK`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.FORCE_BLOCK, "CredentialHarvest"),
            fusion(0.0f, 1.0f),
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.BLOCK, state)
        assertEquals("rule:CredentialHarvest", reason)
    }

    @Test fun `FORCE_BLOCK ignores risk and confidence`() {
        // Even with low risk and high confidence, FORCE_BLOCK → BLOCK
        val (state, _) = controller.cascade(
            rule(RuleAction.FORCE_BLOCK, "KnownPhishingTemplate"),
            fusion(0.0f, 1.0f),
            sender(true),   // trusted sender too — still BLOCK
            urlPresent = false
        )
        assertEquals(DecisionState.BLOCK, state)
    }

    // -------------------------------------------------------------------------
    // Step 2 — FORCE_PASS + no URL
    // -------------------------------------------------------------------------

    @Test fun `F - FORCE_PASS with no URL gives PASS`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.FORCE_PASS, "OTPDetect"),
            fusion(0.0f, 1.0f),
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.PASS, state)
        assertEquals("rule:OTPDetect+no_url", reason)
    }

    @Test fun `FORCE_PASS with URL falls through to fusion`() {
        // FORCE_PASS + URL present → bypass suppressed → confidence gate check
        // With high confidence and high risk → should reach Step 8
        val (state, _) = controller.cascade(
            rule(RuleAction.FORCE_PASS, "DeliveryNotification"),
            fusion(0.70f, 0.90f),   // high risk, high confidence
            sender(false),
            urlPresent = true        // URL present suppresses FORCE_PASS
        )
        assertEquals(DecisionState.BLOCK, state)
    }

    // -------------------------------------------------------------------------
    // Step 3 — Trusted sender + no URL + risk < 0.25
    // -------------------------------------------------------------------------

    @Test fun `D - trusted sender no URL low risk gives PASS`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.15f, 0.90f),
            sender(trusted = true),
            urlPresent = false
        )
        assertEquals(DecisionState.PASS, state)
        assertEquals("trusted_sender+no_url", reason)
    }

    @Test fun `trusted sender with URL falls through`() {
        // Trusted sender + URL → bypass suppressed
        // With gate not fired + risk=0.20 + minConf=0.90 → Step 6 → PASS
        val (state, _) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.20f, 0.90f),
            sender(trusted = true),
            urlPresent = true   // URL suppresses trusted-sender bypass
        )
        assertEquals(DecisionState.PASS, state)
    }

    // -------------------------------------------------------------------------
    // Step 5 — Confidence gate
    // -------------------------------------------------------------------------

    @Test fun `G - low confidence + low risk gives ESCALATE via confidence gate`() {
        // NB04 Cell 25: gate_fired → ESCALATE (regardless of risk level)
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.10f, 0.45f, confidenceMet = false),  // gate fired, risk < 0.25
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.ESCALATE, state)
        assertEquals("confidence_gate", reason)
    }

    @Test fun `H - low confidence + medium risk gives ESCALATE via confidence gate`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.50f, 0.40f, confidenceMet = false),  // gate fired, risk in mid-band
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.ESCALATE, state)
        assertEquals("confidence_gate", reason)
    }

    @Test fun `H - low confidence + high risk gives ESCALATE via confidence gate`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.80f, 0.30f, confidenceMet = false),  // gate fired, high risk
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.ESCALATE, state)
        assertEquals("confidence_gate", reason)
    }

    // -------------------------------------------------------------------------
    // Step 6 — Low risk (< 0.25)
    // -------------------------------------------------------------------------

    @Test fun `A - normal low risk high confidence gives PASS`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.15f, 0.90f),   // risk < 0.25, minConf >= 0.85
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.PASS, state)
        assertEquals("low_risk", reason)
    }

    @Test fun `low risk minConf exactly 0_85 gives PASS`() {
        val (state, _) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.20f, 0.85f),
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.PASS, state)
    }

    @Test fun `low risk minConf just below 0_85 gives PASS_WATCH`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.20f, 0.84f),   // risk < 0.25, minConf < 0.85
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.PASS_WATCH, state)
        assertEquals("low_risk", reason)
    }

    @Test fun `unverified sender low risk benign message explicitly returns PASS_WATCH`() {
        // Construct realistic engine values for benign message from unsaved number:
        // mlScore ≈ 0.0, urlRisk = 0.0, senderRisk = 0.55, ruleDelta = 0.0
        // riskFinal = 0.30*0.0 + 0.30*0.0 + 0.40*0.55 + 0.0 = 0.22 (< 0.25)
        // senderConfidence = 0.55, minConfidence = min(0.95, 1.0, 0.55, 0.90) = 0.55 (>= 0.50 and < 0.85)
        val (state, reason) = controller.cascade(
            ruleResult = rule(RuleAction.SCORE, "normal"),
            fusionResult = fusion(
                risk = 0.22f,
                minConf = 0.55f,
                confidenceMet = true
            ),
            senderResult = SenderResult(
                senderRisk = 0.55f,
                senderConfidence = 0.55f,
                senderLabel = "unknown_sender",
                senderInContacts = false,
                trustedSenderRecommendation = false
            ),
            urlPresent = false
        )
        assertEquals(DecisionState.PASS_WATCH, state)
        assertEquals("low_risk", reason)
    }

    @Test fun `I - risk exactly at 0_25 goes to medium risk branch ESCALATE`() {
        // 0.25 is NOT < 0.25, so it falls to Step 7 (medium risk)
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.25f, 0.90f),
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.ESCALATE, state)
        assertEquals("mid_risk", reason)
    }

    // -------------------------------------------------------------------------
    // Step 7 — Medium risk [0.25, 0.65)
    // -------------------------------------------------------------------------

    @Test fun `C - malicious URL medium risk gives ESCALATE`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "RewardScam"),
            fusion(0.45f, 0.90f),
            sender(false),
            urlPresent = true
        )
        assertEquals(DecisionState.ESCALATE, state)
        assertEquals("mid_risk", reason)
    }

    @Test fun `medium risk range gives ESCALATE`() {
        listOf(0.25f, 0.40f, 0.64f).forEach { risk ->
            val (state, _) = controller.cascade(
                rule(RuleAction.SCORE, "normal"),
                fusion(risk, 0.95f),
                sender(false),
                urlPresent = false
            )
            assertEquals("risk=$risk should give ESCALATE", DecisionState.ESCALATE, state)
        }
    }

    // -------------------------------------------------------------------------
    // Step 8 — High risk (>= 0.65)
    // -------------------------------------------------------------------------

    @Test fun `J - risk exactly at 0_65 gives BLOCK with high confidence`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.65f, 0.90f),   // risk >= 0.65, minConf >= 0.80
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.BLOCK, state)
        assertEquals("high_risk", reason)
    }

    @Test fun `K - high risk high confidence gives BLOCK`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "CredentialHarvest"),
            fusion(0.85f, 0.92f),   // minConf >= 0.80
            sender(false),
            urlPresent = true
        )
        assertEquals(DecisionState.BLOCK, state)
        assertEquals("high_risk", reason)
    }

    @Test fun `L - high risk low confidence gives ESCALATE`() {
        val (state, reason) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.80f, 0.75f),   // risk >= 0.65, minConf < 0.80
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.ESCALATE, state)
        assertEquals("high_risk_low_confidence", reason)
    }

    @Test fun `B - credential harvest FORCE_BLOCK before reaching high risk`() {
        // FORCE_BLOCK from rule engine should short-circuit before fusion risk matters
        val (state, _) = controller.cascade(
            rule(RuleAction.FORCE_BLOCK, "CredentialHarvest"),
            fusion(0.20f, 0.95f),  // low risk, high confidence — but FORCE_BLOCK overrides
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.BLOCK, state)
    }

    // -------------------------------------------------------------------------
    // Boundary: exactly at threshold values
    // -------------------------------------------------------------------------

    @Test fun `risk just below 0_65 gives ESCALATE not BLOCK`() {
        val (state, _) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.6499f, 0.95f),
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.ESCALATE, state)
    }

    @Test fun `minConf exactly 0_80 for high risk gives BLOCK`() {
        val (state, _) = controller.cascade(
            rule(RuleAction.SCORE, "normal"),
            fusion(0.80f, 0.80f),
            sender(false),
            urlPresent = false
        )
        assertEquals(DecisionState.BLOCK, state)
    }
}
