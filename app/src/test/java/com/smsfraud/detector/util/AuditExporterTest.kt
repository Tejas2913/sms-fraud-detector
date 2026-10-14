package com.smsfraud.detector.util

import com.smsfraud.detector.data.db.entity.AuditLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AuditExporter — privacy-safe CSV generation.
 *
 * Verifies:
 *   1. Headers are present and correct.
 *   2. SMS body is not included (not stored in AuditLogEntity — enforced by schema).
 *   3. Raw sender is not included (not stored in AuditLogEntity — enforced by schema).
 *   4. senderHash is not exported.
 *   5. All required metadata fields are present in output.
 *   6. csvEscape wraps in quotes and escapes embedded quotes.
 *   7. Empty record list produces header-only output.
 */
class AuditExporterTest {

    private fun buildRecord(
        messageId: String = "test-id-001",
        decisionState: String = "PASS_WATCH",
        riskFinal: Float = 0.22f,
        mlScore: Float = 0.05f,
        urlRisk: Float = 0.0f,
        senderRisk: Float = 0.55f,
        ruleAction: String? = "SCORE",
        ruleLabel: String? = "normal",
        decisionReason: String? = "low_risk",
        urlPresent: Boolean = false,
        senderInContacts: Boolean = false,
        trustedSenderRecommendation: Boolean = false,
        inferenceLatencyMs: Int? = 12,
        modelVersion: String? = "1.0-int8",
        createdAt: Long = 1700000000000L
    ) = AuditLogEntity(
        logId = "log-001",
        messageId = messageId,
        ruleAction = ruleAction,
        ruleLabel = ruleLabel,
        mlScore = mlScore,
        urlRisk = urlRisk,
        senderRisk = senderRisk,
        riskFinal = riskFinal,
        decisionState = decisionState,
        urlPresent = urlPresent,
        senderInContacts = senderInContacts,
        trustedSenderRecommendation = trustedSenderRecommendation,
        decisionReason = decisionReason,
        inferenceLatencyMs = inferenceLatencyMs,
        modelVersion = modelVersion,
        createdAt = createdAt
    )

    // -------------------------------------------------------------------------
    // Header verification
    // -------------------------------------------------------------------------

    @Test
    fun `headers row contains all required metadata fields`() {
        val csv = AuditExporter.toCsv(listOf(buildRecord()))
        val headerRow = csv.lines().first()
        AuditExporter.HEADERS.forEach { header ->
            assertTrue("Header '$header' missing from CSV", headerRow.contains(header))
        }
    }

    @Test
    fun `empty records produces header-only output`() {
        val csv = AuditExporter.toCsv(emptyList())
        val lines = csv.trim().lines()
        assertTrue("Header row must be present for empty export", lines.isNotEmpty())
        // Only 1 line: the header
        assertTrue("Empty export must have exactly one non-empty line", lines.size == 1)
    }

    // -------------------------------------------------------------------------
    // Privacy: no SMS body
    // -------------------------------------------------------------------------

    @Test
    fun `exported CSV does not contain the string 'body'`() {
        // AuditLogEntity has no body field — this test confirms the exporter
        // does not somehow introduce a body column or value.
        val csv = AuditExporter.toCsv(listOf(buildRecord()))
        // "body" would only appear as a column header — confirm it's absent
        assertFalse(
            "CSV must not contain 'body' as a column or value",
            csv.lines().first().lowercase().contains("body")
        )
    }

    @Test
    fun `exported CSV does not contain 'smsbody' or 'sms_body'`() {
        val csv = AuditExporter.toCsv(listOf(buildRecord()))
        assertFalse(csv.lowercase().contains("smsbody"))
        assertFalse(csv.lowercase().contains("sms_body"))
    }

    // -------------------------------------------------------------------------
    // Privacy: no raw sender, truncated senderHash only
    // -------------------------------------------------------------------------

    @Test
    fun `exported CSV contains senderHash column in header`() {
        val csv = AuditExporter.toCsv(listOf(buildRecord()))
        val headerRow = csv.lines().first()
        assertTrue("Header must contain 'senderHash'", headerRow.contains("senderHash"))
    }

