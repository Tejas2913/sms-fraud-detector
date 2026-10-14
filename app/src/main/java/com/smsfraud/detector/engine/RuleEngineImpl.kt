package com.smsfraud.detector.engine

import com.smsfraud.detector.domain.engine.RuleEngine
import com.smsfraud.detector.domain.model.RuleAction
import com.smsfraud.detector.domain.model.RuleResult
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Rule Intelligence Engine — exact implementation of Notebook 04 Cell 18.
 *
 * FROZEN. Do NOT modify patterns, confidence values, or score values without
 * a confirmed parity failure against Notebook 04 outputs.
 *
 * Architecture rules:
 *   - Text-only, deterministic. Input is preprocessed text (message_clean).
 *   - NO sender metadata, NO URL objects, NO contact lookups.
 *   - First-match wins (ordered evaluation exactly as in Python dict iteration).
 *   - Python dicts preserve insertion order since Python 3.7 — we replicate that order.
 *
 * Rule categories:
 *   FORCE_BLOCK: {CredentialHarvest, KnownPhishingTemplate}
 *     → action=FORCE_BLOCK, confidence=0.95, score=1.0
 *   FORCE_PASS: {OTPDetect, BankTransactionTemplate, DeliveryNotification}
 *     → action=FORCE_PASS, confidence=0.85, score=0.0
 *   SCORE: {RewardScam, GovtImpersonation, UrgencyPhrase}
 *     → action=SCORE, confidence=0.75, score=0.80
 *   No match:
 *     → action=SCORE, label="normal", confidence=0.60, score=0.50
 *
 * IMPORTANT: FORCE_PASS is a recommendation only. The Decision Controller
 * resolves it against url_present — this engine never makes a terminal decision.
 */
class RuleEngineImpl @Inject constructor() : RuleEngine {

    override fun evaluate(normalisedText: String): RuleResult = score(normalisedText)

    fun score(messageClean: String): RuleResult {
        for ((label, pattern) in RULE_PATTERNS) {
            if (pattern.matcher(messageClean).find()) {
                return when (label) {
                    in FORCE_BLOCK_RULES -> RuleResult(
                        action = RuleAction.FORCE_BLOCK,
                        ruleLabel = label,
                        ruleConfidence = 0.95f,
                        ruleScore = 1.0f
                    )
                    in FORCE_PASS_RULES -> RuleResult(
                        action = RuleAction.FORCE_PASS,
                        ruleLabel = label,
                        ruleConfidence = 0.85f,
                        ruleScore = 0.0f
                    )
                    else -> RuleResult(
                        action = RuleAction.SCORE,
                        ruleLabel = label,
                        ruleConfidence = 0.75f,
                        ruleScore = 0.80f
                    )
                }
            }
        }
        // No rule matched — neutral score
        return RuleResult(
            action = RuleAction.SCORE,
            ruleLabel = "normal",
            ruleConfidence = 0.60f,
            ruleScore = 0.50f
        )
    }

    companion object {
        // -----------------------------------------------------------------------
        // Eight frozen rules — exact patterns from Notebook 04 Cell 18
        // Insertion order determines evaluation priority (first-match wins)
        // -----------------------------------------------------------------------
        val RULE_PATTERNS: List<Pair<String, Pattern>> = listOf(
            "CredentialHarvest" to Pattern.compile(
                """\b(verify|confirm|update)\b.{0,25}\b(account|password|kyc|pan|otp)\b""",
                Pattern.CASE_INSENSITIVE
            ),
            "KnownPhishingTemplate" to Pattern.compile(
                """\b(account.{0,10}(suspend|block|lock)|kyc.{0,10}(expire|pending))\b""",
                Pattern.CASE_INSENSITIVE
            ),
            "GovtImpersonation" to Pattern.compile(
                """\b(income.?tax|irs|customs|police|court|fine)\b.{0,20}\b(pay|penalty|due)\b""",
                Pattern.CASE_INSENSITIVE
            ),
            "RewardScam" to Pattern.compile(
                """\b(congratulations|winner|lucky draw|lottery|prize|cashback|reward)\b""",
                Pattern.CASE_INSENSITIVE
            ),
            "OTPDetect" to Pattern.compile(
                """\botp\b.{0,15}\b\d{4,8}\b""",
                Pattern.CASE_INSENSITIVE
            ),
            "BankTransactionTemplate" to Pattern.compile(
                """\b(debited|credited|a/c|acct)\b.{0,20}\b(rs\.?|inr|\[AMOUNT\])\b""",
                Pattern.CASE_INSENSITIVE
            ),
            "DeliveryNotification" to Pattern.compile(
                """\b(parcel|package|courier|shipment|out for delivery)\b""",
                Pattern.CASE_INSENSITIVE
            ),
            "UrgencyPhrase" to Pattern.compile(
                """\b(act now|immediately|urgent|within 24 hours|expire[sd]? (today|soon))\b""",
                Pattern.CASE_INSENSITIVE
            )
        )

        private val FORCE_BLOCK_RULES = setOf("CredentialHarvest", "KnownPhishingTemplate")
        private val FORCE_PASS_RULES  = setOf("OTPDetect", "BankTransactionTemplate", "DeliveryNotification")
        // SCORE rules (not explicitly checked — everything else that matches)
    }
}
