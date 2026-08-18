package com.notificationsaver.app

import android.app.Application
import com.notificationsaver.app.data.AppContainer
import com.notificationsaver.app.worker.HealthWorker
import com.notificationsaver.app.worker.PingWorker
import com.notificationsaver.app.worker.SendTelegramWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationSaverApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this, applicationScope)
        HealthWorker.ensurePeriodic(this)
        SendTelegramWorker.ensurePeriodic(this)
        applicationScope.launch {
            PingWorker.sync(this@NotificationSaverApp, container.settings.current().hourlyPingEnabled)
        }
    }

    companion object {
        lateinit var instance: NotificationSaverApp
            private set
    }
}
