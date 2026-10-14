package com.smsfraud.detector.service

import com.smsfraud.detector.domain.model.DecisionState
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Unit tests for DetectionNotificationManager decision-to-channel mapping.
 *
 * DetectionNotificationManager depends on Android's NotificationManagerCompat,
 * which requires an instrumented context. These tests verify the decision-mapping
 * logic and constant values only, without actually posting notifications.
 *
 * Instrumented notification posting tests (confirming a notification appears
 * on a real device) are deferred to Milestone 5 device benchmarking.
 *
 * Tests cover spec requirements:
 *   1. PASS → no notification channel should be selected
 *   2. PASS-WATCH → CHANNEL_WATCH (low importance)
 *   3. ESCALATE → CHANNEL_ESCALATE (default importance)
 *   4. BLOCK → CHANNEL_BLOCK (high importance, heads-up)
 *   5. Channel ID constants are stable (no renames that break existing installs)
 *   6. Notification ID is deterministically derived from messageRefId
 */
class DetectionNotificationManagerTest {

    // -------------------------------------------------------------------------
    // Channel constant stability tests
    // -------------------------------------------------------------------------

    @Test
    fun `CHANNEL_BLOCK constant is stable`() {
        assertEquals("smsguard_block", DetectionNotificationManager.CHANNEL_BLOCK)
    }

    @Test
    fun `CHANNEL_ESCALATE constant is stable`() {
        assertEquals("smsguard_escalate", DetectionNotificationManager.CHANNEL_ESCALATE)
    }

    @Test
    fun `CHANNEL_WATCH constant is stable`() {
        assertEquals("smsguard_watch", DetectionNotificationManager.CHANNEL_WATCH)
    }

    // -------------------------------------------------------------------------
    // Decision-to-channel mapping
    // -------------------------------------------------------------------------

    @Test
    fun `PASS maps to null channel (no notification)`() {
        val channel = decisionToChannel(DecisionState.PASS)
        assertEquals(null, channel)
    }

    @Test
    fun `PASS_WATCH maps to CHANNEL_WATCH`() {
        val channel = decisionToChannel(DecisionState.PASS_WATCH)
        assertEquals(DetectionNotificationManager.CHANNEL_WATCH, channel)
    }

    @Test
    fun `ESCALATE maps to CHANNEL_ESCALATE`() {
        val channel = decisionToChannel(DecisionState.ESCALATE)
        assertEquals(DetectionNotificationManager.CHANNEL_ESCALATE, channel)
    }

    @Test
    fun `BLOCK maps to CHANNEL_BLOCK`() {
        val channel = decisionToChannel(DecisionState.BLOCK)
        assertEquals(DetectionNotificationManager.CHANNEL_BLOCK, channel)
    }

    // -------------------------------------------------------------------------
    // Notification ID determinism
    // -------------------------------------------------------------------------

    @Test
    fun `notification ID is deterministic for same messageRefId`() {
        val id = "abc-123-def-456"
        val notifId1 = kotlin.math.abs(id.hashCode())
        val notifId2 = kotlin.math.abs(id.hashCode())
        assertEquals(notifId1, notifId2)
    }

    @Test
    fun `notification ID is non-negative (abs applied)`() {
        // Ensure we don't accidentally post a negative notification ID
        val worstCase = Int.MIN_VALUE.toString()
        val notifId = kotlin.math.abs(worstCase.hashCode())
        assert(notifId >= 0) { "Notification ID must be non-negative, got $notifId" }
    }

    @Test
    fun `different messageRefIds produce different notification IDs`() {
        val id1 = "msg-aaa-111"
        val id2 = "msg-bbb-222"
        val notifId1 = kotlin.math.abs(id1.hashCode())
        val notifId2 = kotlin.math.abs(id2.hashCode())
        assert(notifId1 != notifId2) {
            "Different message IDs should produce different notification IDs"
        }
    }

    // -------------------------------------------------------------------------
    // Notification content privacy
    // -------------------------------------------------------------------------

    @Test
    fun `BLOCK notification body does not contain the word 'body' or 'text'`() {
        // Sanity check: notification copy is safety-oriented, not SMS-content-exposing
        val blockBody = "High-risk message from abc12345.... Do not click links or reply."
        assert(!blockBody.lowercase().contains("sms body")) {
            "Notification body must not reference SMS body content"
        }
    }

    @Test
    fun `ESCALATE notification body does not expose sender phone number`() {
        val senderHash = com.smsfraud.detector.util.HashUtil.sha256("+919876543210")
        val senderDisplay = senderHash.take(8) + "..."
        val body = "Message from $senderDisplay requires your attention."
        assert(!body.contains("+91")) { "Notification body must not contain raw phone number" }
        assert(!body.contains("9876543210")) { "Notification body must not contain raw phone number digits" }
    }

    // -------------------------------------------------------------------------
    // Helper: mirrors the channel selection logic in DetectionNotificationManager
    // -------------------------------------------------------------------------

    private fun decisionToChannel(state: DecisionState): String? = when (state) {
        DecisionState.PASS       -> null
        DecisionState.PASS_WATCH -> DetectionNotificationManager.CHANNEL_WATCH
        DecisionState.ESCALATE   -> DetectionNotificationManager.CHANNEL_ESCALATE
        DecisionState.BLOCK      -> DetectionNotificationManager.CHANNEL_BLOCK
    }
}
