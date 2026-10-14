package com.smsfraud.detector.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsfraud.detector.data.db.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(auditLog: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE message_id = :messageId")
    suspend fun getByMessageId(messageId: String): AuditLogEntity?

    @Query("SELECT * FROM audit_log ORDER BY created_at DESC")
    fun getAll(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE created_at >= :since ORDER BY created_at DESC")
    fun getSince(since: Long): Flow<List<AuditLogEntity>>

    @Query("UPDATE audit_log SET user_action = :action WHERE message_id = :messageId")
    suspend fun updateUserAction(messageId: String, action: String)

    @Query("DELETE FROM audit_log WHERE created_at < :before")
    suspend fun purgeOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM audit_log WHERE decision_state = 'BLOCK' OR (decision_state = 'ESCALATE' AND user_action = 'CONFIRM_FRAUD')")
    fun getConfirmedFraudCount(): Flow<Int>

    @Query("DELETE FROM audit_log")
    suspend fun deleteAll()
}
