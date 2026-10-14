package com.smsfraud.detector.ui.auditlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsfraud.detector.data.db.entity.AuditLogEntity
import com.smsfraud.detector.data.repository.AuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditLogRepository: AuditLogRepository
) : ViewModel() {

    private val _logs = MutableStateFlow<List<AuditLogEntity>>(emptyList())
    val logs: StateFlow<List<AuditLogEntity>> = _logs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            auditLogRepository.getAll().collect {
                _logs.value = it
                _isLoading.value = false
            }
        }
    }
}
