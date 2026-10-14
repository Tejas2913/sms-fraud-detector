package com.smsfraud.detector.domain.model

/**
 * Score Fusion output per Architecture v2 Layer 3.
 *
 * Extended for Milestone 2 to carry the full set of outputs produced by
 * fuse_scores() in Notebook 04 Cell 24.
 *
 * [riskFinal]           — clip(0.30*ml + 0.30*url + 0.40*sender + rule_delta, 0, 1)
 * [ruleDelta]           — clip((rule_score - 0.5) * 0.20, -0.15, +0.15)
 * [confidenceMet]       — true when min_confidence >= confidence_gate_threshold (0.50)
 *                         = !confidence_gate_fired
 * [minConfidence]       — min of all four engine confidences
 * [highUncertaintyFlag] — true when more than 2 engine confidences are < 0.70
 */
data class FusionResult(
    val riskFinal: Float,
    val ruleDelta: Float,
    val confidenceMet: Boolean,
    val minConfidence: Float,
    val highUncertaintyFlag: Boolean
)
