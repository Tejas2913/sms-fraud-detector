package com.smsfraud.detector.data.repository

import com.smsfraud.detector.data.db.dao.UserFeedbackDao
import com.smsfraud.detector.data.db.entity.UserFeedbackEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor(
    private val dao: UserFeedbackDao
) {
    suspend fun insert(feedback: UserFeedbackEntity) = dao.insert(feedback)

    suspend fun getByMessageId(messageId: String): List<UserFeedbackEntity> =
        dao.getByMessageId(messageId)

    suspend fun deleteAll() = dao.deleteAll()
}
