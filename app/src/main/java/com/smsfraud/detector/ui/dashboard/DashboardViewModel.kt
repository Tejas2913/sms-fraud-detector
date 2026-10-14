package com.smsfraud.detector.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsfraud.detector.data.repository.AuditLogRepository
import com.smsfraud.detector.data.repository.MessageRepository
import com.smsfraud.detector.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val isProtectionEnabled: Boolean = true,
    val scannedToday: Int = 0,
    val fraudInboxCount: Int = 0,
    val confirmedFraudsThisMonth: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val auditLogRepository: AuditLogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val protectionEnabled: StateFlow<Boolean> = settingsRepository
        .observeProtectionEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            messageRepository.getScannedSince(todayStart).collect { scanned ->
                _uiState.value = _uiState.value.copy(scannedToday = scanned, isLoading = false)
            }
        }
        viewModelScope.launch {
            messageRepository.getFraudInboxCount().collect { count ->
                _uiState.value = _uiState.value.copy(fraudInboxCount = count)
            }
        }
        viewModelScope.launch {
            auditLogRepository.getConfirmedFraudCount().collect { count ->
                _uiState.value = _uiState.value.copy(confirmedFraudsThisMonth = count)
            }
        }
    }

    fun toggleProtection(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.set(SettingsRepository.KEY_SMS_PROTECTION_ENABLED, enabled.toString())
        }
    }
}
