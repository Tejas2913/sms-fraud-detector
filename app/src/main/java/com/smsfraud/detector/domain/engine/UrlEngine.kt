package com.smsfraud.detector.domain.engine

import com.smsfraud.detector.domain.model.UrlEntry
import com.smsfraud.detector.domain.model.UrlResult

/**
 * URL Intelligence Engine interface per Architecture v2 Layer 2C.
 * Receives structured UrlEntry objects (extracted during preprocessing).
 * Pure Kotlin/Java, no ML dependency.
 * Implementation in Milestone 2.
 */
interface UrlEngine {
    fun evaluate(urlList: List<UrlEntry>): UrlResult
}
