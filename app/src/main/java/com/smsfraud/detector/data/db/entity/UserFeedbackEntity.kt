package com.smsfraud.detector.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * UserFeedback entity per Product Spec v4 §9.4.
 * Independent from AuditLog — supports future ML training pipelines.
 */
@Entity(tableName = "user_feedback")
data class UserFeedbackEntity(
    @PrimaryKey
    @ColumnInfo(name = "feedback_id")
    val feedbackId: String,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "original_state")
    val originalState: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
