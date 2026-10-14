package com.smsfraud.detector.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for softmaxFraud() — the package-internal softmax helper used by OnnxRunner.
 *
 * Verifies numerically-stable softmax behaviour against known hand-computed values.
 * These results must match:
 *   scipy.special.softmax([logit_ham, logit_fraud])[1]
 */
class SoftmaxTest {

    private val tol = 1e-5f

    private fun assertNearlyEqual(expected: Float, actual: Float, message: String = "") {
        assertTrue("$message: |expected $expected − actual $actual| > tol $tol",
            abs(expected - actual) < tol)
    }

    @Test
    fun `equal logits produce probability 0_5`() {
        val prob = softmaxFraud(1.0f, 1.0f)
        assertNearlyEqual(0.5f, prob)
    }

    @Test
    fun `very large fraud logit approaches 1`() {
        val prob = softmaxFraud(-10f, 10f)
        assertTrue("Expected prob near 1, got $prob", prob > 0.9999f)
    }

    @Test
    fun `very large ham logit approaches 0`() {
        val prob = softmaxFraud(10f, -10f)
        assertTrue("Expected prob near 0, got $prob", prob < 0.0001f)
    }

    @Test
    fun `hand-computed value for logits 0_3 and 0_9`() {
        // Python: scipy.special.softmax([0.3, 0.9])[1] = 0.6456563...
        val prob = softmaxFraud(0.3f, 0.9f)
        assertNearlyEqual(0.64565f, prob, "logits [0.3, 0.9]")
    }

    @Test
    fun `negative logits still produce valid probability in 0 to 1`() {
        val prob = softmaxFraud(-2.5f, -0.8f)
        assertTrue("Probability must be in [0,1]: got $prob", prob in 0f..1f)
    }

    @Test
    fun `result is always in range 0 to 1`() {
        val cases = listOf(
            Pair(-100f, 100f),
            Pair(100f, -100f),
            Pair(0f, 0f),
            Pair(1.23f, -0.45f)
        )
        for ((h, f) in cases) {
            val prob = softmaxFraud(h, f)
            assertTrue("softmaxFraud($h,$f)=$prob not in [0,1]", prob in 0f..1f)
        }
    }

    @Test
    fun `output matches OnnxInferenceResult fraud probability field`() {
        val logitHam   = 0.3f
        val logitFraud = 1.7f
        val expected   = softmaxFraud(logitHam, logitFraud)
        val result = OnnxInferenceResult(
            variant = OnnxModelVariant.FP32,
            logits = floatArrayOf(logitHam, logitFraud),
            fraudProbability = expected,
            inferenceMs = 0L
        )
        assertEquals(expected, result.fraudProbability, tol)
        assertEquals(logitHam,   result.logits[0], tol)
        assertEquals(logitFraud, result.logits[1], tol)
    }
}
