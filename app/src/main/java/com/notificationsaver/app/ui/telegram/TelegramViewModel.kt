package com.notificationsaver.app.ui.telegram

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.data.telegram.SendResult
import com.notificationsaver.app.data.telegram.TelegramSender
import com.notificationsaver.app.ui.components.AppNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TelegramUiState(
    val token: String = "",
    val chatId: String = "",
    val ignoreTelegram: Boolean = true,
    val notice: AppNotice? = null,
    val busy: Boolean = false,
)

class TelegramViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotificationSaverApp
    private val tokenDraft = MutableStateFlow<String?>(null)
    private val chatDraft = MutableStateFlow<String?>(null)
    private val flash = MutableStateFlow<AppNotice?>(null)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<TelegramUiState> = combine(
        app.container.settings.snapshot,
        tokenDraft,
        chatDraft,
        flash,
        busy,
    ) { settings, token, chat, notice, isBusy ->
        TelegramUiState(
            token = token ?: settings.botToken,
            chatId = chat ?: settings.chatId,
            ignoreTelegram = settings.ignoreTelegram,
            notice = notice,
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TelegramUiState())

    fun onTokenChange(value: String) {
        tokenDraft.value = value
    }

    fun onChatChange(value: String) {
        chatDraft.value = value
    }

    fun consumeNotice() {
        flash.value = null
    }

    fun save() {
        viewModelScope.launch {
            val current = state.value
            app.container.settings.setBotToken(current.token)
            app.container.settings.setChatId(current.chatId)
            tokenDraft.value = null
            chatDraft.value = null
            flash.value = AppNotice(
                title = "Saved",
                message = "Bot token and chat ID stay on this phone.",
            )
        }
    }

    fun setIgnoreTelegram(ignore: Boolean) {
        viewModelScope.launch {
            app.container.settings.setIgnoreTelegram(ignore)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val current = state.value
            app.container.settings.setBotToken(current.token)
            app.container.settings.setChatId(current.chatId)
            tokenDraft.value = null
            chatDraft.value = null
            if (current.token.isBlank() || current.chatId.isBlank()) {
                flash.value = AppNotice(
                    title = "Telegram",
                    message = "Enter bot token and chat ID",
                )
                return@launch
            }
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
                SendResult.Ok -> AppNotice(
                    title = "Telegram",
                    message = "Test sent. Check Telegram.",
                )
                is SendResult.RetryAfter -> AppNotice(
                    title = "Telegram",
                    message = "Rate limited, retry in ${result.seconds}s",
                )
                is SendResult.Failed -> AppNotice(
                    title = "Telegram",
                    message = result.message,
                )
            }
        }
    }
}
