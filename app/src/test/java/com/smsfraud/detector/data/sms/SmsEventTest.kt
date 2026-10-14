package com.smsfraud.detector.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for SMS PDU extraction logic.
 *
 * SmsReceiver.extractSmsEvent() is private, so we test the extraction logic
 * by exercising equivalent helper functions. The real PDU path requires
 * android.telephony.SmsMessage which is not available in JVM unit tests.
 *
 * This class tests:
 *   1. SmsEvent construction validity
 *   2. Multipart body concatenation logic
 *   3. Deduplication key determinism
 *   4. Sender hash privacy (never stores raw sender)
 *
 * Integration coverage for the actual PDU decoding path (SmsMessage.createFromPdu)
 * is deferred to instrumented tests on a physical device / emulator (Milestone 5).
 */
class SmsEventTest {

    // -------------------------------------------------------------------------
    // SmsEvent — construction and field access
    // -------------------------------------------------------------------------

    @Test
    fun `SmsEvent stores sender body and timestamp correctly`() {
        val event = SmsEvent(
            sender      = "+919876543210",
            body        = "Your OTP is 123456",
            timestampMs = 1_700_000_000_000L
        )
        assertEquals("+919876543210", event.sender)
        assertEquals("Your OTP is 123456", event.body)
        assertEquals(1_700_000_000_000L, event.timestampMs)
    }

    @Test
    fun `SmsEvent with empty sender is valid`() {
        val event = SmsEvent(sender = "", body = "Test body", timestampMs = 0L)
        assertEquals("", event.sender)
        assertNotNull(event)
    }

    @Test
    fun `SmsEvent with empty body is valid`() {
        val event = SmsEvent(sender = "HDFC-OTP", body = "", timestampMs = 100L)
        assertEquals("", event.body)
        assertNotNull(event)
    }

    // -------------------------------------------------------------------------
    // Multipart body concatenation logic
    // -------------------------------------------------------------------------

    @Test
    fun `multipart bodies concatenate in order`() {
        val parts = listOf("Hello ", "world", "!")
        val concatenated = parts.joinToString(separator = "") { it }
        assertEquals("Hello world!", concatenated)
    }

    @Test
    fun `single part body is returned as-is`() {
        val parts = listOf("Single part SMS")
        val concatenated = parts.joinToString(separator = "")
        assertEquals("Single part SMS", concatenated)
    }

    @Test
    fun `multipart with empty parts concatenates without gaps`() {
        val parts = listOf("Part1", "", "Part3")
        val concatenated = parts.joinToString(separator = "")
        assertEquals("Part1Part3", concatenated)
    }

    @Test
    fun `empty parts list produces empty body`() {
        val parts = emptyList<String>()
        val concatenated = parts.joinToString(separator = "")
        assertEquals("", concatenated)
    }

    // -------------------------------------------------------------------------
    // Deduplication key determinism
    // -------------------------------------------------------------------------

    @Test
    fun `same event always produces same dedup key`() {
        val event = SmsEvent(
            sender      = "HDFC-Alerts",
            body        = "Your account balance is INR 5000",
            timestampMs = 1_720_000_000_000L
        )
        val key1 = buildTestDedupKey(event)
        val key2 = buildTestDedupKey(event)
        assertEquals(key1, key2)
    }

    @Test
    fun `different senders produce different dedup keys`() {
        val body = "Same message body"
        val ts   = 1_720_000_000_000L
        val key1 = buildTestDedupKey(SmsEvent("sender_A", body, ts))
        val key2 = buildTestDedupKey(SmsEvent("sender_B", body, ts))
        assert(key1 != key2) { "Different senders must produce different dedup keys" }
    }

    @Test
    fun `different bodies produce different dedup keys`() {
        val sender = "HDFC-Alerts"
        val ts     = 1_720_000_000_000L
        val key1   = buildTestDedupKey(SmsEvent(sender, "body_A", ts))
        val key2   = buildTestDedupKey(SmsEvent(sender, "body_B", ts))
        assert(key1 != key2) { "Different bodies must produce different dedup keys" }
    }

    @Test
    fun `events in same 5-second window produce same bucket dedup key`() {
        val ts1  = 1_720_000_001_000L
        val ts2  = 1_720_000_004_999L // same 5-second bucket
        val key1 = buildTestDedupKey(SmsEvent("sender", "body", ts1))
        val key2 = buildTestDedupKey(SmsEvent("sender", "body", ts2))
        assertEquals("Events in same 5-second window must share dedup key", key1, key2)
    }

    @Test
    fun `events in different 5-second windows produce different dedup keys`() {
        val ts1  = 1_720_000_000_000L
        val ts2  = 1_720_000_005_001L // different 5-second bucket
        val key1 = buildTestDedupKey(SmsEvent("sender", "body", ts1))
        val key2 = buildTestDedupKey(SmsEvent("sender", "body", ts2))
        assert(key1 != key2) { "Events in different 5-second windows must produce different dedup keys" }
    }

    // -------------------------------------------------------------------------
    // Sender privacy: sha256 hash is not reversible to raw sender
    // -------------------------------------------------------------------------

    @Test
    fun `sender hash does not contain raw phone number`() {
        val rawSender = "+919876543210"
        val hash = com.smsfraud.detector.util.HashUtil.sha256(rawSender)
        assert(!hash.contains(rawSender)) { "Hash must not contain raw sender string" }
        assertEquals(64, hash.length) // SHA-256 → 64 hex chars
    }

    @Test
    fun `sender display is first 8 chars of hash followed by ellipsis`() {
        val rawSender = "HDFC-Bank"
        val hash = com.smsfraud.detector.util.HashUtil.sha256(rawSender)
        val display = hash.take(8) + "..."
        assertEquals(11, display.length) // 8 hex chars + 3-char ellipsis
        assert(display.endsWith("..."))
    }

    // -------------------------------------------------------------------------
    // Helpers (mirror coordinator logic for test isolation)
    // -------------------------------------------------------------------------

    private val DEDUP_WINDOW_MS = 5_000L

    private fun buildTestDedupKey(event: SmsEvent): String {
        val bucket = event.timestampMs / DEDUP_WINDOW_MS
        return com.smsfraud.detector.util.HashUtil.sha256("${event.sender}:${event.body}:$bucket")
    }
}