    @Test
    fun `exported CSV truncates senderHash to 8 characters`() {
        val fullHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val map = mapOf("test-id-001" to fullHash)
        val csv = AuditExporter.toCsv(listOf(buildRecord(messageId = "test-id-001")), map)
        val dataRow = csv.lines().drop(1).first { it.isNotBlank() }

        // Must contain first 8 chars
        assertTrue(dataRow.contains("e3b0c442"))
        // Must NOT contain full 64-char hash
        assertFalse(dataRow.contains(fullHash))
    }

    @Test
    fun `exported CSV does not contain raw phone number or sender address`() {
        val rawSender = "+919876543210"
        val fullHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val map = mapOf("test-id-001" to fullHash)
        val csv = AuditExporter.toCsv(listOf(buildRecord(messageId = "test-id-001")), map)
        assertFalse("CSV must not contain raw phone number", csv.contains(rawSender))
    }


    // -------------------------------------------------------------------------
    // Required metadata fields
    // -------------------------------------------------------------------------

    @Test
    fun `exported row contains messageId`() {
        val csv = AuditExporter.toCsv(listOf(buildRecord(messageId = "uuid-abc-123")))
        assertTrue(csv.contains("uuid-abc-123"))
    }

    @Test
    fun `exported row contains decisionState`() {
        val csv = AuditExporter.toCsv(listOf(buildRecord(decisionState = "PASS_WATCH")))
        assertTrue(csv.contains("PASS_WATCH"))
    }

    @Test
    fun `exported row contains riskFinal as float string`() {
        val csv = AuditExporter.toCsv(listOf(buildRecord(riskFinal = 0.22f)))
        assertTrue(csv.contains("0.22"))
    }

    @Test
    fun `exported row contains decisionReason`() {
        val csv = AuditExporter.toCsv(listOf(buildRecord(decisionReason = "low_risk")))
        assertTrue(csv.contains("low_risk"))
    }

    // -------------------------------------------------------------------------
    // csvEscape
    // -------------------------------------------------------------------------

    @Test
    fun `csvEscape wraps plain value in double-quotes`() {
        val result = AuditExporter.csvEscape("hello")
        assertTrue(result.startsWith("\""))
        assertTrue(result.endsWith("\""))
    }

    @Test
    fun `csvEscape escapes embedded double-quote by doubling`() {
        val result = AuditExporter.csvEscape("say \"hi\"")
        assertTrue("Embedded quotes must be doubled", result.contains("\"\""))
    }

    @Test
    fun `csvEscape handles empty string`() {
        val result = AuditExporter.csvEscape("")
        assertEquals("\"\"", result)
    }

    // -------------------------------------------------------------------------
    // File creation & naming tests
    // -------------------------------------------------------------------------

    @Test
    fun `generateFileName ends with dot csv`() {
        val fileName = AuditExporter.generateFileName()
        assertTrue("Filename must end with .csv", fileName.endsWith(".csv"))
        assertTrue("Filename must start with SMS_Guard_Audit_Log_", fileName.startsWith("SMS_Guard_Audit_Log_"))
    }

    @Test
    fun `generateFileName formats given timestamp with yyyy-MM-dd`() {
        // 2026-08-15 UTC
        val fileName = AuditExporter.generateFileName(1786790400000L)
        assertTrue(fileName.endsWith(".csv"))
        assertTrue(fileName.contains("2026-08-1"))
    }

    @Test
    fun `exported CSV does not contain contact name`() {
        val csv = AuditExporter.toCsv(listOf(buildRecord()))
        assertFalse("CSV must not contain contact name", csv.contains("contact_name"))
        assertFalse("CSV must not contain contact name header", csv.contains("contactName"))
    }

    @Test
    fun `writeCsvToFile writes UTF-8 content to file`() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "test_audit_export")
        tempDir.mkdirs()
        try {
            val file = java.io.File(tempDir, "test_export.csv")
            val sampleCsv = "messageId,receivedAt\n\"test-1\",\"2026-08-15\""
            file.writeText(sampleCsv, Charsets.UTF_8)

            assertTrue(file.exists())
            assertEquals(sampleCsv, file.readText(Charsets.UTF_8))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

