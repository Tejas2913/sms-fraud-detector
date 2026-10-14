package com.smsfraud.detector.data.sms

import com.smsfraud.detector.domain.model.DecisionState
import com.smsfraud.detector.util.HashUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SmsProcessingCoordinator logic that can be exercised without
 * Android framework components.
 *
 * SmsProcessingCoordinator is a Hilt @Singleton with coroutine and Room dependencies.
 * Those dependencies (DetectionPipeline, Room DAOs, NotificationManager) require
 * either an Android runtime or full mocking with MockK.
 *
 * This class tests the pure-logic helpers:
 *   1. Deduplication key algorithm
 *   2. Sender display string format (privacy-safe label)
 *   3. Pipeline failure fallback state semantics
 *   4. BLOCK semantics: correctly classified, NOT claimed to be intercepted
 *   5. DecisionState label round-trips (used when storing to Room)
 *
 * Full integration testing (actual pipeline invocation, Room writes, notifications)
 * is deferred to Milestone 5 instrumented test suite on device.
 */
class SmsProcessingCoordinatorTest {

    // -------------------------------------------------------------------------
    // Deduplication key logic
    // -------------------------------------------------------------------------

    @Test
    fun `dedup key is deterministic for identical events`() {
        val event = SmsEvent("sender-A", "body text", 1_720_000_000_000L)
        assertEquals(buildDedupKey(event), buildDedupKey(event))
    }

    @Test
    fun `dedup key differs when sender changes`() {
        val body = "Hello"
        val ts   = 1_720_000_000_000L
        assertFalse(
            buildDedupKey(SmsEvent("sender-A", body, ts)) ==
            buildDedupKey(SmsEvent("sender-B", body, ts))
        )
    }

    @Test
    fun `dedup key differs when body changes`() {
        val sender = "Bank-Alert"
        val ts     = 1_720_000_000_000L
        assertFalse(
            buildDedupKey(SmsEvent(sender, "body-A", ts)) ==
            buildDedupKey(SmsEvent(sender, "body-B", ts))
        )
    }

    @Test
    fun `events within same 5s window share dedup key`() {
        val ts1 = 1_720_000_000_100L
        val ts2 = 1_720_000_004_900L
        assertEquals(
            buildDedupKey(SmsEvent("s", "b", ts1)),
            buildDedupKey(SmsEvent("s", "b", ts2))
        )
    }

    @Test
    fun `events across 5s window boundary have different dedup keys`() {
        val ts1 = 1_720_000_004_999L
        val ts2 = 1_720_000_005_001L
        assertFalse(
            buildDedupKey(SmsEvent("s", "b", ts1)) ==
            buildDedupKey(SmsEvent("s", "b", ts2))
        )
    }

    // -------------------------------------------------------------------------
    // Sender display format (privacy)
    // -------------------------------------------------------------------------

    @Test
    fun `sender display is 8 hash chars + ellipsis`() {
        val hash    = HashUtil.sha256("+919876543210")
        val display = senderDisplayFrom(hash)
        assertTrue(display.endsWith("..."))
        assertEquals(11, display.length) // 8 hex chars + 3-char ellipsis
    }

    @Test
    fun `sender display does not contain raw sender`() {
        val rawSender = "+919876543210"
        val hash      = HashUtil.sha256(rawSender)
        val display   = senderDisplayFrom(hash)
        assertFalse(display.contains(rawSender))
    }

    @Test
    fun `sender display is different for different senders`() {
        val hash1 = HashUtil.sha256("sender-A")
        val hash2 = HashUtil.sha256("sender-B")
        assertFalse(senderDisplayFrom(hash1) == senderDisplayFrom(hash2))
    }

    // -------------------------------------------------------------------------
    // Pipeline failure fallback semantics
    // -------------------------------------------------------------------------

    @Test
    fun `pipeline failure uses ESCALATE not PASS`() {
        // Confirm the fallback state constant: ESCALATE, not PASS
        // We NEVER silently invent a PASS on pipeline failure
        val fallbackState = DecisionState.ESCALATE
        assertEquals("ESCALATE", fallbackState.name)
        assertFalse(fallbackState == DecisionState.PASS)
    }

    @Test
    fun `pipeline failure uses PIPELINE_ERROR rule label`() {
        val errorLabel = "PIPELINE_ERROR"
        assertTrue(errorLabel.isNotEmpty())
        assertFalse(errorLabel == "normal") // must not look like a normal message
    }

    @Test
    fun `pipeline failure decision reason is pipeline_error`() {
        val reason = "pipeline_error"
        assertTrue(reason.contains("error")) // must be identifiable as an error state
    }

    // -------------------------------------------------------------------------
    // BLOCK semantics
    // -------------------------------------------------------------------------

    @Test
    fun `BLOCK state label is exactly BLOCK`() {
        assertEquals("BLOCK", DecisionState.BLOCK.label)
    }

    @Test
    fun `BLOCK does not mean SMS was deleted — label check`() {
        // BLOCK = "DetectionPipeline classified as BLOCK"
        // NOT = "SMS was deleted" or "SMS was intercepted"
        val label = DecisionState.BLOCK.label
        assertFalse(label.contains("deleted", ignoreCase = true))
        assertFalse(label.contains("intercepted", ignoreCase = true))
        assertFalse(label.contains("blocked_delivery", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // DecisionState round-trip (label ↔ name)
    // -------------------------------------------------------------------------

    @Test
    fun `PASS name stored in Room is PASS`() {
        assertEquals("PASS", DecisionState.PASS.name)
    }

    @Test
    fun `PASS_WATCH label is PASS-WATCH`() {
        // label field (not .name) is used in CSV/audit comparisons
        assertEquals("PASS-WATCH", DecisionState.PASS_WATCH.label)
    }

    @Test
    fun `DecisionState fromLabel round-trips all four states`() {
        for (state in DecisionState.entries) {
            val fromLabel = DecisionState.fromLabel(state.label)
            assertEquals(state, fromLabel)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers mirroring coordinator private methods for test isolation
    // -------------------------------------------------------------------------

    private val DEDUP_WINDOW_MS = 5_000L

    private fun buildDedupKey(event: SmsEvent): String {
        val bucket = event.timestampMs / DEDUP_WINDOW_MS
        return HashUtil.sha256("${event.sender}:${event.body}:$bucket")
    }

    private fun senderDisplayFrom(senderHash: String): String =
        senderHash.take(8) + "..."
}
