package com.notificationsaver.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.data.telegram.SendResult
import com.notificationsaver.app.data.telegram.TelegramSender
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class PingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = NotificationSaverApp.instance.container
        val settings = container.settings.current()
        if (!settings.hourlyPingEnabled || !settings.telegramConfigured) {
            return Result.success()
        }
        val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        return when (
            container.telegram.send(
                settings.botToken,
                settings.chatId,
                TelegramSender.formatMessage(
                    "Notification Saver",
                    "Hourly ping",
                    whenText,
                ),
            )
        ) {
            SendResult.Ok -> Result.success()
            is SendResult.RetryAfter -> Result.retry()
            is SendResult.Failed -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE = "telegram-hourly-ping"

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PingWorker>(1, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }

        fun sync(context: Context, enabled: Boolean) {
            if (enabled) ensurePeriodic(context) else cancel(context)
        }
    }
}
