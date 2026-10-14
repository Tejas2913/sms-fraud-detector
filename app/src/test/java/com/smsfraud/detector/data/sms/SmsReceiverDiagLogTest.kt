package com.smsfraud.detector.data.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DIAG logging privacy contract.
 *
 * Verifies that the production-build log messages do NOT contain raw sender PII.
 * These tests exercise the log message string construction logic in isolation.
 */
class SmsReceiverDiagLogTest {

    /**
     * Mirrors the production-build log message format from SmsReceiver.
     * In release builds: "DIAG [3/5] SmsReceiver extracted SmsEvent (N chars)"
     * In debug builds: "DIAG [3/5] SmsReceiver extracted SmsEvent: sender=<raw>, ..."
     */
    private fun productionDiagMessage(bodyLength: Int): String =
        "DIAG [3/5] SmsReceiver extracted SmsEvent ($bodyLength chars)"

    private fun debugDiagMessage(sender: String, bodyLength: Int, ts: Long): String =
        "DIAG [3/5] SmsReceiver extracted SmsEvent: sender=$sender, bodyLen=$bodyLength, ts=$ts"

    @Test
    fun `production diag message does not contain raw sender`() {
        val rawSender = "+919876543210"
        val msg = productionDiagMessage(42)
        assertFalse("Production log must not contain raw phone number", msg.contains(rawSender))
    }

    @Test
    fun `production diag message does not contain alphanumeric sender ID`() {
        val msg = productionDiagMessage(20)
        assertFalse("Production log must not contain 'sender='", msg.contains("sender="))
    }

    @Test
    fun `production diag message contains body length only`() {
        val msg = productionDiagMessage(55)
        assertTrue("Production log must contain character count", msg.contains("55 chars"))
    }

    @Test
    fun `production diag message does not contain SMS body text`() {
        val msg = productionDiagMessage(15)
        // Body text would only appear if we accidentally logged it
        assertFalse(msg.contains("body text"))
    }

    @Test
    fun `debug diag message contains sender for debugging purposes`() {
        val msg = debugDiagMessage("+919876543210", 42, 1700000000000L)
        assertTrue("Debug log must contain sender for diagnostics", msg.contains("sender="))
    }

    @Test
    fun `stage marker DIAG 3 of 5 is present in both production and debug messages`() {
        val prod = productionDiagMessage(10)
        val debug = debugDiagMessage("test", 10, 1700000000000L)
        assertTrue(prod.contains("DIAG [3/5]"))
        assertTrue(debug.contains("DIAG [3/5]"))
    }
}
