package com.smsfraud.detector.data.repository

import com.smsfraud.detector.data.db.dao.AuditLogDao
import com.smsfraud.detector.data.db.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepository @Inject constructor(
    private val dao: AuditLogDao
) {
    suspend fun insert(auditLog: AuditLogEntity) = dao.insert(auditLog)

    suspend fun getByMessageId(messageId: String): AuditLogEntity? = dao.getByMessageId(messageId)

    fun getAll(): Flow<List<AuditLogEntity>> = dao.getAll()

    fun getSince(since: Long): Flow<List<AuditLogEntity>> = dao.getSince(since)

    suspend fun updateUserAction(messageId: String, action: String) =
        dao.updateUserAction(messageId, action)

    suspend fun purgeOlderThan(before: Long) = dao.purgeOlderThan(before)

    fun getConfirmedFraudCount(): Flow<Int> = dao.getConfirmedFraudCount()

    suspend fun deleteAll() = dao.deleteAll()
}
