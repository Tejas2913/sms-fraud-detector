package com.smsfraud.detector.domain.model

/**
 * URL Engine output per Architecture v2 Layer 2C.
 * url_present is an explicit boolean — NOT inferred from url_risk == 0.0.
 */
data class UrlResult(
    val urlRisk: Float,
    val urlConfidence: Float,
    val urlLabel: String,
    val urlPresent: Boolean
)
