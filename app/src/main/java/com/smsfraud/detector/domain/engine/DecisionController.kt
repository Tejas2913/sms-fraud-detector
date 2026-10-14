package com.smsfraud.detector.domain.engine

import com.smsfraud.detector.domain.model.DecisionEvent
import com.smsfraud.detector.domain.model.FusionResult
import com.smsfraud.detector.domain.model.MlResult
import com.smsfraud.detector.domain.model.RuleResult
import com.smsfraud.detector.domain.model.SenderResult
import com.smsfraud.detector.domain.model.UrlResult

/**
 * Decision Controller interface per Architecture v2 Layer 4.
 * Sole cross-engine resolution authority.
 * No Android UI dependency. No notification construction.
 * Implementation in Milestone 2.
 */
interface DecisionController {
    suspend fun decide(
        ruleResult: RuleResult,
        fusionResult: FusionResult,
        senderResult: SenderResult,
        urlResult: UrlResult,
        mlResult: MlResult,
        messageRefId: String
    ): DecisionEvent
}
