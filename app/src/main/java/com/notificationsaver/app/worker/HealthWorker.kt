package com.notificationsaver.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.data.ListenerStatus
import com.notificationsaver.app.service.ForwardNotificationListener
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class HealthWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = runCatching {
            NotificationSaverApp.instance.container.settings.current()
        }.getOrNull() ?: return Result.success()

        if (!settings.listenerShouldRun) return Result.success()
        if (!ForwardNotificationListener.hasAccess(applicationContext)) return Result.success()
        if (ListenerStatus.connected.value) return Result.success()

        ForwardNotificationListener.rebind(applicationContext)
        delay(2_000)
        if (!ListenerStatus.connected.value) {
            ForwardNotificationListener.bounce(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "listener-health"

        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
