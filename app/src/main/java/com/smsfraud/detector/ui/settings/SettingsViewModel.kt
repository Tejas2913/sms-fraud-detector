package com.smsfraud.detector.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsfraud.detector.data.repository.AuditLogRepository
import com.smsfraud.detector.data.repository.FeedbackRepository
import com.smsfraud.detector.data.repository.MessageRepository
import com.smsfraud.detector.data.repository.SenderHistoryRepository
import com.smsfraud.detector.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.smsfraud.detector.util.BackgroundProtectionManager
import com.smsfraud.detector.util.BackgroundProtectionStatus
import com.smsfraud.detector.util.AuditExporter
import android.content.Context
import android.content.Intent
import android.util.Log

data class SettingsUiState(
    val isProtectionEnabled: Boolean = true,
    val backgroundProtectionStatus: BackgroundProtectionStatus = BackgroundProtectionStatus.UNVERIFIABLE,
    val hasOpenedBackgroundSettings: Boolean = false,
    val scanScope: String = "all",
    val isContactLookupEnabled: Boolean = true,
    val auditLogRetentionDays: Int = 30,
    val isDeveloperModeUnlocked: Boolean = false,
    val versionTapCount: Int = 0,
    val showExportMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val messageRepository: MessageRepository,
    private val auditLogRepository: AuditLogRepository,
    private val senderHistoryRepository: SenderHistoryRepository,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val isDeveloperMode: StateFlow<Boolean> = settingsRepository
        .observeDeveloperMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProtectionEnabled = settingsRepository.isSmsProtectionEnabled(),
                scanScope = settingsRepository.get(SettingsRepository.KEY_SCAN_SCOPE) ?: "all",
                isContactLookupEnabled = settingsRepository.get(SettingsRepository.KEY_CONTACT_LOOKUP_ENABLED) != "false",
                auditLogRetentionDays = settingsRepository.get(SettingsRepository.KEY_AUDIT_LOG_RETENTION_DAYS)?.toIntOrNull() ?: 30,
                isDeveloperModeUnlocked = settingsRepository.isDeveloperModeUnlocked()
            )
        }
    }

    fun checkBackgroundProtection(context: android.content.Context) {
        val status = BackgroundProtectionManager.getStatus(
            context = context,
            hasOpenedSettings = _uiState.value.hasOpenedBackgroundSettings
        )
        _uiState.value = _uiState.value.copy(backgroundProtectionStatus = status)
    }

    fun onOpenedBackgroundSettings(context: android.content.Context) {
        _uiState.value = _uiState.value.copy(hasOpenedBackgroundSettings = true)
        checkBackgroundProtection(context)
    }

    fun setProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.set(SettingsRepository.KEY_SMS_PROTECTION_ENABLED, enabled.toString())
            _uiState.value = _uiState.value.copy(isProtectionEnabled = enabled)
        }
    }

    fun setScanScope(scope: String) {
        viewModelScope.launch {
            settingsRepository.set(SettingsRepository.KEY_SCAN_SCOPE, scope)
            _uiState.value = _uiState.value.copy(scanScope = scope)
        }
    }

    fun setContactLookupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.set(SettingsRepository.KEY_CONTACT_LOOKUP_ENABLED, enabled.toString())
            _uiState.value = _uiState.value.copy(isContactLookupEnabled = enabled)
        }
    }

    fun setAuditLogRetention(days: Int) {
        viewModelScope.launch {
            settingsRepository.set(SettingsRepository.KEY_AUDIT_LOG_RETENTION_DAYS, days.toString())
            _uiState.value = _uiState.value.copy(auditLogRetentionDays = days)
        }
    }

    /** Product Spec v4 §13.2: 7-tap on version number unlocks Developer Mode. */
    fun onVersionTap() {
        val newCount = _uiState.value.versionTapCount + 1
        if (newCount >= 7) {
            viewModelScope.launch {
                settingsRepository.set(SettingsRepository.KEY_DEVELOPER_MODE_UNLOCKED, "true")
                _uiState.value = _uiState.value.copy(
                    isDeveloperModeUnlocked = true,
                    versionTapCount = 0
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(versionTapCount = newCount)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            messageRepository.deleteAll()
            auditLogRepository.deleteAll()
            senderHistoryRepository.deleteAll()
            feedbackRepository.deleteAll()
            settingsRepository.clearAllExceptOnboarding()
            loadSettings()
        }
    }

    /**
     * Exports audit log records as an actual .csv file attachment via FileProvider and Android share sheet.
     *
     * Privacy guarantees:
     *   - No SMS body is included (not stored in Room).
     *   - No raw sender address/phone number is included (not stored in Room).
     *   - senderHash is truncated to at most 8 hex characters.
     *   - Only detection metadata fields are exported.
     *
     * Falls back to an error snackbar if the export fails or the records list is genuinely empty.
     */
    fun exportAuditLog(context: Context) {
        viewModelScope.launch {
            try {
                // Collect real Room audit records from database
                val records = auditLogRepository.getAll().first()

                if (records.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        showExportMessage = "No audit records to export yet."
                    )
                    return@launch
                }

                // Map messageRefId -> senderHash for truncated senderHash export
                val messageRefs = messageRepository.getAll().first()
                val senderHashMap = messageRefs.associate { it.messageRefId to it.senderHash }

                // 1. Generate CSV content
                val csv = AuditExporter.toCsv(records, senderHashMap)

                // 2. Write to temporary .csv file in app cache directory
                val fileName = AuditExporter.generateFileName()
                val file = AuditExporter.writeCsvToFile(context, csv, fileName)

                // 3. Obtain secure FileProvider content:// URI
                val contentUri = AuditExporter.getFileProviderUri(context, file)

                // 4. Create ACTION_SEND intent with text/csv MIME type and EXTRA_STREAM
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "SMS Guard Audit Log")
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Export Audit Log").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Log.e(TAG, "Audit export failed", e)
                _uiState.value = _uiState.value.copy(
                    showExportMessage = "Export failed. Please try again."
                )
            }
        }
    }



    fun clearExportMessage() {
        _uiState.value = _uiState.value.copy(showExportMessage = null)
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
