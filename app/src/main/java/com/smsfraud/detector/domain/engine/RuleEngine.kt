package com.smsfraud.detector.domain.engine

import com.smsfraud.detector.domain.model.RuleResult

/**
 * Rule Engine interface per Architecture v2 Layer 2A.
 * Receives normalised_text ONLY — no sender metadata, no contact data, no URL objects.
 * Implementation in Milestone 2.
 */
interface RuleEngine {
    fun evaluate(normalisedText: String): RuleResult
}
