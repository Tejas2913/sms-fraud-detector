package com.smsfraud.detector.data.sms

import android.content.BroadcastReceiver
import android.util.Log
import com.smsfraud.detector.BuildConfig
import com.smsfraud.detector.data.db.entity.AuditLogEntity
import com.smsfraud.detector.data.db.entity.MessageRefEntity
import com.smsfraud.detector.data.repository.AuditLogRepository
import com.smsfraud.detector.data.repository.MessageRepository
import com.smsfraud.detector.data.repository.SettingsRepository
import com.smsfraud.detector.domain.model.DecisionState
import com.smsfraud.detector.engine.DetectionPipeline
import com.smsfraud.detector.engine.MlEngineImpl
import com.smsfraud.detector.service.DetectionNotificationManager
import com.smsfraud.detector.util.HashUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full real-time detection pipeline for incoming SMS events.
 *
 * Called exclusively from [SmsReceiver] via goAsync(). Manages:
 *   1. Duplicate detection (concurrent-safe in-memory deduplication)
 *   2. DetectionPipeline invocation on IO dispatcher
 *   3. Room persistence (MessageRefEntity + AuditLogEntity)
 *   4. Notification dispatch via DetectionNotificationManager
 *   5. PendingResult.finish() — always called, including all error paths
 *
 * Session lifecycle:
 *   This class is a Hilt @Singleton. Its [applicationScope] uses a SupervisorJob
 *   tied to the process lifetime. Individual SMS jobs are independent; one failure
 *   does not cancel sibling jobs.
 *
 * ONNX session management:
 *   MlEngineImpl (already a Hilt @Singleton) holds the warm ONNX session.
 *   This coordinator does NOT create or close ONNX sessions.
 *
 * Privacy:
 *   - [SmsEvent.sender] is hashed before writing to Room.
 *   - [SmsEvent.body] is NEVER written to any persistent store.
 *   - Neither field is logged.
 *
 * BLOCK semantics:
 *   BLOCK means "DetectionPipeline classified this message as BLOCK."
 *   The app is NOT the default SMS handler. It cannot delete or intercept
 *   the SMS. The action is: persist the classification + high-priority alert.
 */
