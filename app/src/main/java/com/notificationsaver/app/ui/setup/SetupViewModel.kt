package com.notificationsaver.app.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.data.npoint.NpointSender
import com.notificationsaver.app.data.telegram.SendResult
import com.notificationsaver.app.data.telegram.TelegramSender
import com.notificationsaver.app.ui.components.AppNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetupUiState(
    val token: String = "",
    val chatId: String = "",
    val npointUrl: String = "",
    val notice: AppNotice? = null,
    val busy: Boolean = false,
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotificationSaverApp
    private val tokenDraft = MutableStateFlow<String?>(null)
    private val chatDraft = MutableStateFlow<String?>(null)
    private val urlDraft = MutableStateFlow<String?>(null)
    private val flash = MutableStateFlow<AppNotice?>(null)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<SetupUiState> = combine(
        app.container.settings.snapshot,
        tokenDraft,
        chatDraft,
        urlDraft,
        combine(flash, busy) { notice, isBusy -> notice to isBusy },
    ) { settings, token, chat, url, extra ->
        SetupUiState(
            token = token ?: settings.botToken,
            chatId = chat ?: settings.chatId,
            npointUrl = url ?: settings.npointUrl,
            notice = extra.first,
            busy = extra.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    fun onTokenChange(value: String) {
        tokenDraft.value = value
    }

    fun onChatChange(value: String) {
        chatDraft.value = value
    }

    fun onUrlChange(value: String) {
        urlDraft.value = value
    }

    fun consumeNotice() {
        flash.value = null
    }

    fun saveTelegram() {
        viewModelScope.launch {
            val current = state.value
            if (current.token.isBlank() || current.chatId.isBlank()) {
                flash.value = AppNotice(
                    title = "Telegram",
                    message = "Enter bot token and chat ID",
                )
                return@launch
            }
            app.container.settings.setBotToken(current.token)
            app.container.settings.setChatId(current.chatId)
            app.container.settings.setForwardingEnabled(true)
            tokenDraft.value = null
            chatDraft.value = null
        }
    }

    fun saveNpoint() {
        viewModelScope.launch {
            val current = state.value
            if (!NpointSender.isValidUrl(current.npointUrl)) {
                flash.value = AppNotice(title = "npoint", message = NpointSender.INVALID_URL)
                return@launch
            }
            app.container.settings.ensureNpointKeys()
            app.container.settings.setNpointUrl(current.npointUrl)
            app.container.settings.setNpointEnabled(true)
            app.container.settings.setForwardingEnabled(false)
            urlDraft.value = null
        }
    }

    fun testTelegram() {
        viewModelScope.launch {
            val current = state.value
            if (current.token.isBlank() || current.chatId.isBlank()) {
                flash.value = AppNotice(
                    title = "Telegram",
                    message = "Enter bot token and chat ID",
                )
                return@launch
            }
            app.container.settings.setBotToken(current.token)
            app.container.settings.setChatId(current.chatId)
            busy.value = true
            val result = app.container.telegram.send(
                current.token,
                current.chatId,
                TelegramSender.formatMessage(
                    "Notification Saver",
                    "Connection test",
                    "This chat is ready to receive notifications.",
                ),
            )
            busy.value = false
            flash.value = when (result) {
                SendResult.Ok -> AppNotice(title = "Telegram", message = "Test sent. Check Telegram.")
                is SendResult.RetryAfter -> AppNotice(
                    title = "Telegram",
                    message = "Rate limited, retry in ${result.seconds}s",
                )
                is SendResult.Failed -> AppNotice(title = "Telegram", message = result.message)
            }
        }
    }
}
