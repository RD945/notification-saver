package com.notificationsaver.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.data.AppSettings
import com.notificationsaver.app.data.ListenerStatus
import com.notificationsaver.app.data.telegram.SendResult
import com.notificationsaver.app.data.telegram.TelegramSender
import com.notificationsaver.app.service.ForwardNotificationListener
import com.notificationsaver.app.ui.DeviceStatus
import com.notificationsaver.app.ui.components.AppNotice
import com.notificationsaver.app.ui.components.NoticeAction
import com.notificationsaver.app.worker.PingWorker
import com.notificationsaver.app.worker.SendTelegramWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val listenerConnected: Boolean = false,
    val hasAccess: Boolean = false,
    val batteryExempt: Boolean = false,
    val notice: AppNotice? = null,
    val busy: Boolean = false,
) {
    val canForward: Boolean
        get() = settings.setupComplete && hasAccess && settings.allowlist.isNotEmpty()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotificationSaverApp
    private val extra = MutableStateFlow(0)
    private val flash = MutableStateFlow<AppNotice?>(null)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(
        app.container.settings.snapshot,
        ListenerStatus.connected,
        extra,
        flash,
        busy,
    ) { settings, connected, _, notice, isBusy ->
        HomeUiState(
            settings = settings,
            listenerConnected = connected,
            hasAccess = DeviceStatus.hasNotificationAccess(app),
            batteryExempt = DeviceStatus.isIgnoringBatteryOptimizations(app),
            notice = notice,
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        extra.value = extra.value + 1
    }

    fun consumeNotice() {
        flash.value = null
    }

    fun performNoticeAction(action: NoticeAction) {
        when (action) {
            NoticeAction.OpenBackgroundSettings -> {
                if (!DeviceStatus.isIgnoringBatteryOptimizations(app)) {
                    DeviceStatus.openBatterySettings(app)
                } else {
                    DeviceStatus.openAppInfo(app)
                }
            }
            NoticeAction.OpenAppInfo -> DeviceStatus.openAppInfo(app)
            NoticeAction.ConfirmReset -> resetAll()
            NoticeAction.ConfirmResetKeys,
            NoticeAction.ConfirmClearBin,
            -> Unit
        }
    }

    fun requestReset() {
        flash.value = AppNotice(
            title = "Reset all",
            message = "This clears Telegram, npoint, keys, target apps, logs, and forwarding. You will set a destination up again.",
            actionLabel = "Reset",
            action = NoticeAction.ConfirmReset,
        )
    }

    private fun resetAll() {
        viewModelScope.launch {
            ForwardNotificationListener.unbind(app)
            app.container.database.deliveryLogDao().clear()
            app.container.database.npointItemDao().clear()
            SendTelegramWorker.cancelImmediate(app)
            PingWorker.cancel(app)
            app.container.settings.reset()
            PingWorker.sync(app, true)
        }
    }

    fun setHourlyPing(enabled: Boolean) {
        viewModelScope.launch {
            app.container.settings.setHourlyPingEnabled(enabled)
            PingWorker.sync(app, enabled)
        }
    }

    fun setOtpOnly(enabled: Boolean) {
        viewModelScope.launch {
            app.container.settings.setOtpOnly(enabled)
        }
    }

    fun onListenerTap() {
        if (!DeviceStatus.hasNotificationAccess(app)) {
            DeviceStatus.openNotificationAccess(app)
            return
        }
        runCatching { ForwardNotificationListener.rebind(app) }
        flash.value = AppNotice(
            title = "Listener",
            message = if (ListenerStatus.connected.value) {
                "Reconnected"
            } else {
                "Waiting for the system to bind"
            },
        )
    }

    fun setTelegramEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val settings = app.container.settings.cached
                if (!settings.telegramConfigured) {
                    flash.value = AppNotice(
                        title = "Telegram",
                        message = "Save bot token and chat ID first",
                    )
                    return@launch
                }
                if (!requireForwardingPrereqs(settings.allowlist.isEmpty())) return@launch
                app.container.settings.setForwardingEnabled(true)
            } else {
                app.container.settings.setForwardingEnabled(false)
            }
            syncListener()
        }
    }

    fun setNpointEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                app.container.settings.ensureNpointKeys()
                val settings = app.container.settings.current()
                if (!settings.npointConfigured) {
                    flash.value = AppNotice(
                        title = "npoint",
                        message = "Save the npoint API URL first",
                    )
                    return@launch
                }
                if (!requireForwardingPrereqs(settings.allowlist.isEmpty())) return@launch
                app.container.settings.setNpointEnabled(true)
            } else {
                app.container.settings.setNpointEnabled(false)
            }
            syncListener()
        }
    }

    private suspend fun requireForwardingPrereqs(allowlistEmpty: Boolean): Boolean {
        if (!DeviceStatus.hasNotificationAccess(app)) {
            flash.value = AppNotice(
                title = "Notification access",
                message = "Notification access is required",
            )
            DeviceStatus.openNotificationAccess(app)
            return false
        }
        if (allowlistEmpty) {
            flash.value = AppNotice(
                title = "Target apps",
                message = "Select at least one app on the Apps tab",
            )
            return false
        }
        if (!DeviceStatus.isIgnoringBatteryOptimizations(app)) {
            flash.value = AppNotice(
                title = "Allow background running",
                message = "Set battery to Unrestricted so forwarding can keep running.",
                actionLabel = "Open settings",
                action = NoticeAction.OpenBackgroundSettings,
            )
        }
        return true
    }

    private suspend fun syncListener() {
        val settings = app.container.settings.current()
        if (settings.listenerShouldRun) {
            ForwardNotificationListener.rebind(app)
        } else {
            ForwardNotificationListener.unbind(app)
        }
    }

    fun sendTest() {
        viewModelScope.launch {
            val settings = app.container.settings.cached
            if (!settings.telegramConfigured) {
                flash.value = AppNotice(
                    title = "Telegram",
                    message = "Set bot token and chat ID first",
                )
                return@launch
            }
            busy.value = true
            val result = app.container.telegram.send(
                settings.botToken,
                settings.chatId,
                TelegramSender.formatMessage(
                    "Notification Saver",
                    "Test",
                    "Forwarding is working.",
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
                    message = "Telegram asked to retry in ${result.seconds}s",
                )
                is SendResult.Failed -> AppNotice(
                    title = "Telegram",
                    message = result.message,
                )
            }
        }
    }
}
