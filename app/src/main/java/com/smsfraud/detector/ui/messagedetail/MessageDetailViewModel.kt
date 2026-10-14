package com.smsfraud.detector.ui.messagedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsfraud.detector.data.db.entity.AuditLogEntity
import com.smsfraud.detector.data.db.entity.MessageRefEntity
import com.smsfraud.detector.data.repository.AuditLogRepository
import com.smsfraud.detector.data.repository.MessageRepository
import com.smsfraud.detector.data.sms.SmsContentResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageDetailUiState(
    val messageRef: MessageRefEntity? = null,
    val auditLog: AuditLogEntity? = null,
    val messageBody: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val auditLogRepository: AuditLogRepository,
    private val smsContentResolver: SmsContentResolver
) : ViewModel() {

    private val messageRefId: String = checkNotNull(savedStateHandle["messageRefId"])

    private val _uiState = MutableStateFlow(MessageDetailUiState())
    val uiState: StateFlow<MessageDetailUiState> = _uiState.asStateFlow()

    init {
        loadMessage()
    }

    private fun loadMessage() {
        viewModelScope.launch {
            val messageRef = messageRepository.getById(messageRefId)
            val auditLog = auditLogRepository.getByMessageId(messageRefId)
            val messageBody = messageRef?.let {
                smsContentResolver.getMessageBody(it.systemSmsUri, it.receivedAt)
            }
            _uiState.value = MessageDetailUiState(
                messageRef = messageRef,
                auditLog = auditLog,
                messageBody = messageBody,
                isLoading = false
            )
        }
    }
}
