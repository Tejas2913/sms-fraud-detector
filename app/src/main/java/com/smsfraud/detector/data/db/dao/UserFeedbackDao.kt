package com.smsfraud.detector.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsfraud.detector.data.db.entity.UserFeedbackEntity

@Dao
interface UserFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: UserFeedbackEntity)

    @Query("SELECT * FROM user_feedback WHERE message_id = :messageId ORDER BY created_at DESC")
    suspend fun getByMessageId(messageId: String): List<UserFeedbackEntity>

    @Query("DELETE FROM user_feedback")
    suspend fun deleteAll()
}
