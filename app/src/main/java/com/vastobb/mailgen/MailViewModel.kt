package com.vastobb.mailgen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MailViewModel : ViewModel() {

    private val _mails = MutableStateFlow<List<MailMessage>>(emptyList())
    val mails: StateFlow<List<MailMessage>> = _mails.asStateFlow()

    private val _selectedMail = MutableStateFlow<MailMessage?>(null)
    val selectedMail: StateFlow<MailMessage?> = _selectedMail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadMails(inbox: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch mails list
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectMail(mailId: String) {
        viewModelScope.launch {
            try {
                // Fetch detailed mail message with body and attachments
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearSelection() {
        _selectedMail.value = null
    }
}
