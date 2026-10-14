package com.smsfraud.detector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smsfraud.detector.MainActivity
import com.smsfraud.detector.domain.model.DecisionState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Creates and posts detection result notifications.
 *
 * Channel architecture (per Architecture Review § Notification Architecture):
 *
 *   CHANNEL_BLOCK     — HIGH importance, heads-up, alarm sound
 *   CHANNEL_ESCALATE  — DEFAULT importance, alert sound
 *   CHANNEL_WATCH     — LOW importance, silent
 *   (PASS)            — No notification emitted
 *
 * Channels are registered in [SmsGuardApp.onCreate] via [createChannels].
 *
 * Privacy:
 *   - Full SMS body is NEVER placed in any notification field.
 *   - Notification tap opens the detail screen keyed by [messageRefId] only.
 *   - Sender display is the first 8 hex chars of the SHA-256 sender hash.
 *
 * BLOCK semantics:
 *   BLOCK notification = high-urgency fraud alert.
 *   The app is NOT the default SMS handler and cannot delete/intercept the message.
 *   The notification explicitly tells the user NOT to interact with the message.
 */
@Singleton
class DetectionNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    init {
        createChannels(context)
    }

    // -------------------------------------------------------------------------
    // Channel constants

    // -------------------------------------------------------------------------

    companion object {
        const val CHANNEL_BLOCK    = "smsguard_block"
        const val CHANNEL_ESCALATE = "smsguard_escalate"
        const val CHANNEL_WATCH    = "smsguard_watch"

        private const val TAG = "DetectionNotificationManager"

        /**
         * Registers all three notification channels with the system.
         * Safe to call multiple times (OS is idempotent for already-registered channels).
         * Must be called before any [notify] call — typically in Application.onCreate().
         */
        fun createChannels(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BLOCK,
                    "Scam Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-risk fraudulent SMS alerts"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ESCALATE,
                    "Suspicious Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Suspicious SMS requiring attention"
                    enableVibration(true)
                }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WATCH,
                    "Caution Messages",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Messages under low-priority monitoring"
                    enableVibration(false)
                }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Posts a notification appropriate for the given [decisionState].
     * PASS → no notification (normal SMS apps already notify).
     *
     * @param messageRefId  Room primary key; passed in the tap PendingIntent.
     * @param decisionState Pipeline decision output.
     * @param senderDisplay Privacy-safe sender label (hash prefix, never raw number).
     */
    fun notify(
        messageRefId:  String,
        decisionState: DecisionState,
        senderDisplay: String
    ) {
        when (decisionState) {
            DecisionState.PASS      -> return  // Deliberate no-op
            DecisionState.PASS_WATCH -> postWatchNotification(messageRefId, senderDisplay)
            DecisionState.ESCALATE  -> postEscalateNotification(messageRefId, senderDisplay)
            DecisionState.BLOCK     -> postBlockNotification(messageRefId, senderDisplay)
        }
    }

    // -------------------------------------------------------------------------
    // Notification builders
    // -------------------------------------------------------------------------

    private fun postBlockNotification(messageRefId: String, senderDisplay: String) {
        val notification = buildNotification(
            channelId    = CHANNEL_BLOCK,
            title        = "SMS Guard — Fraudulent SMS detected",
            body         = "High-risk message from $senderDisplay. Do not click links or reply.",
            messageRefId = messageRefId,
            priority     = NotificationCompat.PRIORITY_HIGH
        )
        postSafely(messageRefId, notification)
    }

    private fun postEscalateNotification(messageRefId: String, senderDisplay: String) {
        val notification = buildNotification(
            channelId    = CHANNEL_ESCALATE,
            title        = "SMS Guard — Suspicious SMS detected",
            body         = "Message from $senderDisplay requires your attention.",
            messageRefId = messageRefId,
            priority     = NotificationCompat.PRIORITY_DEFAULT
        )
        postSafely(messageRefId, notification)
    }

    private fun postWatchNotification(messageRefId: String, senderDisplay: String) {
        val notification = buildNotification(
            channelId    = CHANNEL_WATCH,
            title        = "SMS Guard — Message requires attention",
            body         = "Low-risk message from $senderDisplay placed under watch.",
            messageRefId = messageRefId,
            priority     = NotificationCompat.PRIORITY_LOW
        )
        postSafely(messageRefId, notification)
    }

    // -------------------------------------------------------------------------
    // Shared builder
    // -------------------------------------------------------------------------

    private fun buildNotification(
        channelId:    String,
        title:        String,
        body:         String,
        messageRefId: String,
        priority:     Int
    ): Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            // Navigate directly to the detail screen for this message
            putExtra("messageRefId", messageRefId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Use abs(messageRefId.hashCode()) as a stable, distinct notification ID
        val notificationId = abs(messageRefId.hashCode())

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .build()
    }

    // -------------------------------------------------------------------------
    // Safe posting (handles missing permission gracefully)
    // -------------------------------------------------------------------------

    private fun postSafely(messageRefId: String, notification: Notification) {
        val notificationId = abs(messageRefId.hashCode())
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!enabled) {
            Log.w(TAG, "NotificationManagerCompat.areNotificationsEnabled is FALSE — notification will be blocked by system (POST_NOTIFICATIONS permission not granted or disabled in settings).")
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "Notification posted successfully (id=$notificationId, enabled=$enabled)")
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission denied — detection still ran, only alert is suppressed
            Log.w(TAG, "POST_NOTIFICATIONS permission denied — notification suppressed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification: ${e.javaClass.simpleName}")
        }
    }

}
