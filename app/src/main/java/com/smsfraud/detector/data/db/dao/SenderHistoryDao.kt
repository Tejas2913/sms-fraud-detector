package com.smsfraud.detector.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsfraud.detector.data.db.entity.SenderHistoryEntity

@Dao
interface SenderHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(senderHistory: SenderHistoryEntity)

    @Query("SELECT * FROM sender_history WHERE sender_hash = :senderHash")
    suspend fun getBySenderHash(senderHash: String): SenderHistoryEntity?

    @Query("UPDATE sender_history SET message_count = message_count + 1 WHERE sender_hash = :senderHash")
    suspend fun incrementMessageCount(senderHash: String)

    @Query("UPDATE sender_history SET fraud_reports_by_user = fraud_reports_by_user + 1 WHERE sender_hash = :senderHash")
    suspend fun incrementFraudReports(senderHash: String)

    @Query("DELETE FROM sender_history")
    suspend fun deleteAll()
}
