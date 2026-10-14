package com.smsfraud.detector.data.sms

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around Android's SMS Content Provider.
 * Resolves message body from Telephony Content Provider at render time.
 * Product Spec v4 §9.1: app never stores SMS content locally.
 */
@Singleton
class SmsContentResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Internal query runner — configurable in JVM unit tests. */
    internal var queryRunner: (uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?) -> String? =
        { uri, projection, selection, selectionArgs, sortOrder ->
            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }

    /**
     * Reads the SMS body text from the system Content Provider by URI or receivedAt timestamp.
     * @param systemSmsUri The content:// URI stored in MessageRef.system_sms_uri (can be empty string)
     * @param receivedAt Epoch timestamp in milliseconds from MessageRef.receivedAt (used if URI is blank)
     * @return The message body, or null if the message has been deleted, permission denied, or provider lookup fails.
     */
    fun getMessageBody(systemSmsUri: String, receivedAt: Long = 0L): String? {
        // Attempt 1: Direct URI lookup if systemSmsUri is valid
        if (systemSmsUri.isNotBlank()) {
            val body = safeQuery(Uri.parse(systemSmsUri), arrayOf("body"), null, null, null)
            if (body != null) return body
        }

        // Attempt 2: Timestamp window query on content://sms/inbox if receivedAt is available
        if (receivedAt > 0L) {
            val minDate = (receivedAt - 5000L).toString()
            val maxDate = (receivedAt + 5000L).toString()
            return safeQuery(
                Uri.parse("content://sms/inbox"),
                arrayOf("body"),
                "date >= ? AND date <= ?",
                arrayOf(minDate, maxDate),
                "date DESC"
            )
        }

        return null
    }

    private fun safeQuery(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): String? {
        return try {
            queryRunner(uri, projection, selection, selectionArgs, sortOrder)
        } catch (e: Exception) {
            null
        }
    }
}


