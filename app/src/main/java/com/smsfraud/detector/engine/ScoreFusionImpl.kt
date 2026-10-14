package com.smsfraud.detector.engine

import android.content.Context
import com.smsfraud.detector.domain.engine.ScoreFusion
import com.smsfraud.detector.domain.model.FusionResult
import com.smsfraud.detector.domain.model.MlResult
import com.smsfraud.detector.domain.model.RuleResult
import com.smsfraud.detector.domain.model.SenderResult
import com.smsfraud.detector.domain.model.UrlResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Score Fusion — exact implementation of Notebook 04 Cell 24 fuse_scores().
 *
 * FROZEN FORMULA (from fusion_config.json + NB04):
 *
 *   rule_delta = clip((rule_score - 0.5) * 0.20, -0.15, +0.15)
 *
 *   risk_final = clip(
 *       0.30 * ml_score
 *     + 0.30 * url_risk
 *     + 0.40 * sender_risk
 *     + rule_delta,
 *     0.0, 1.0
 *   )
 *
 *   confidence_gate_fired = min(all four confidences) < 0.50
 *   high_uncertainty_flag = count(conf < 0.70) > 2
 *
 * Weights and thresholds are loaded from fusion_config.json at construction time.
 * Hard-coded fallbacks are provided in case the asset is unavailable (should never happen).
 *
 * IMPORTANT: Do NOT modify weights or threshold constants in code.
 * The single source of truth is fusion_config.json.
 */
@Singleton
class ScoreFusionImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ScoreFusion {

    // -----------------------------------------------------------------------
    // Configuration loaded from fusion_config.json
    // -----------------------------------------------------------------------
    private val weightMl:     Float
    private val weightUrl:    Float
    private val weightSender: Float
    private val confidenceGateThreshold: Float
    private val ruleDeltaCap: Float

    init {
        val parsed = loadFusionConfig(context)
        weightMl     = parsed.weightMl
        weightUrl    = parsed.weightUrl
        weightSender = parsed.weightSender
        confidenceGateThreshold = parsed.confidenceGateThreshold
        ruleDeltaCap = parsed.ruleDeltaCap
    }

    // -----------------------------------------------------------------------
    // ScoreFusion interface
    // -----------------------------------------------------------------------

    override fun fuse(
        mlResult:     MlResult,
        urlResult:    UrlResult,
        senderResult: SenderResult,
        ruleResult:   RuleResult
    ): FusionResult {
        // Step 1: Compute rule_delta — bounded signed adjustment
        val rawDelta  = (ruleResult.ruleScore - 0.5f) * 0.20f
        val ruleDelta = rawDelta.coerceIn(-ruleDeltaCap, +ruleDeltaCap)

        // Step 2: Weighted sum + rule_delta, clipped to [0, 1]
        val rawRisk  = weightMl     * mlResult.mlScore +
                       weightUrl    * urlResult.urlRisk +
                       weightSender * senderResult.senderRisk +
                       ruleDelta
        val riskFinal = rawRisk.coerceIn(0.0f, 1.0f)

        // Step 3: Confidence gate — min of all four engine confidences
        val confidences = listOf(
            mlResult.mlConfidence,
            urlResult.urlConfidence,
            senderResult.senderConfidence,
            ruleResult.ruleConfidence
        )
        val minConfidence        = confidences.min()
        val gateThreshold        = confidenceGateThreshold
        val confidenceGateFired  = minConfidence < gateThreshold
        val highUncertaintyFlag  = confidences.count { it < 0.70f } > 2

        return FusionResult(
            riskFinal            = riskFinal,
            ruleDelta            = ruleDelta,
            confidenceMet        = !confidenceGateFired,
            minConfidence        = minConfidence,
            highUncertaintyFlag  = highUncertaintyFlag
        )
    }

    // -----------------------------------------------------------------------
    // Config loading
    // -----------------------------------------------------------------------

    private data class FusionConfig(
        val weightMl: Float,
        val weightUrl: Float,
        val weightSender: Float,
        val confidenceGateThreshold: Float,
        val ruleDeltaCap: Float
    )

    private fun loadFusionConfig(context: Context): FusionConfig {
        return try {
            val text = context.assets.open("fusion_config.json")
                .bufferedReader().readText()
            val root = json.parseToJsonElement(text).jsonObject
            val weights = root["weights"]!!.jsonObject
            FusionConfig(
                weightMl     = weights["ml"]!!.jsonPrimitive.float,
                weightUrl    = weights["url"]!!.jsonPrimitive.float,
                weightSender = weights["sender"]!!.jsonPrimitive.float,
                confidenceGateThreshold = root["confidence_gate_threshold"]!!.jsonPrimitive.float,
                ruleDeltaCap = root["rule_delta_cap"]!!.jsonPrimitive.float
            )
        } catch (e: Exception) {
            // Frozen fallback — exact values from fusion_config.json
            FusionConfig(
                weightMl     = 0.30f,
                weightUrl    = 0.30f,
                weightSender = 0.40f,
                confidenceGateThreshold = 0.50f,
                ruleDeltaCap = 0.15f
            )
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
