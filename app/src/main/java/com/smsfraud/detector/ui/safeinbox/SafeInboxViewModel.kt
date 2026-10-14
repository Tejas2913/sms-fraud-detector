package com.smsfraud.detector.ui.safeinbox

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

@HiltViewModel
class SafeInboxViewModel @Inject constructor(
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<MessageRefEntity>>(emptyList())
    val messages: StateFlow<List<MessageRefEntity>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            messageRepository.getByState(DecisionState.PASS.name).collect {
                _messages.value = it
                _isLoading.value = false
            }
        }
    }
}
