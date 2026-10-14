package com.smsfraud.detector.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smsfraud.detector.data.db.dao.AuditLogDao
import com.smsfraud.detector.data.db.dao.MessageRefDao
import com.smsfraud.detector.data.db.dao.SenderHistoryDao
import com.smsfraud.detector.data.db.dao.SettingsDao
import com.smsfraud.detector.data.db.dao.UserFeedbackDao
import com.smsfraud.detector.data.db.entity.AuditLogEntity
import com.smsfraud.detector.data.db.entity.MessageRefEntity
import com.smsfraud.detector.data.db.entity.SenderHistoryEntity
import com.smsfraud.detector.data.db.entity.SettingsEntity
import com.smsfraud.detector.data.db.entity.UserFeedbackEntity

/**
 * Room database with 5 tables per Product Spec v4 §9.
 *
 * Version history:
 *   v1 — MVP schema (Milestone 1/2)
 *   v2 — Milestone 3: adds decision_reason column to audit_log
 *        Reason: DetectionPipeline already computes the decision reason string
 *        (e.g. "rule:CredentialHarvest", "confidence_gate", "trusted_sender+no_url")
 *        in DecisionControllerImpl and returns it in DecisionEvent. Milestone 3
 *        begins persisting this field so the Result Detail screen can display it
 *        and the Audit Log can expose it for research analysis.
 */
@Database(
    entities = [
        MessageRefEntity::class,
        AuditLogEntity::class,
        SenderHistoryEntity::class,
        UserFeedbackEntity::class,
        SettingsEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageRefDao(): MessageRefDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun senderHistoryDao(): SenderHistoryDao
    abstract fun userFeedbackDao(): UserFeedbackDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        /**
         * Migration 1 → 2: Adds `decision_reason TEXT` column to audit_log.
         * Column is nullable with no default — existing rows will have NULL,
         * which is correct (they predate the decision reason feature).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE audit_log ADD COLUMN decision_reason TEXT"
                )
            }
        }
    }
}
