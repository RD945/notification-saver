package com.notificationsaver.app.data

import android.content.Context
import com.notificationsaver.app.data.db.AppDatabase
import com.notificationsaver.app.data.telegram.TelegramSender
import kotlinx.coroutines.CoroutineScope

class AppContainer(
    context: Context,
    scope: CoroutineScope,
) {
    val database: AppDatabase = AppDatabase.create(context)
    val settings: SettingsRepository = SettingsRepository(context, scope)
    val telegram: TelegramSender = TelegramSender()
}
