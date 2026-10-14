package com.smsfraud.detector.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for MlEngineImpl.mlConfidenceFromProba() — the NB04 Cell 21 formula.
 *
 * This is a pure function test with no Android Context dependency.
 * Formula: threshold = 0.25
 *   proba <= threshold: confidence = (threshold - proba) / threshold
 *   proba >  threshold: confidence = (proba - threshold) / (1.0 - threshold)
 */
class MlEngineConfidenceTest {

    private val tol = 1e-4f
    private val threshold = MlEngineImpl.OPERATING_THRESHOLD  // 0.25

    @Test fun `proba at threshold 0_25 gives confidence 0`() {
        val c = MlEngineImpl.mlConfidenceFromProba(0.25f)
        assertEquals(0.0f, c, tol)
    }

    @Test fun `proba 0 gives confidence 1 on ham side`() {
        // (0.25 - 0.0) / 0.25 = 1.0
        val c = MlEngineImpl.mlConfidenceFromProba(0.0f)
        assertEquals(1.0f, c, tol)
    }

    @Test fun `proba 1 gives confidence 1 on fraud side`() {
        // (1.0 - 0.25) / (1.0 - 0.25) = 0.75 / 0.75 = 1.0
        val c = MlEngineImpl.mlConfidenceFromProba(1.0f)
        assertEquals(1.0f, c, tol)
    }

    @Test fun `proba 0_125 gives confidence 0_5 on ham side`() {
        // (0.25 - 0.125) / 0.25 = 0.125 / 0.25 = 0.5
        val c = MlEngineImpl.mlConfidenceFromProba(0.125f)
        assertEquals(0.5f, c, tol)
    }

    @Test fun `proba 0_625 gives confidence 0_5 on fraud side`() {
        // (0.625 - 0.25) / (1.0 - 0.25) = 0.375 / 0.75 = 0.5
        val c = MlEngineImpl.mlConfidenceFromProba(0.625f)
        assertEquals(0.5f, c, tol)
    }

    @Test fun `confidence is always in range 0 to 1`() {
        val testProbas = listOf(0f, 0.1f, 0.2f, 0.25f, 0.3f, 0.5f, 0.75f, 0.9f, 1.0f)
        for (p in testProbas) {
            val c = MlEngineImpl.mlConfidenceFromProba(p)
            assertTrue("proba=$p gave confidence=$c outside [0,1]", c in 0f..1f)
        }
    }

    @Test fun `confidence is NOT anchored on 0_5 proba`() {
        // The old buggy formula used *2 anchored on 0.5.
        // At proba=0.5: old formula = (0.5 - 0.5) * 2 = 0 (WRONG)
        // New formula: (0.5 - 0.25) / (1.0 - 0.25) = 0.333 (CORRECT)
        val c = MlEngineImpl.mlConfidenceFromProba(0.5f)
        assertTrue("At proba=0.5, confidence should be ~0.333, not 0", abs(c - 0.333f) < 0.01f)
    }
}
