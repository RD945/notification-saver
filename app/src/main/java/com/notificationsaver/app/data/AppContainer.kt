package com.notificationsaver.app.data

import android.content.Context
import com.notificationsaver.app.data.crypto.SealedBoxCrypto
import com.notificationsaver.app.data.db.AppDatabase
import com.notificationsaver.app.data.npoint.NpointSender
import com.notificationsaver.app.data.telegram.TelegramSender
import kotlinx.coroutines.CoroutineScope

class AppContainer(
    context: Context,
    scope: CoroutineScope,
) {
    val database: AppDatabase = AppDatabase.create(context)
    val crypto: SealedBoxCrypto = SealedBoxCrypto()
    val settings: SettingsRepository = SettingsRepository(context, scope, crypto)
    val telegram: TelegramSender = TelegramSender()
    val npoint: NpointSender = NpointSender()
}
