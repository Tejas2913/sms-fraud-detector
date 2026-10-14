package com.smsfraud.detector.domain.model

/**
 * User feedback actions per Product Spec v4 §8, Table 8.
 * Stored in the UserFeedback table, independent from AuditLog.
 */
enum class FeedbackAction {
    APPEAL,
    CONFIRM_FRAUD,
    DELIVER_ANYWAY,
    DELETE,
    RESTORE
}
