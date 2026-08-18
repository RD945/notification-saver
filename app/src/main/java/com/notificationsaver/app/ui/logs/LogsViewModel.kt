package com.notificationsaver.app.ui.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.data.db.DeliveryLog
import com.notificationsaver.app.worker.SendTelegramWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotificationSaverApp
    private val dao = app.container.database.deliveryLogDao()

    val logs: StateFlow<List<DeliveryLog>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(id: Long) {
        viewModelScope.launch {
            dao.requeue(id)
            SendTelegramWorker.enqueueImmediate(app)
        }
    }

    fun clear() {
        viewModelScope.launch { dao.clear() }
    }
}
