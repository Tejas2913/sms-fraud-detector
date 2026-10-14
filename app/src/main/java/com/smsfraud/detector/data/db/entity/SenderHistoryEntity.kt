package com.smsfraud.detector.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SenderHistory entity per Architecture v2 Layer 2D + Product Spec v4 §9.3.
 * Per-sender reputation accumulator.
 */
@Entity(tableName = "sender_history")
data class SenderHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "sender_hash")
    val senderHash: String,

    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,

    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long,

    @ColumnInfo(name = "fraud_reports_by_user")
    val fraudReportsByUser: Int = 0,

    @ColumnInfo(name = "is_registered_business")
    val isRegisteredBusiness: Boolean = false
)
