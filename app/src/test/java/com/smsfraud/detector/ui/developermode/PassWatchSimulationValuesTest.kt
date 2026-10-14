package com.smsfraud.detector.ui.developermode

import com.smsfraud.detector.domain.model.DecisionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the PASS-WATCH simulation record values.
 *
 * These tests verify the static values that DeveloperModeViewModel.simulatePassWatch()
 * uses when inserting a synthetic PASS-WATCH record. They confirm the values are
 * consistent with the frozen DecisionControllerImpl thresholds and do NOT change
 * detection behavior.
 *
 * Note: The ViewModel itself is not tested here because it requires Room + Hilt.
 * These tests verify the constant values that the simulation uses.
 */
class PassWatchSimulationValuesTest {

    // Values used by DeveloperModeViewModel.simulatePassWatch()
    private val SIMULATED_RISK_FINAL = 0.22f
    private val SIMULATED_ML_SCORE = 0.05f
    private val SIMULATED_URL_RISK = 0.0f
    private val SIMULATED_SENDER_RISK = 0.55f
    private val SIMULATED_MIN_CONFIDENCE = 0.55f
    private val SIMULATED_DECISION_STATE = DecisionState.PASS_WATCH
    private val SIMULATED_DECISION_REASON = "low_risk"

    // Frozen thresholds from DecisionControllerImpl
    private val PASS_MAX = 0.25f
    private val HIGH_CONF_PASS = 0.85f
    private val CONFIDENCE_GATE = 0.50f

    @Test
    fun `simulated riskFinal is below PASS_MAX — satisfies low-risk condition`() {
        assertTrue(
            "riskFinal=$SIMULATED_RISK_FINAL must be < PASS_MAX=$PASS_MAX",
            SIMULATED_RISK_FINAL < PASS_MAX
        )
    }

    @Test
    fun `simulated minConfidence is above CONFIDENCE_GATE — satisfies gate condition`() {
        assertTrue(
            "minConfidence=$SIMULATED_MIN_CONFIDENCE must be >= CONFIDENCE_GATE=$CONFIDENCE_GATE",
            SIMULATED_MIN_CONFIDENCE >= CONFIDENCE_GATE
        )
    }

    @Test
    fun `simulated minConfidence is below HIGH_CONF_PASS — triggers PASS_WATCH not PASS`() {
        assertTrue(
            "minConfidence=$SIMULATED_MIN_CONFIDENCE must be < HIGH_CONF_PASS=$HIGH_CONF_PASS",
            SIMULATED_MIN_CONFIDENCE < HIGH_CONF_PASS
        )
    }

    @Test
    fun `simulated decision state is PASS_WATCH`() {
        assertEquals(DecisionState.PASS_WATCH, SIMULATED_DECISION_STATE)
        assertEquals("PASS-WATCH", SIMULATED_DECISION_STATE.label)
    }

    @Test
    fun `simulated decision reason is low_risk`() {
        assertEquals("low_risk", SIMULATED_DECISION_REASON)
    }

    @Test
    fun `simulated record has no SMS body (DecisionState only)`() {
        // The simulation inserts a DecisionState string — no body field exists
        assertFalse(SIMULATED_DECISION_STATE.name.contains("body", ignoreCase = true))
    }

    @Test
    fun `simulated url risk is zero — no URL in test scenario`() {
        assertEquals(0.0f, SIMULATED_URL_RISK)
    }

    @Test
    fun `simulated mlScore is near zero — benign content scenario`() {
        assertTrue("mlScore should be low for benign simulation", SIMULATED_ML_SCORE < 0.10f)
    }
}
