package com.smsfraud.detector.domain.model

/**
 * Rule Engine output per Architecture v2 Layer 2A.
 * Terminal actions: FORCE_PASS, FORCE_BLOCK — pipeline stops.
 * SCORE: soft score, pipeline continues to ML/URL/Sender engines.
 */
data class RuleResult(
    val action: RuleAction,
    val ruleLabel: String,
    val ruleConfidence: Float,
    val ruleScore: Float
)

enum class RuleAction {
    FORCE_PASS,
    FORCE_BLOCK,
    SCORE
}
