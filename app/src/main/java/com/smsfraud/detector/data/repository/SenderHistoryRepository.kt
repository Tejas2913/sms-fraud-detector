package com.smsfraud.detector.data.repository

import com.smsfraud.detector.data.db.dao.SenderHistoryDao
import com.smsfraud.detector.data.db.entity.SenderHistoryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SenderHistoryRepository @Inject constructor(
    private val dao: SenderHistoryDao
) {
    suspend fun getOrCreate(senderHash: String, currentTime: Long): SenderHistoryEntity {
        val existing = dao.getBySenderHash(senderHash)
        if (existing != null) return existing
        val newEntry = SenderHistoryEntity(
            senderHash = senderHash,
            firstSeenAt = currentTime
        )
        dao.insertIfNew(newEntry)
        return dao.getBySenderHash(senderHash) ?: newEntry
    }

    suspend fun getBySenderHash(senderHash: String): SenderHistoryEntity? =
        dao.getBySenderHash(senderHash)

    suspend fun incrementMessageCount(senderHash: String) = dao.incrementMessageCount(senderHash)

    suspend fun incrementFraudReports(senderHash: String) = dao.incrementFraudReports(senderHash)

    suspend fun deleteAll() = dao.deleteAll()
}
