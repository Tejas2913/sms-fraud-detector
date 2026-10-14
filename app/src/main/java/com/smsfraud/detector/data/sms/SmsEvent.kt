package com.smsfraud.detector.data.sms

/**
 * Immutable value object representing an incoming SMS message as extracted
 * from the Android Telephony API's PDU array inside an SMS_RECEIVED broadcast.
 *
 * Privacy contract:
 *   - [sender]      is passed directly to DetectionPipeline.run() and never
 *                   written to Room.  The database stores sender_hash only.
 *   - [body]        is passed to DetectionPipeline.run() and never written
 *                   to Room.  MessageRefEntity deliberately excludes it.
 *   - [timestampMs] is the network-reported PDU timestamp (epoch millis).
 *
 * Logcat guidance: Do NOT log [sender] or [body] in production builds.
 */
data class SmsEvent(
    /** Raw sender address string as reported by the PDU. */
    val sender: String,
    /** Reconstructed (possibly multipart-concatenated) message body. */
    val body: String,
    /** Network timestamp in epoch milliseconds from the PDU. */
    val timestampMs: Long,
    /** Monotonic delivery timestamp at the exact moment onReceive() is called by Android. */
    val receivedNanoTime: Long = System.nanoTime()
)
