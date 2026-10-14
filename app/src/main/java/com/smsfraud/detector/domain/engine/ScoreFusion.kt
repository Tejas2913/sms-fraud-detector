package com.smsfraud.detector.domain.engine

import com.smsfraud.detector.domain.model.FusionResult
import com.smsfraud.detector.domain.model.MlResult
import com.smsfraud.detector.domain.model.RuleResult
import com.smsfraud.detector.domain.model.SenderResult
import com.smsfraud.detector.domain.model.UrlResult

/**
 * Score Fusion interface per Architecture v2 Layer 3.
 * Applies the weighted formula + rule_delta + confidence gate.
 * Implementation in Milestone 2.
 */
interface ScoreFusion {
    fun fuse(
        mlResult: MlResult,
        urlResult: UrlResult,
        senderResult: SenderResult,
        ruleResult: RuleResult
    ): FusionResult
}
