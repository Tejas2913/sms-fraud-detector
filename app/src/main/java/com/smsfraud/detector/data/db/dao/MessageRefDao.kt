package com.smsfraud.detector.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsfraud.detector.data.db.entity.MessageRefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageRefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(messageRef: MessageRefEntity)

    @Query("SELECT * FROM message_ref WHERE message_ref_id = :id")
    suspend fun getById(id: String): MessageRefEntity?

    @Query("SELECT * FROM message_ref WHERE decision_state = :state AND is_deleted = 0 ORDER BY received_at DESC")
    fun getByState(state: String): Flow<List<MessageRefEntity>>

    @Query("SELECT * FROM message_ref WHERE decision_state IN (:states) AND is_deleted = 0 ORDER BY received_at DESC")
    fun getByStates(states: List<String>): Flow<List<MessageRefEntity>>

    @Query("SELECT * FROM message_ref WHERE is_deleted = 0 ORDER BY received_at DESC")
    fun getAll(): Flow<List<MessageRefEntity>>

    @Query("UPDATE message_ref SET decision_state = :state WHERE message_ref_id = :id")
    suspend fun updateDecisionState(id: String, state: String)

    @Query("UPDATE message_ref SET is_deleted = :deleted WHERE message_ref_id = :id")
    suspend fun updateDeletedState(id: String, deleted: Boolean)

    @Query("SELECT COUNT(*) FROM message_ref WHERE is_deleted = 0")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM message_ref WHERE decision_state IN ('ESCALATE', 'BLOCK') AND is_deleted = 0")
    fun getFraudInboxCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM message_ref WHERE received_at >= :since AND is_deleted = 0")
    fun getScannedSince(since: Long): Flow<Int>

    @Query("DELETE FROM message_ref")
    suspend fun deleteAll()
}
