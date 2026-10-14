package com.smsfraud.detector.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the updated DecisionState enum.
 * Verifies all 4 frozen research states exist with correct external labels.
 */
class DecisionStateTest {

    @Test
    fun `all four frozen research states are present`() {
        val states = DecisionState.entries.map { it.name }
        assert("PASS"       in states) { "PASS missing" }
        assert("PASS_WATCH" in states) { "PASS_WATCH missing" }
        assert("ESCALATE"   in states) { "ESCALATE missing" }
        assert("BLOCK"      in states) { "BLOCK missing" }
        assertEquals(4, states.size)
    }

    @Test
    fun `PASS_WATCH external label is PASS-WATCH`() {
        assertEquals("PASS-WATCH", DecisionState.PASS_WATCH.label)
    }

    @Test
    fun `other states have matching external labels`() {
        assertEquals("PASS",     DecisionState.PASS.label)
        assertEquals("ESCALATE", DecisionState.ESCALATE.label)
        assertEquals("BLOCK",    DecisionState.BLOCK.label)
    }

    @Test
    fun `fromLabel round-trips all four states`() {
        for (state in DecisionState.entries) {
            assertEquals(state, DecisionState.fromLabel(state.label))
        }
    }

    @Test
    fun `fromLabel parses PASS-WATCH string correctly`() {
        assertEquals(DecisionState.PASS_WATCH, DecisionState.fromLabel("PASS-WATCH"))
    }
}
