package com.smsfraud.detector.ui.fraudinbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsfraud.detector.data.db.entity.MessageRefEntity
import com.smsfraud.detector.data.repository.MessageRepository
import com.smsfraud.detector.domain.model.DecisionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FraudInboxUiState(
    val messages: List<MessageRefEntity> = emptyList(),
    val filter: FraudInboxFilter = FraudInboxFilter.ALL,
    val isLoading: Boolean = true
)

enum class FraudInboxFilter { ALL, BLOCK, ESCALATE, PASS_WATCH }

@HiltViewModel
class FraudInboxViewModel @Inject constructor(
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FraudInboxUiState())
    val uiState: StateFlow<FraudInboxUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            val states = listOf(
                DecisionState.BLOCK.name,
                DecisionState.ESCALATE.name,
                DecisionState.PASS_WATCH.name,
                "PASS-WATCH"
            )
            messageRepository.getByStates(states).collect { messages ->
                val filtered = when (_uiState.value.filter) {
                    FraudInboxFilter.ALL -> messages
                    FraudInboxFilter.BLOCK -> messages.filter { it.decisionState == DecisionState.BLOCK.name }
                    FraudInboxFilter.ESCALATE -> messages.filter { it.decisionState == DecisionState.ESCALATE.name }
                    FraudInboxFilter.PASS_WATCH -> messages.filter {
                        it.decisionState == DecisionState.PASS_WATCH.name || it.decisionState == "PASS-WATCH"
                    }
                }
                _uiState.value = _uiState.value.copy(messages = filtered, isLoading = false)
            }
        }
    }

    fun setFilter(filter: FraudInboxFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        loadMessages()
    }

    fun deleteMessage(messageRefId: String) {
        viewModelScope.launch {
            messageRepository.softDelete(messageRefId)
        }
    }
}
