package com.notificationsaver.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.data.ListenerStatus
import com.notificationsaver.app.data.NotificationDeduplicator
import com.notificationsaver.app.data.db.DeliveryLog
import com.notificationsaver.app.data.db.DeliveryStatus
import com.notificationsaver.app.worker.SendTelegramWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ForwardNotificationListener : NotificationListenerService() {
    private var unbindJob: Job? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerStatus.setConnected(true)
        unbindJob?.cancel()
        unbindJob = NotificationSaverApp.instance.container.settings.settings
            .map { it.forwardingEnabled }
            .distinctUntilChanged()
            .onEach { enabled ->
                if (!enabled) {
                    runCatching { requestUnbind() }
                }
            }
            .launchIn(NotificationSaverApp.instance.applicationScope)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        unbindJob?.cancel()
        unbindJob = null
        ListenerStatus.setConnected(false)
        val enabled = runCatching {
            NotificationSaverApp.instance.container.settings.cached.forwardingEnabled
        }.getOrDefault(false)
        if (enabled) {
            requestRebind(component(this))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val settings = NotificationSaverApp.instance.container.settings.cached
        if (!settings.forwardingEnabled) return
        if (sbn.packageName == packageName) return
        if (settings.ignoreTelegram && sbn.packageName in TELEGRAM_PACKAGES) return
        if (sbn.isOngoing) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.packageName !in settings.allowlist) return
        if (NotificationDeduplicator.isDuplicate(sbn.key)) return

        val extras = sbn.notification.extras
        val title = extras.charSequence(Notification.EXTRA_TITLE)
        val text = extras.charSequence(Notification.EXTRA_BIG_TEXT)
            .ifBlank { extras.charSequence(Notification.EXTRA_TEXT) }
        if (title.isBlank() && text.isBlank()) return

        val appName = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        }.getOrDefault(sbn.packageName)

        val log = DeliveryLog(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            postedAt = sbn.postTime,
            queuedAt = System.currentTimeMillis(),
            status = DeliveryStatus.QUEUED.name,
            notificationKey = sbn.key,
        )

        NotificationSaverApp.instance.applicationScope.launch(Dispatchers.IO) {
            NotificationSaverApp.instance.container.database.deliveryLogDao().insert(log)
            SendTelegramWorker.enqueueImmediate(this@ForwardNotificationListener)
        }
    }

    companion object {
        val TELEGRAM_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
        )

        fun component(context: Context): ComponentName =
            ComponentName(context, ForwardNotificationListener::class.java)

        fun hasAccess(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val manager = context.getSystemService(android.app.NotificationManager::class.java)
                manager.isNotificationListenerAccessGranted(component(context))
            } else {
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
        }

        fun rebind(context: Context) {
            NotificationListenerService.requestRebind(component(context))
        }

        fun bounce(context: Context) {
            val pm = context.packageManager
            val name = component(context)
            pm.setComponentEnabledSetting(
                name,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                name,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
            rebind(context)
        }

        fun unbind(context: Context) {
            if (Build.VERSION.SDK_INT >= 34) {
                NotificationListenerService.requestUnbind(component(context))
            }
        }
    }
}

private fun android.os.Bundle.charSequence(key: String): String =
    getCharSequence(key)?.toString()?.trim().orEmpty()
