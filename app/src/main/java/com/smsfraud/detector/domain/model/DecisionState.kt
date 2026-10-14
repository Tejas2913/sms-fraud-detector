package com.smsfraud.detector.domain.model

/**
 * Four frozen research decision states — per Notebook 04 decision-controller cascade.
 *
 * Ordering mirrors the cascade priority:
 *   PASS       — high confidence, low risk (risk_final ≤ 0.25)
 *   PASS_WATCH — LOW confidence branch inside the low-risk region (risk_final < 0.25, confidence < 0.50)
 *   ESCALATE   — medium risk (0.25 < risk_final ≤ 0.65) or low confidence with elevated risk
 *   BLOCK      — high risk (risk_final > 0.65)
 *
 * IMPORTANT: PASS_WATCH is NOT produced by a 0.45 threshold.
 * It is the low-confidence branch when risk is also low (<0.25).
 *
 * External/research label for PASS_WATCH is "PASS-WATCH".
 * Use [label] when writing to audit logs, CSV output, or comparisons with Python outputs.
 */
enum class DecisionState(val label: String) {
    PASS("PASS"),
    PASS_WATCH("PASS-WATCH"),
    ESCALATE("ESCALATE"),
    BLOCK("BLOCK");

    companion object {
        fun fromLabel(label: String): DecisionState =
            entries.firstOrNull { it.label == label }
                ?: throw IllegalArgumentException("Unknown decision label: $label")
    }
}
