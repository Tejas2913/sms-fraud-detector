package com.smsfraud.detector.engine

import com.smsfraud.detector.domain.engine.DecisionController
import com.smsfraud.detector.domain.model.DecisionEvent
import com.smsfraud.detector.domain.model.DecisionState
import com.smsfraud.detector.domain.model.FusionResult
import com.smsfraud.detector.domain.model.MlResult
import com.smsfraud.detector.domain.model.RuleAction
import com.smsfraud.detector.domain.model.RuleResult
import com.smsfraud.detector.domain.model.SenderResult
import com.smsfraud.detector.domain.model.UrlResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decision Controller — exact implementation of Notebook 04 Cell 25
 * decision_controller() function.
 *
 * FROZEN PRIORITY CASCADE (8 steps, first-matching wins):
 *
 *   1. FORCE_BLOCK rule action
 *      → BLOCK, reason = "rule:{label}"
 *
 *   2. FORCE_PASS rule action + no URL present
 *      → PASS, reason = "rule:{label}+no_url"
 *
 *   3. Trusted sender + no URL + risk_final < 0.25
 *      → PASS, reason = "trusted_sender+no_url"
 *
 *   4. Trusted sender + URL present
 *      → NO FAST PATH (falls through to fusion-based decision)
 *      (documented explicitly in NB04: "bypass suppressed, falls through")
 *
 *   5. Confidence gate fired (min_confidence < 0.50)
 *      → ESCALATE, reason = "confidence_gate"
 *
 *   6. Low risk (risk_final < 0.25):
 *      min_confidence >= 0.85  → PASS,       reason = "low_risk"
 *      min_confidence < 0.85   → PASS-WATCH, reason = "low_risk"
 *
 *   7. Medium risk (0.25 <= risk_final < 0.65)
 *      → ESCALATE, reason = "mid_risk"
 *
 *   8. High risk (risk_final >= 0.65):
 *      min_confidence >= 0.80  → BLOCK,    reason = "high_risk"
 *      min_confidence < 0.80   → ESCALATE, reason = "high_risk_low_confidence"
 *
 * CRITICAL NOTES:
 *   - 0.45 is NOT a decision threshold (not present anywhere in this cascade).
 *   - PASS-WATCH is ONLY emitted in step 6 (low risk, lower confidence).
 *   - When confidence gate fires, the result is ALWAYS ESCALATE (step 5),
 *     regardless of whether risk is low or high.
 *   - Thresholds (0.25, 0.65, 0.85, 0.80, 0.50) are verbatim from NB04.
 */
@Singleton
class DecisionControllerImpl @Inject constructor() : DecisionController {

    override suspend fun decide(
        ruleResult:    RuleResult,
        fusionResult:  FusionResult,
        senderResult:  SenderResult,
        urlResult:     UrlResult,
        mlResult:      MlResult,
        messageRefId:  String
    ): DecisionEvent {
        val (state, reason) = cascade(
            ruleResult   = ruleResult,
            fusionResult = fusionResult,
            senderResult = senderResult,
            urlPresent   = urlResult.urlPresent
        )
        return DecisionEvent(
            messageRefId   = messageRefId,
            decisionState  = state,
            riskFinal      = fusionResult.riskFinal,
            decisionReason = reason
        )
    }

    /**
     * Pure cascade logic — isolated for unit testing without coroutine overhead.
     * Returns Pair(DecisionState, reason).
     *
     * This directly mirrors the Python function body:
     *   decision_controller(fusion_out, rule_out, sender_out, url_present)
     */
    fun cascade(
        ruleResult:   RuleResult,
        fusionResult: FusionResult,
        senderResult: SenderResult,
        urlPresent:   Boolean
    ): Pair<DecisionState, String> {
        val riskFinal   = fusionResult.riskFinal
        val gateFired   = !fusionResult.confidenceMet   // gate fired = NOT confidence met
        val trusted     = senderResult.trustedSenderRecommendation
        val minConf     = fusionResult.minConfidence

        // Step 1 — FORCE_BLOCK terminal action
        if (ruleResult.action == RuleAction.FORCE_BLOCK) {
            return DecisionState.BLOCK to "rule:${ruleResult.ruleLabel}"
        }

        // Step 2 — FORCE_PASS + no URL (FORCE_PASS with URL falls through)
        if (ruleResult.action == RuleAction.FORCE_PASS && !urlPresent) {
            return DecisionState.PASS to "rule:${ruleResult.ruleLabel}+no_url"
        }

        // Step 3 — Trusted sender + no URL + low risk
        if (trusted && !urlPresent && riskFinal < 0.25f) {
            return DecisionState.PASS to "trusted_sender+no_url"
        }

        // Step 4 — Trusted sender + URL present: bypass suppressed, fall through
        // (no return here — explicitly documented in NB04)

        // Step 5 — Confidence gate
        if (gateFired) {
            return DecisionState.ESCALATE to "confidence_gate"
        }

        // Step 6 — Low risk
        if (riskFinal < 0.25f) {
            return if (minConf >= 0.85f) {
                DecisionState.PASS to "low_risk"
            } else {
                DecisionState.PASS_WATCH to "low_risk"
            }
        }

        // Step 7 — Medium risk
        if (riskFinal < 0.65f) {
            return DecisionState.ESCALATE to "mid_risk"
        }

        // Step 8 — High risk
        return if (minConf >= 0.80f) {
            DecisionState.BLOCK to "high_risk"
        } else {
            DecisionState.ESCALATE to "high_risk_low_confidence"
        }
    }

    companion object {
        // Frozen thresholds — verbatim from Notebook 04 Cell 25
        const val PASS_MAX           = 0.25f
        const val ESCALATE_MAX       = 0.65f
        const val CONFIDENCE_GATE    = 0.50f
        const val HIGH_CONF_PASS     = 0.85f
        const val HIGH_CONF_BLOCK    = 0.80f
    }
}
