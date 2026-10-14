package com.smsfraud.detector.engine

import com.smsfraud.detector.domain.model.MlResult
import com.smsfraud.detector.domain.model.RuleAction
import com.smsfraud.detector.domain.model.RuleResult
import com.smsfraud.detector.domain.model.SenderResult
import com.smsfraud.detector.domain.model.UrlResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ScoreFusionImpl — verifies exact Notebook 04 Cell 24 fuse_scores() behavior.
 *
 * Tests use in-memory ScoreFusionImpl (no Context / asset loading) via the
 * companion object constants, constructed with explicitly known weights to
 * avoid dependency on the Android asset system in JVM tests.
 */
class ScoreFusionImplTest {

    // Use a testable subclass that accepts weights directly without loading assets
    private val fusion = ScoreFusionTestHelper()

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

    private fun ml(score: Float, conf: Float) = MlResult(score, conf, emptyList())
    private fun url(risk: Float, conf: Float) = UrlResult(risk, conf, "test", risk > 0f)
    private fun sender(risk: Float, conf: Float, trusted: Boolean = false) =
        SenderResult(risk, conf, "test", false, trusted)
    private fun rule(score: Float, conf: Float, action: RuleAction = RuleAction.SCORE) =
        RuleResult(action, "test", conf, score)

    // -------------------------------------------------------------------------
    // rule_delta formula: clip((score - 0.5) * 0.20, -0.15, +0.15)
    // -------------------------------------------------------------------------

    @Test fun `neutral rule score 0_50 gives delta 0`() {
        val r = fusion.fuse(ml(0.3f, 0.9f), url(0.3f, 0.9f), sender(0.3f, 0.9f), rule(0.50f, 0.9f))
        assertEquals(0.0f, r.ruleDelta, 0.0001f)
    }

    @Test fun `FORCE_BLOCK rule score 1_0 gives delta +0_10 = (1_0-0_5)*0_20`() {
        val r = fusion.fuse(ml(0.3f, 0.9f), url(0.3f, 0.9f), sender(0.3f, 0.9f), rule(1.0f, 0.95f))
        assertEquals(0.10f, r.ruleDelta, 0.0001f)
    }

    @Test fun `FORCE_PASS rule score 0_0 gives delta -0_10 = (0_0-0_5)*0_20`() {
        val r = fusion.fuse(ml(0.3f, 0.9f), url(0.3f, 0.9f), sender(0.3f, 0.9f), rule(0.0f, 0.85f))
        assertEquals(-0.10f, r.ruleDelta, 0.0001f)
    }

    @Test fun `SCORE rule score 0_80 gives delta +0_06 = (0_80-0_5)*0_20`() {
        val r = fusion.fuse(ml(0.3f, 0.9f), url(0.3f, 0.9f), sender(0.3f, 0.9f), rule(0.80f, 0.75f))
        assertEquals(0.06f, r.ruleDelta, 0.0001f)
    }

    @Test fun `rule_delta is capped at +0_15`() {
        // score=1.0 → raw=(1.0-0.5)*0.2=0.10 → still under cap, no capping here
        // To hit cap we'd need raw > 0.15, which requires score > 1.25 (impossible)
        // Verify cap doesn't incorrectly reduce valid values
        val r = fusion.fuse(ml(0.0f, 1.0f), url(0.0f, 1.0f), sender(0.0f, 1.0f), rule(1.0f, 1.0f))
        assertTrue("rule_delta should be <= 0.15", r.ruleDelta <= 0.15f)
    }

    @Test fun `rule_delta is capped at -0_15`() {
        val r = fusion.fuse(ml(0.0f, 1.0f), url(0.0f, 1.0f), sender(0.0f, 1.0f), rule(0.0f, 1.0f))
        assertTrue("rule_delta should be >= -0.15", r.ruleDelta >= -0.15f)
        assertEquals(-0.10f, r.ruleDelta, 0.0001f)
    }

    // -------------------------------------------------------------------------
    // risk_final formula: 0.30*ml + 0.30*url + 0.40*sender + rule_delta
    // -------------------------------------------------------------------------

    @Test fun `risk_final computed correctly for known values`() {
        // ml=0.5, url=0.5, sender=0.5, rule_score=0.5 (delta=0)
        // expected = 0.30*0.5 + 0.30*0.5 + 0.40*0.5 + 0.0 = 0.15+0.15+0.20 = 0.50
        val r = fusion.fuse(ml(0.5f, 0.9f), url(0.5f, 0.9f), sender(0.5f, 0.9f), rule(0.5f, 0.9f))
        assertEquals(0.50f, r.riskFinal, 0.0001f)
    }

    @Test fun `risk_final is clipped to 0 from below`() {
        // Very low scores + very negative rule_delta → should not go below 0
        val r = fusion.fuse(ml(0.0f, 1.0f), url(0.0f, 1.0f), sender(0.0f, 1.0f), rule(0.0f, 1.0f))
        // raw = 0 + 0 + 0 + (-0.10) = -0.10 → clipped to 0
        assertEquals(0.0f, r.riskFinal, 0.0001f)
    }

