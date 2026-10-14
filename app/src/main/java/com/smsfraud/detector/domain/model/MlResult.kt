package com.smsfraud.detector.domain.model

/**
 * ML Engine output per Architecture v2 Layer 2B.
 * Attention weights are a byproduct of inference — not a separate model.
 */
data class MlResult(
    val mlScore: Float,
    val mlConfidence: Float,
    val attentionWeights: List<Float>
)
