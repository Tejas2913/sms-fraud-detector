package com.smsfraud.detector.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * MessageRef entity per Product Spec v4 §9.1, Table 9.
 *
 * Intentionally holds NO message body, SMS content, display text, or extracted features.
 * The app resolves system_sms_uri against Android's SMS Content Provider at render time.
 */
@Entity(tableName = "message_ref")
data class MessageRefEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_ref_id")
    val messageRefId: String,

    @ColumnInfo(name = "sender_hash")
    val senderHash: String,

    @ColumnInfo(name = "system_sms_uri")
    val systemSmsUri: String,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    @ColumnInfo(name = "decision_state")
    val decisionState: String,

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false
)
