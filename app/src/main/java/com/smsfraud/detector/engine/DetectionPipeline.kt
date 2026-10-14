package com.smsfraud.detector.engine

import com.smsfraud.detector.domain.model.DecisionEvent
import com.smsfraud.detector.domain.model.FusionResult
import com.smsfraud.detector.domain.model.MlResult
import com.smsfraud.detector.domain.model.RuleResult
import com.smsfraud.detector.domain.model.SenderResult
import com.smsfraud.detector.domain.model.UrlResult
import com.smsfraud.detector.preprocessing.TextPreprocessor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * End-to-end detection pipeline orchestrator.
 *
 * Mirrors the Notebook 04 Cell 26 run_hybrid_pipeline() function.
 *
 * Pipeline order:
 *   1. TextPreprocessor.preprocess(rawMessage)       → messageClean
 *   2. UrlExtractor.extract(rawMessage)              → urlList (from RAW message)
 *   3. RuleEngineImpl.score(messageClean)            → ruleResult
 *   4. MlEngineImpl.predict(messageClean)            → mlResult
 *   5. UrlEngineImpl.score(urlList, urlPresent)      → urlResult
 *   6. SenderContextEngineImpl.evaluate(sender)      → senderResult
 *   7. ScoreFusionImpl.fuse(ml, url, sender, rule)   → fusionResult
 *   8. DecisionControllerImpl.cascade(...)           → (state, reason) → DecisionEvent
 *
 * IMPORTANT: URL extraction happens on the RAW message (before preprocessing),
 * because preprocessing replaces URLs with [URL] tokens.
 *
 * This class is also the parity testing interface.
 * Use [runWithMetadata] for exact Python-parity comparison (sender metadata override).
 */
@Singleton
class DetectionPipeline @Inject constructor(
    private val textPreprocessor:   TextPreprocessor,
    private val ruleEngine:         RuleEngineImpl,
    private val mlEngine:           MlEngineImpl,
    private val urlEngine:          UrlEngineImpl,
    private val senderEngine:       SenderContextEngineImpl,
    private val scoreFusion:        ScoreFusionImpl,
    private val decisionController: DecisionControllerImpl
) {

    /**
     * Standard on-device pipeline invocation.
     *
     * @param rawMessage   The raw SMS message text (unprocessed)
     * @param senderString The sender ID string (e.g. "+919876543210" or "HDFC-OTP")
     * @param messageRefId Opaque ID for audit trail linkage
     */
    suspend fun run(
        rawMessage:   String,
        senderString: String,
        messageRefId: String
    ): PipelineResult {
        val messageClean = textPreprocessor.preprocess(rawMessage)
        android.util.Log.d(TAG, "PREPROCESSING OK")

        val urlList      = UrlExtractor.extract(rawMessage)
        val urlPresent   = urlList.isNotEmpty()

        val ruleResult   = ruleEngine.score(messageClean)
        android.util.Log.d(TAG, "RULE OK")

        val mlResult     = mlEngine.predict(messageClean)
        // mlEngine logs TOKENIZATION OK and ML OK internally

        val urlResult    = urlEngine.score(urlList.map { it }, urlPresent)
        android.util.Log.d(TAG, "URL OK")

        val senderResult = senderEngine.evaluate(senderString)
        android.util.Log.d(TAG, "SENDER OK")

        val fusionResult = scoreFusion.fuse(mlResult, urlResult, senderResult, ruleResult)
        android.util.Log.d(TAG, "FUSION OK")

        val (state, reason) = decisionController.cascade(ruleResult, fusionResult, senderResult, urlPresent)
        android.util.Log.d(TAG, "DECISION OK")

        val decisionEvent = DecisionEvent(
            messageRefId   = messageRefId,
            decisionState  = state,
            riskFinal      = fusionResult.riskFinal,
            decisionReason = reason
        )

        return PipelineResult(
            rawMessage    = rawMessage,
            messageClean  = messageClean,
            urlList       = urlList,
            ruleResult    = ruleResult,
            mlResult      = mlResult,
            urlResult     = urlResult,
            senderResult  = senderResult,
            fusionResult  = fusionResult,
            decisionEvent = decisionEvent
        )
    }

    /**
     * Parity testing pipeline invocation.
     *
     * Accepts explicit NB04-style metadata (source, ruleCategory) for
     * deterministic sender simulation, matching Python research outputs exactly.
     *
     * @param rawMessage   Raw SMS message text
     * @param source       NB04 source field: "synthetic" | "real"
     * @param ruleCategory NB04 rule_category field: "otp" | "banking" | "normal" | etc.
     * @param messageRefId Audit trail ID
     */
    suspend fun runWithMetadata(
        rawMessage:   String,
        source:       String,
        ruleCategory: String,
        messageRefId: String = "parity-test"
    ): PipelineResult {
        val messageClean = textPreprocessor.preprocess(rawMessage)
        val urlList      = UrlExtractor.extract(rawMessage)
        val urlPresent   = urlList.isNotEmpty()

        val ruleResult   = ruleEngine.score(messageClean)
        val mlResult     = mlEngine.predict(messageClean)
        val urlResult    = urlEngine.score(urlList, urlPresent)
        val senderResult = senderEngine.scoreFromMetadata(source, ruleCategory)

        val fusionResult = scoreFusion.fuse(mlResult, urlResult, senderResult, ruleResult)
        val (state, reason) = decisionController.cascade(ruleResult, fusionResult, senderResult, urlPresent)

        val decisionEvent = DecisionEvent(
            messageRefId   = messageRefId,
            decisionState  = state,
            riskFinal      = fusionResult.riskFinal,
            decisionReason = reason
        )

        return PipelineResult(
            rawMessage    = rawMessage,
            messageClean  = messageClean,
            urlList       = urlList,
            ruleResult    = ruleResult,
            mlResult      = mlResult,
            urlResult     = urlResult,
            senderResult  = senderResult,
            fusionResult  = fusionResult,
            decisionEvent = decisionEvent
        )
    }

    companion object {
        private const val TAG = "DetectionPipeline"
    }
}

/**
 * Complete pipeline output — all intermediate and final values.
 *
 * This is the parity test interface: every field maps to a named output
 * in Notebook 04's run_hybrid_pipeline() return dict.
 */
data class PipelineResult(
    val rawMessage:    String,
    val messageClean:  String,
    val urlList:       List<String>,

    // Engine outputs
    val ruleResult:    RuleResult,
    val mlResult:      MlResult,
    val urlResult:     UrlResult,
    val senderResult:  SenderResult,

    // Fusion
    val fusionResult:  FusionResult,

    // Final decision
    val decisionEvent: DecisionEvent
) {
    /** Convenience: rule_score as float */
    val ruleScore: Float    get() = ruleResult.ruleScore
    /** Convenience: rule_delta as float */
    val ruleDelta: Float    get() = fusionResult.ruleDelta
    /** Convenience: url_risk as float */
    val urlRisk: Float      get() = urlResult.urlRisk
    /** Convenience: sender_risk as float */
    val senderRisk: Float   get() = senderResult.senderRisk
    /** Convenience: ml_score (fraud probability) */
    val mlScore: Float      get() = mlResult.mlScore
    /** Convenience: ml_confidence */
    val mlConfidence: Float get() = mlResult.mlConfidence
    /** Convenience: risk_final */
    val riskFinal: Float    get() = fusionResult.riskFinal
    /** Convenience: decision label matching NB04 string (PASS, PASS-WATCH, etc.) */
    val decision: String    get() = decisionEvent.decisionState.label
}
