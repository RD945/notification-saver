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
import com.notificationsaver.app.data.db.DeliveryLog
import com.notificationsaver.app.data.db.DestStatus
import com.notificationsaver.app.data.db.NpointBinItem
import com.notificationsaver.app.data.npoint.NpointItemPayload
import com.notificationsaver.app.data.telegram.SendResult
import com.notificationsaver.app.data.telegram.TelegramSender
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SendTelegramWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = NotificationSaverApp.instance.container
        val settings = container.settings.cached
        val dao = container.database.deliveryLogDao()
        val npointDao = container.database.npointItemDao()

        if (!settings.telegramConfigured && !settings.npointConfigured) {
            return Result.success()
        }

        val batch = dao.nextQueued(BATCH_SIZE)
        if (batch.isEmpty()) {
            dao.trim(MAX_LOGS)
            return Result.success()
        }

        for (item in batch) {
            var telegramStatus = item.telegramStatus
            var npointStatus = item.npointStatus
            val errors = mutableListOf<String>()

            if (telegramStatus == DestStatus.QUEUED.name) {
                if (!settings.telegramActive) {
                    telegramStatus = DestStatus.SKIPPED.name
                } else {
                    val text = TelegramSender.formatMessage(item.appName, item.title, item.text)
                    when (val result = container.telegram.send(settings.botToken, settings.chatId, text)) {
                        SendResult.Ok -> telegramStatus = DestStatus.SENT.name
                        is SendResult.RetryAfter -> {
                            enqueueDelayed(applicationContext, result.seconds.toLong())
                            return Result.success()
                        }
                        is SendResult.Failed -> {
                            errors += result.message
                            if (result.retryable) {
                                persist(dao, item, telegramStatus, npointStatus, errors, retry = true)
                                return Result.retry()
                            }
                            telegramStatus = DestStatus.FAILED.name
                        }
                    }
                }
            }

            if (npointStatus == DestStatus.QUEUED.name) {
                if (!settings.npointActive) {
                    npointStatus = DestStatus.SKIPPED.name
                } else {
                    val sealed = runCatching {
                        container.crypto.seal(plaintext(item), settings.npointEncodeKey)
                    }
                    if (sealed.isFailure) {
                        npointStatus = DestStatus.FAILED.name
                        errors += sealed.exceptionOrNull()?.message ?: "encrypt failed"
                        persist(dao, item, telegramStatus, npointStatus, errors, retry = false)
                    } else {
                        val box = sealed.getOrThrow()
                        val pending = npointDao.all().map { NpointItemPayload(it.ts, it.box) } +
                            NpointItemPayload(item.postedAt, box)
                        val capped = pending.takeLast(MAX_NPOINT)
                        when (
                            val result = container.npoint.post(
                                settings.npointUrl,
                                settings.npointBearer,
                                settings.npointEncodeKey,
                                capped,
                            )
                        ) {
                            SendResult.Ok -> {
                                npointDao.insert(NpointBinItem(ts = item.postedAt, box = box))
                                npointDao.trim(MAX_NPOINT)
                                npointStatus = DestStatus.SENT.name
                            }
                            is SendResult.RetryAfter -> {
                                persist(dao, item, telegramStatus, npointStatus, errors, retry = false)
                                enqueueDelayed(applicationContext, result.seconds.toLong())
                                return Result.success()
                            }
                            is SendResult.Failed -> {
                                errors += result.message
                                if (result.retryable) {
                                    persist(dao, item, telegramStatus, npointStatus, errors, retry = true)
                                    return Result.retry()
                                }
                                npointStatus = DestStatus.FAILED.name
                            }
                        }
                    }
                }
            }

            persist(dao, item, telegramStatus, npointStatus, errors, retry = false)
        }

        if (dao.queuedCount() > 0) {
            enqueueImmediate(applicationContext)
        }
        dao.trim(MAX_LOGS)
        return Result.success()
    }

    private suspend fun persist(
        dao: com.notificationsaver.app.data.db.DeliveryLogDao,
        item: DeliveryLog,
        telegramStatus: String,
        npointStatus: String,
        errors: List<String>,
        retry: Boolean,
    ) {
        val updated = item.copy(telegramStatus = telegramStatus, npointStatus = npointStatus)
        dao.updateDelivery(
            id = item.id,
            telegramStatus = telegramStatus,
            npointStatus = npointStatus,
            status = updated.overallStatus(),
            error = errors.lastOrNull(),
            retryDelta = if (retry) 1 else 0,
        )
    }

    private fun plaintext(item: DeliveryLog): String = JSONObject()
        .put("packageName", item.packageName)
        .put("appName", item.appName)
        .put("title", item.title)
        .put("text", item.text)
        .put("otp", item.otp ?: JSONObject.NULL)
        .put("postedAt", item.postedAt)
        .toString()

    companion object {
        private const val UNIQUE_IMMEDIATE = "send-telegram-immediate"
        private const val UNIQUE_PERIODIC = "send-telegram-periodic"
        private const val BATCH_SIZE = 5
        private const val MAX_LOGS = 500
        const val MAX_NPOINT = 50

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
