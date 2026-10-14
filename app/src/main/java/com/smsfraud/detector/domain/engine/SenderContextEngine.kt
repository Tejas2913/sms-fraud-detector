package com.smsfraud.detector.domain.engine

import com.smsfraud.detector.domain.model.SenderResult

/**
 * Sender Context Engine interface per Architecture v2 Layer 2D.
 * Emits TrustedSenderRecommendation — NEVER produces a terminal decision.
 * Implementation in Milestone 2.
 */
interface SenderContextEngine {
    suspend fun evaluate(senderString: String): SenderResult
}