@Singleton
class SmsProcessingCoordinator @Inject constructor(
    private val pipeline:             DetectionPipeline,
    private val mlEngine:             MlEngineImpl,
    private val messageRepository:    MessageRepository,
    private val auditLogRepository:   AuditLogRepository,
    private val settingsRepository:   SettingsRepository,
    private val notificationManager:  DetectionNotificationManager
) {

    /**
     * Application-scoped coroutine scope.
     * SupervisorJob: a failure in one SMS job does not cancel others.
     * Dispatchers.Default as the dispatcher — IO switches happen inside.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Short-lived in-memory deduplication set.
     * Key = SHA-256(sender + body + bucketedTimestamp).
     * Entries expire lazily when a full GC cycle occurs or the process dies.
     * Max 200 entries to bound memory; oldest are pruned when over capacity.
     */
    private val recentEventKeys = ConcurrentHashMap<String, Long>()
    private val DEDUP_WINDOW_MS = 5_000L    // 5-second window
    private val DEDUP_MAX_SIZE  = 200

    /**
     * Entry point called by [SmsReceiver.onReceive] after goAsync().
     *
     * Dispatches processing on the application scope and ensures
     * [pendingResult].finish() is always called.
     *
     * @param event         extracted SMS data (sender + body + timestamp)
     * @param pendingResult the goAsync() handle to be finished when done
     */
    fun process(event: SmsEvent, pendingResult: BroadcastReceiver.PendingResult) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DIAG [4/5] SmsProcessingCoordinator.process: scheduling coroutine for sender=${event.sender}")
        } else {
            Log.d(TAG, "DIAG [4/5] SmsProcessingCoordinator.process: scheduling coroutine")
        }
        applicationScope.launch(Dispatchers.IO) {
            try {
                processSms(event)
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled error during SMS processing: ${e.javaClass.simpleName} — ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Core processing pipeline
    // -------------------------------------------------------------------------

    private suspend fun processSms(event: SmsEvent) {
        val protectionEnabled = settingsRepository.isSmsProtectionEnabled()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DIAG [5/5] processSms: protectionEnabled=$protectionEnabled, sender=${event.sender}")
        } else {
            Log.d(TAG, "DIAG [5/5] processSms: protectionEnabled=$protectionEnabled")
        }

        // 1. Check if SMS protection is enabled (user may have disabled it)
        if (!protectionEnabled) {
            Log.d(TAG, "SMS protection disabled — skipping detection.")
            return
        }

        // 2. Deduplication: drop if we've seen this exact event recently
        val dedupKey = buildDedupKey(event)
        if (isDuplicate(dedupKey, event.timestampMs)) {
            Log.d(TAG, "SMS_RECEIVED: duplicate event detected — dropping.")
            return
        }

        // 3. Generate a stable message reference ID (UUID)
        val messageRefId = UUID.randomUUID().toString()

        // 4. Run the frozen DetectionPipeline
        val pipelineStart = System.currentTimeMillis()

        val result = try {
            pipeline.run(
                rawMessage   = event.body,
                senderString = event.sender,
                messageRefId = messageRefId
            )
        } catch (e: Exception) {
            Log.e(TAG, "DetectionPipeline failure: ${e.javaClass.simpleName} — using PIPELINE_ERROR fallback")
            handlePipelineFailure(event, messageRefId)
            return
        }

        // Live SMS Processing Latency: from Android SMS delivery to SmsReceiver until final decision
        val processEndNs = System.nanoTime()
        val latencyMs = (processEndNs - event.receivedNanoTime) / 1_000_000.0
        val runNum = liveRunCounter.incrementAndGet()

        Log.i(BENCHMARK_TAG, """
            LIVE_SMS_BENCHMARK
            run=$runNum
            received_at_ms=${event.timestampMs}
            start_ns=${event.receivedNanoTime}
            end_ns=$processEndNs
            latency_ms=${String.format(java.util.Locale.US, "%.2f", latencyMs)}
            decision=${result.decisionEvent.decisionState.name}
            url_present=${result.urlList.isNotEmpty()}
            message_length=${event.body.length}
        """.trimIndent())

        Log.i(BENCHMARK_TAG, "LIVE_SMS_RECORD: run=$runNum latency_ms=${String.format(java.util.Locale.US, "%.2f", latencyMs)} decision=${result.decisionEvent.decisionState.name} url_present=${result.urlList.isNotEmpty()} message_length=${event.body.length} received_at_ms=${event.timestampMs} start_ns=${event.receivedNanoTime} end_ns=$processEndNs")

        val totalLatencyMs = (System.currentTimeMillis() - pipelineStart).toInt()

        // 5. Persist MessageRef (no SMS text stored — privacy contract)
        val senderHash = HashUtil.sha256(event.sender)
        val messageRef = MessageRefEntity(
            messageRefId  = messageRefId,
            senderHash    = senderHash,
            // systemSmsUri intentionally left empty:
            // Android's SMS_RECEIVED broadcast (for non-default SMS apps) does NOT
            // reliably expose a content:// URI for the newly received message.
            // The `msg_uri` / `uri` extras are OEM-specific and not part of the AOSP
            // Telephony contract. SmsContentResolver.getMessageBody() uses a
            // ±5-second timestamp window query on content://sms/inbox as the
            // correct resolution strategy for all devices.
            systemSmsUri  = "",
            receivedAt    = event.timestampMs,
            decisionState = result.decisionEvent.decisionState.name
        )
        val auditLog = AuditLogEntity(
            logId                       = UUID.randomUUID().toString(),
            messageId                   = messageRefId,
            ruleAction                  = result.ruleResult.action.name, // RuleAction enum → String
            ruleLabel                   = result.ruleResult.ruleLabel,
            mlScore                     = result.mlScore,
            urlRisk                     = result.urlRisk,
            senderRisk                  = result.senderRisk,
            riskFinal                   = result.riskFinal,
            decisionState               = result.decisionEvent.decisionState.name,
            urlPresent                  = result.urlList.isNotEmpty(),
            senderInContacts            = result.senderResult.senderInContacts,
            trustedSenderRecommendation = result.senderResult.trustedSenderRecommendation,
            decisionReason              = result.decisionEvent.decisionReason,
            inferenceLatencyMs          = totalLatencyMs,
            modelVersion                = mlEngine.activeVariant.label, // INT8 or FP32
            createdAt                   = System.currentTimeMillis()
        )

        try {
            messageRepository.insert(messageRef)
            auditLogRepository.insert(auditLog)
            Log.d(TAG, "PERSISTENCE OK")
        } catch (e: Exception) {
            Log.e(TAG, "Room insert failed: ${e.javaClass.simpleName}")
        }

        // 7. Post notification (PASS → silent; PASS-WATCH/ESCALATE/BLOCK → alert)
        try {
            notificationManager.notify(
                messageRefId  = messageRefId,
                decisionState = result.decisionEvent.decisionState,
                senderDisplay = senderDisplayFrom(senderHash)
            )
            Log.d(TAG, "NOTIFICATION OK")
        } catch (e: Exception) {
            Log.e(TAG, "Notification post failed: ${e.javaClass.simpleName}")
        }

        Log.i(TAG, "Detection complete: state=${result.decision} latency=${totalLatencyMs}ms")
    }

    // -------------------------------------------------------------------------
    // Pipeline failure fallback
    // -------------------------------------------------------------------------

    /**
     * Called when [DetectionPipeline.run] throws an exception.
     *
     * We do NOT silently invent a PASS. Instead we:
     *   1. Persist an ESCALATE record (conservative — assume unknown risk).
     *   2. Post an ESCALATE notification (user is notified something happened).
     *
     * The [AuditLogEntity.ruleLabel] is set to "PIPELINE_ERROR" so that failures
     * are distinguishable in the history screen and audit log.
     */
    private suspend fun handlePipelineFailure(event: SmsEvent, messageRefId: String) {
        val senderHash = HashUtil.sha256(event.sender)

        try {
            messageRepository.insert(
                MessageRefEntity(
                    messageRefId  = messageRefId,
                    senderHash    = senderHash,
                    systemSmsUri  = "",
                    receivedAt    = event.timestampMs,
                    decisionState = DecisionState.ESCALATE.name
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fallback Room insert failed: ${e.javaClass.simpleName}")
        }

        try {
            auditLogRepository.insert(
                AuditLogEntity(
                    logId                       = UUID.randomUUID().toString(),
                    messageId                   = messageRefId,
                    ruleAction                  = null,
                    ruleLabel                   = "PIPELINE_ERROR",
                    mlScore                     = 0f,
                    urlRisk                     = 0f,
                    senderRisk                  = 0f,
                    riskFinal                   = 0f,
                    decisionState               = DecisionState.ESCALATE.name,
                    urlPresent                  = false,
                    senderInContacts            = false,
                    trustedSenderRecommendation = false,
                    decisionReason              = "pipeline_error",
                    inferenceLatencyMs          = null,
                    modelVersion                = null,
                    createdAt                   = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fallback AuditLog insert failed: ${e.javaClass.simpleName}")
        }

        try {
            notificationManager.notify(
                messageRefId  = messageRefId,
                decisionState = DecisionState.ESCALATE,
                senderDisplay = senderDisplayFrom(senderHash)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fallback notification failed: ${e.javaClass.simpleName}")
        }
    }

    // -------------------------------------------------------------------------
    // Deduplication helpers
    // -------------------------------------------------------------------------

    private fun buildDedupKey(event: SmsEvent): String {
        // Bucket timestamp to 5-second windows to absorb carrier retransmission jitter
        val bucket = event.timestampMs / DEDUP_WINDOW_MS
        // Hash the content — do NOT store raw body or sender in the key set
        return HashUtil.sha256("${event.sender}:${event.body}:$bucket")
    }

    private fun isDuplicate(key: String, timestampMs: Long): Boolean {
        val now = System.currentTimeMillis()
        // Prune stale entries (older than window) to bound map size
        if (recentEventKeys.size >= DEDUP_MAX_SIZE) {
            recentEventKeys.entries.removeIf { (_, ts) -> now - ts > DEDUP_WINDOW_MS * 2 }
        }
        return recentEventKeys.putIfAbsent(key, timestampMs) != null
    }

    // -------------------------------------------------------------------------
    // Display helpers (privacy-safe)
    // -------------------------------------------------------------------------

    /**
     * Returns the first 8 hex chars of the sender hash as a display label.
     * Never exposes the real phone number or sender string.
     */
    private fun senderDisplayFrom(senderHash: String): String =
        senderHash.take(8) + "..."

    companion object {
        private const val TAG = "SmsProcessingCoordinator"
        private const val BENCHMARK_TAG = "LIVE_SMS_BENCHMARK"
        private val liveRunCounter = java.util.concurrent.atomic.AtomicInteger(0)
    }
}
