package com.notificationsaver.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.worker.HealthWorker
import com.notificationsaver.app.worker.PingWorker
import com.notificationsaver.app.worker.SendTelegramWorker
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ACTIONS) return
        val pending = goAsync()
        NotificationSaverApp.instance.applicationScope.launch {
            try {
                val enabled = NotificationSaverApp.instance.container.settings.current().listenerShouldRun
                if (enabled) {
                    ForwardNotificationListener.rebind(context)
                }
                HealthWorker.ensurePeriodic(context)
                SendTelegramWorker.ensurePeriodic(context)
                SendTelegramWorker.enqueueImmediate(context)
                val pingOn = NotificationSaverApp.instance.container.settings.current().hourlyPingEnabled
                PingWorker.sync(context, pingOn)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT,
        )
    }
}
