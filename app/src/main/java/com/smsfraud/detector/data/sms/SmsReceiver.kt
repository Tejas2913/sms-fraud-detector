package com.smsfraud.detector.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import com.smsfraud.detector.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Manifest-declared BroadcastReceiver for incoming SMS.
 *
 * Listens for [android.provider.Telephony.SMS_RECEIVED].
 * Does NOT require the Default SMS App role — any app holding
 * RECEIVE_SMS can register for this action (API 26+ exempt from
 * background broadcast restrictions, per telephony whitelist).
 *
 * Processing contract:
 *   - Extracts PDUs and reconstructs multipart messages on the calling thread.
 *   - Immediately hands off to [SmsProcessingCoordinator] via goAsync().
 *   - Never performs blocking I/O or ONNX inference inside onReceive().
 *   - Always calls PendingResult.finish() — even on error paths.
 *
 * Privacy:
 *   - Sender and body are NEVER logged to Logcat.
 *   - Malformed / empty PDU arrays are silently dropped after error logging
 *     the PDU count only (no content).
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: SmsProcessingCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val receiveStartNs = System.nanoTime()
        if (intent.action != ACTION_SMS_RECEIVED) return

        Log.d(TAG, "DIAG [1/5] SmsReceiver.onReceive: action=${intent.action}, extras=${intent.extras?.keySet()?.joinToString()}")

        // goAsync() extends the broadcast timeout beyond the 10-second ANR window
        // so the coordinator can safely dispatch to a coroutine scope.
        val pendingResult = goAsync()

        try {
            val event = extractSmsEvent(intent, receiveStartNs)
            if (event == null) {
                Log.w(TAG, "DIAG [FAIL] SMS_RECEIVED: could not extract a valid SmsEvent — dropping.")
                pendingResult.finish()
                return
            }
            // Production log: stage marker only. Sender/body detail gated to debug builds.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DIAG [3/5] SmsReceiver extracted SmsEvent: sender=${event.sender}, bodyLen=${event.body.length}, ts=${event.timestampMs}")
            } else {
                Log.d(TAG, "DIAG [3/5] SmsReceiver extracted SmsEvent (${event.body.length} chars)")
            }
            // Hand off to coordinator; it is responsible for calling finish()
            coordinator.process(event, pendingResult)
        } catch (e: Exception) {
            Log.e(TAG, "DIAG [FAIL] SMS_RECEIVED: unexpected error during PDU extraction: ${e.javaClass.simpleName} — ${e.message}")
            pendingResult.finish()
        }
    }

    // -------------------------------------------------------------------------
    // PDU extraction helpers
    // -------------------------------------------------------------------------

    /**
     * Reconstructs a single [SmsEvent] from the PDU bundle in the broadcast intent.
     * Uses standard [android.provider.Telephony.Sms.Intents.getMessagesFromIntent] with
     * fallback to raw PDU array extraction to support all Android versions (API 21-35+).
     */
    private fun extractSmsEvent(intent: Intent, receiveStartNs: Long): SmsEvent? {
        // Attempt 1: Standard Android Telephony SDK helper (robust across API levels 19-35+)
        val sdkMessages = try {
            android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull()
        } catch (e: Exception) {
            Log.w(TAG, "DIAG: Telephony.Sms.Intents.getMessagesFromIntent threw: ${e.javaClass.simpleName}")
            null
        }

        val messages: List<SmsMessage> = if (!sdkMessages.isNullOrEmpty()) {
            Log.d(TAG, "DIAG [2/5] SmsReceiver extracted ${sdkMessages.size} SmsMessage(s) via Telephony SDK")
            sdkMessages
        } else {
            // Attempt 2: Fallback raw PDU array parsing
            val format = intent.getStringExtra("format") ?: "3gpp"
            val pdusObj = intent.extras?.get("pdus")
            val rawPdus: Array<*> = when (pdusObj) {
                is Array<*> -> pdusObj
                is java.util.ArrayList<*> -> pdusObj.toArray()
                else -> emptyArray<Any>()
            }

            Log.d(TAG, "DIAG [2/5] SmsReceiver fallback: found ${rawPdus.size} raw PDU(s), format=$format")
            if (rawPdus.isEmpty()) {
                Log.w(TAG, "DIAG [FAIL] SMS_RECEIVED: pdu array is empty — dropping.")
                return null
            }

            rawPdus.mapNotNull { pdu ->
                if (pdu !is ByteArray) return@mapNotNull null
                try {
                    SmsMessage.createFromPdu(pdu, format)
                } catch (e: Exception) {
                    Log.w(TAG, "SMS_RECEIVED: failed to parse PDU — skipping part: ${e.javaClass.simpleName}")
                    null
                }
            }
        }

        if (messages.isEmpty()) {
            Log.w(TAG, "DIAG [FAIL] SMS_RECEIVED: decoded messages list is empty — dropping.")
            return null
        }

        // Use the first valid message for sender + timestamp
        val first = messages.first()
        val sender = first.originatingAddress ?: ""

        // Concatenate all message bodies in PDU order (handles multipart)
        val body = messages.joinToString(separator = "") { msg ->
            msg.messageBody ?: ""
        }

        val timestampMs = first.timestampMillis

        return SmsEvent(
            sender           = sender,
            body             = body,
            timestampMs      = timestampMs,
            receivedNanoTime = receiveStartNs
        )
    }

    companion object {
        private const val TAG = "SmsReceiver"
        const val ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED"
    }
}
