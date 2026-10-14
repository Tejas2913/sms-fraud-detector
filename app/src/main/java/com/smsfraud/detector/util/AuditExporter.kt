package com.smsfraud.detector.util

import android.content.Context
import android.net.Uri

import androidx.core.content.FileProvider
import com.smsfraud.detector.data.db.entity.AuditLogEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * Privacy-safe audit log exporter.
 *
 * Formats [AuditLogEntity] records into a CSV string suitable for sharing
 * via Android's ACTION_SEND intent.
 *
 * Privacy guarantees:
 *   - SMS body is NEVER included (not stored in Room).
 *   - Raw sender address/phone number is NEVER included (not stored in Room).
 *   - senderHash is truncated to at most 8 hex characters.
 *   - Only detection metadata is exported.
 *
 * Column order (stable):
 *   messageId, receivedAt, decisionState, senderHash, riskFinal, mlScore,
 *   urlRisk, senderRisk, ruleAction, ruleLabel, decisionReason,
 *   inferenceLatencyMs, modelVersion, urlPresent, senderInContacts,
 *   trustedSenderRecommendation
 */
object AuditExporter {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Column headers per Product Spec & Audit Export requirements. */
    val HEADERS = listOf(
        "messageId",
        "receivedAt",
        "decisionState",
        "senderHash",
        "riskFinal",
        "mlScore",
        "urlRisk",
        "senderRisk",
        "ruleAction",
        "ruleLabel",
        "decisionReason",
        "inferenceLatencyMs",
        "modelVersion",
        "urlPresent",
        "senderInContacts",
        "trustedSenderRecommendation"
    )

    /**
     * Converts a list of [AuditLogEntity] records to a privacy-safe CSV string.
     * Returns a header-only string when [records] is empty.
     *
     * @param records List of audit log records.
     * @param senderHashMap Optional map of messageRefId -> senderHash to populate truncated senderHash.
     */
    fun toCsv(
        records: List<AuditLogEntity>,
        senderHashMap: Map<String, String> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.appendLine(HEADERS.joinToString(","))
        records.forEach { record ->
            sb.appendLine(toCsvRow(record, senderHashMap[record.messageId]))
        }
        return sb.toString()
    }

    /**
     * Converts a single [AuditLogEntity] to a CSV row string.
     * Truncates senderHash to 8 characters if provided.
     * All string fields are CSV-escaped to prevent injection.
     */
    fun toCsvRow(record: AuditLogEntity, rawSenderHash: String? = null): String {
        val receivedAtHuman = DATE_FORMAT.format(Date(record.createdAt))
        val truncatedHash = rawSenderHash?.take(8) ?: ""
        return listOf(
            csvEscape(record.messageId),
            csvEscape(receivedAtHuman),
            csvEscape(record.decisionState),
            csvEscape(truncatedHash),
            record.riskFinal.toString(),
            record.mlScore.toString(),
            record.urlRisk.toString(),
            record.senderRisk.toString(),
            csvEscape(record.ruleAction ?: ""),
            csvEscape(record.ruleLabel ?: ""),
            csvEscape(record.decisionReason ?: ""),
            (record.inferenceLatencyMs ?: "").toString(),
            csvEscape(record.modelVersion ?: ""),
            record.urlPresent.toString(),
            record.senderInContacts.toString(),
            record.trustedSenderRecommendation.toString()
        ).joinToString(",")
    }

    /**
     * Generates a descriptive file name with date suffix, e.g. "SMS_Guard_Audit_Log_2026-08-15.csv".
     */
    fun generateFileName(timestampMs: Long = System.currentTimeMillis()): String {
        val dateSuffix = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMs))
        return "SMS_Guard_Audit_Log_$dateSuffix.csv"
    }

    /**
     * Writes CSV string to a temporary .csv file inside app cache directory (cache/audit_exports/).
     * Guarantees UTF-8 encoding.
     */
    fun writeCsvToFile(context: Context, csvContent: String, fileName: String = generateFileName()): File {
        val exportDir = File(context.cacheDir, "audit_exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val file = File(exportDir, fileName)
        file.writeText(csvContent, Charsets.UTF_8)
        return file
    }

    /**
     * Obtains a secure content:// URI via AndroidX FileProvider for sharing.
     */
    fun getFileProviderUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /** Wraps value in double-quotes and escapes embedded double-quotes. */
    fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}


