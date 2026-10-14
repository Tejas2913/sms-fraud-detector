package com.smsfraud.detector.domain.model

/**
 * Sender Context Engine output per Architecture v2 Layer 2D.
 * trusted_sender_recommendation is a recommendation only —
 * the Sender Engine never produces a terminal decision.
 */
data class SenderResult(
    val senderRisk: Float,
    val senderConfidence: Float,
    val senderLabel: String,
    val senderInContacts: Boolean,
    val trustedSenderRecommendation: Boolean
)
