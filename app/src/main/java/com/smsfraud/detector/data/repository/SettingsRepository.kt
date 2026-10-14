package com.smsfraud.detector.data.repository

import com.smsfraud.detector.data.db.dao.SettingsDao
import com.smsfraud.detector.data.db.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dao: SettingsDao
) {
    companion object {
        const val KEY_SMS_PROTECTION_ENABLED = "sms_protection_enabled"
        const val KEY_SCAN_SCOPE = "scan_scope"
        const val KEY_CONTACT_LOOKUP_ENABLED = "contact_lookup_enabled"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_DEVELOPER_MODE_UNLOCKED = "developer_mode_unlocked"
        const val KEY_RULE_VERSION = "rule_version"
        const val KEY_PATTERN_HASH = "pattern_hash"
        const val KEY_AUDIT_LOG_RETENTION_DAYS = "audit_log_retention_days"
        const val KEY_ML_ENGINE_MODE = "ml_engine_mode"
    }

    suspend fun get(key: String): String? = dao.get(key)

    fun observe(key: String): Flow<String?> = dao.observe(key)

    suspend fun set(key: String, value: String) {
        dao.set(SettingsEntity(key = key, value = value, updatedAt = System.currentTimeMillis()))
    }

    suspend fun isOnboardingComplete(): Boolean =
        dao.get(KEY_ONBOARDING_COMPLETE) == "true"

    suspend fun setOnboardingComplete() = set(KEY_ONBOARDING_COMPLETE, "true")

    suspend fun isSmsProtectionEnabled(): Boolean =
        dao.get(KEY_SMS_PROTECTION_ENABLED) != "false" // default: enabled

    suspend fun isContactLookupEnabled(): Boolean =
        dao.get(KEY_CONTACT_LOOKUP_ENABLED) != "false" // default: enabled

    suspend fun isDeveloperModeUnlocked(): Boolean =
        dao.get(KEY_DEVELOPER_MODE_UNLOCKED) == "true"

    fun observeProtectionEnabled(): Flow<Boolean> =
        dao.observe(KEY_SMS_PROTECTION_ENABLED).map { it != "false" }

    fun observeDeveloperMode(): Flow<Boolean> =
        dao.observe(KEY_DEVELOPER_MODE_UNLOCKED).map { it == "true" }

    suspend fun clearAllExceptOnboarding() = dao.clearAllExceptOnboarding()

    suspend fun deleteAll() = dao.deleteAll()
}
