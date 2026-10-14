package com.smsfraud.detector.domain.engine

import com.smsfraud.detector.domain.model.MlResult

/**
 * ML Engine interface per Architecture v2 Layer 2B.
 * Same contract for mock and real TFLite implementations.
 * Implementation (mock) in Milestone 2, real in Milestone 8.
 */
interface MlEngine {
    fun predict(normalisedText: String): MlResult
}