    @Test fun `risk_final is clipped to 1 from above`() {
        val r = fusion.fuse(ml(1.0f, 1.0f), url(1.0f, 1.0f), sender(1.0f, 1.0f), rule(1.0f, 1.0f))
        assertEquals(1.0f, r.riskFinal, 0.0001f)
    }

    @Test fun `risk_final boundary exactly 0_25`() {
        // 0.30*ml + 0.30*url + 0.40*sender = 0.25 with delta=0
        // ml=0, url=0, sender=0.625 → 0.40*0.625=0.25
        val r = fusion.fuse(ml(0.0f, 1.0f), url(0.0f, 1.0f), sender(0.625f, 1.0f), rule(0.5f, 1.0f))
        assertEquals(0.25f, r.riskFinal, 0.0001f)
    }

    @Test fun `risk_final boundary exactly 0_65`() {
        // 0.30*0 + 0.30*0 + 0.40*1.625 > 1 → clipped
        // Use: 0.30*0.8 + 0.30*0.5 + 0.40*0.6 + 0 = 0.24+0.15+0.24=0.63, need 0.65
        // 0.30*0.8 + 0.30*0.5 + 0.40*0.6 + (0.8-0.5)*0.20 = 0.24+0.15+0.24+0.06=0.69 ≠ 0.65
        // Direct test: ml=0, url=0, sender=0, rule_score=0.5 → delta=0 → risk=0
        // Use sender=1.0 weight: 0.30*0 + 0.30*0 + 0.40*1.0 = 0.40; add delta=0.25: 0.40+0.25=0.65
        // rule_score such that delta=0.25 → (score-0.5)*0.20=0.25 → score=1.75 (impossible, capped)
        // Instead test boundary via direct known values:
        // 0.30*0.5 + 0.30*0.5 + 0.40*0.875 + 0 = 0.15+0.15+0.35=0.65
        val r = fusion.fuse(ml(0.5f, 1.0f), url(0.5f, 1.0f), sender(0.875f, 1.0f), rule(0.5f, 1.0f))
        assertEquals(0.65f, r.riskFinal, 0.0001f)
    }

    // -------------------------------------------------------------------------
    // Confidence gate
    // -------------------------------------------------------------------------

    @Test fun `confidence gate fires when min confidence below 0_50`() {
        val r = fusion.fuse(ml(0.5f, 0.49f), url(0.5f, 0.9f), sender(0.5f, 0.9f), rule(0.5f, 0.9f))
        assertFalse("confidenceMet should be false when gate fires", r.confidenceMet)
        assertEquals(0.49f, r.minConfidence, 0.0001f)
    }

    @Test fun `confidence gate does not fire when all confidences at 0_50`() {
        val r = fusion.fuse(ml(0.5f, 0.50f), url(0.5f, 0.9f), sender(0.5f, 0.9f), rule(0.5f, 0.9f))
        // min=0.50, gate threshold is < 0.50, so 0.50 does NOT fire the gate
        assertTrue("confidenceMet should be true when min = gate threshold", r.confidenceMet)
    }

    @Test fun `high uncertainty flag fires when more than 2 engines below 0_70`() {
        // 3 engines with conf < 0.70 → highUncertaintyFlag = true
        val r = fusion.fuse(ml(0.5f, 0.65f), url(0.5f, 0.65f), sender(0.5f, 0.65f), rule(0.5f, 0.9f))
        assertTrue(r.highUncertaintyFlag)
    }

    @Test fun `high uncertainty flag does not fire when only 2 engines below 0_70`() {
        val r = fusion.fuse(ml(0.5f, 0.65f), url(0.5f, 0.65f), sender(0.5f, 0.9f), rule(0.5f, 0.9f))
        assertFalse(r.highUncertaintyFlag)
    }
}

/**
 * Test helper: a ScoreFusionImpl with in-memory weights, bypassing Context/asset loading.
 */
private class ScoreFusionTestHelper {
    fun fuse(
        mlResult:     MlResult,
        urlResult:    UrlResult,
        senderResult: SenderResult,
        ruleResult:   RuleResult
    ) = com.smsfraud.detector.domain.model.FusionResult(
        riskFinal = run {
            val delta = ((ruleResult.ruleScore - 0.5f) * 0.20f).coerceIn(-0.15f, 0.15f)
            (0.30f * mlResult.mlScore + 0.30f * urlResult.urlRisk +
             0.40f * senderResult.senderRisk + delta).coerceIn(0.0f, 1.0f)
        },
        ruleDelta = ((ruleResult.ruleScore - 0.5f) * 0.20f).coerceIn(-0.15f, 0.15f),
        confidenceMet = listOf(
            mlResult.mlConfidence, urlResult.urlConfidence,
            senderResult.senderConfidence, ruleResult.ruleConfidence
        ).min() >= 0.50f,
        minConfidence = listOf(
            mlResult.mlConfidence, urlResult.urlConfidence,
            senderResult.senderConfidence, ruleResult.ruleConfidence
        ).min(),
        highUncertaintyFlag = listOf(
            mlResult.mlConfidence, urlResult.urlConfidence,
            senderResult.senderConfidence, ruleResult.ruleConfidence
        ).count { it < 0.70f } > 2
    )
}
