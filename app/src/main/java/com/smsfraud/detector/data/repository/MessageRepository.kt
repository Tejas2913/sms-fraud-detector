package com.smsfraud.detector.data.repository

import com.smsfraud.detector.data.db.dao.MessageRefDao
import com.smsfraud.detector.data.db.entity.MessageRefEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val dao: MessageRefDao
) {
    suspend fun insert(messageRef: MessageRefEntity) = dao.insert(messageRef)

    suspend fun getById(id: String): MessageRefEntity? = dao.getById(id)

    fun getByState(state: String): Flow<List<MessageRefEntity>> = dao.getByState(state)

    fun getByStates(states: List<String>): Flow<List<MessageRefEntity>> = dao.getByStates(states)

    fun getAll(): Flow<List<MessageRefEntity>> = dao.getAll()

    suspend fun updateDecisionState(id: String, state: String) = dao.updateDecisionState(id, state)

    suspend fun softDelete(id: String) = dao.updateDeletedState(id, true)

    suspend fun restore(id: String) = dao.updateDeletedState(id, false)

    fun getTotalCount(): Flow<Int> = dao.getTotalCount()

    fun getFraudInboxCount(): Flow<Int> = dao.getFraudInboxCount()

    fun getScannedSince(since: Long): Flow<Int> = dao.getScannedSince(since)

    suspend fun deleteAll() = dao.deleteAll()
}
