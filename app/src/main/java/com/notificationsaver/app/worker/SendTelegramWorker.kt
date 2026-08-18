package com.notificationsaver.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.data.db.DeliveryStatus
import com.notificationsaver.app.data.telegram.SendResult
import com.notificationsaver.app.data.telegram.TelegramSender
import java.util.concurrent.TimeUnit

class SendTelegramWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = NotificationSaverApp.instance.container
        val settings = container.settings.cached
        val dao = container.database.deliveryLogDao()

        if (!settings.telegramConfigured) {
            return Result.success()
        }

        val batch = dao.nextQueued(BATCH_SIZE)
        if (batch.isEmpty()) {
            dao.trim(MAX_LOGS)
            return Result.success()
        }

        for (item in batch) {
            val text = TelegramSender.formatMessage(item.appName, item.title, item.text)
            when (val result = container.telegram.send(settings.botToken, settings.chatId, text)) {
                SendResult.Ok -> dao.updateStatus(item.id, DeliveryStatus.SENT.name, null, 0)
                is SendResult.RetryAfter -> {
                    enqueueDelayed(applicationContext, result.seconds.toLong())
                    return Result.success()
                }
                is SendResult.Failed -> {
                    if (result.retryable) {
                        dao.updateStatus(item.id, DeliveryStatus.QUEUED.name, result.message, 1)
                        return Result.retry()
                    }
                    dao.updateStatus(item.id, DeliveryStatus.FAILED.name, result.message, 1)
                }
            }
        }

        if (dao.queuedCount() > 0) {
            enqueueImmediate(applicationContext)
        }
        dao.trim(MAX_LOGS)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_IMMEDIATE = "send-telegram-immediate"
        private const val UNIQUE_PERIODIC = "send-telegram-periodic"
        private const val BATCH_SIZE = 5
        private const val MAX_LOGS = 500

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun cancelImmediate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMMEDIATE)
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<SendTelegramWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        fun enqueueDelayed(context: Context, seconds: Long) {
            val request = OneTimeWorkRequestBuilder<SendTelegramWorker>()
                .setInitialDelay(seconds.coerceAtLeast(1), TimeUnit.SECONDS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SendTelegramWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
