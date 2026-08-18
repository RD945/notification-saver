package com.notificationsaver.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.notificationsaver.app.data.telegram.TelegramSender

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val forwardingEnabled: Boolean = true,
    val botToken: String = "",
    val chatId: String = "",
    val allowlist: Set<String> = emptySet(),
    val ignoreTelegram: Boolean = true,
    val hourlyPingEnabled: Boolean = true,
) {
    val telegramConfigured: Boolean
        get() = botToken.isNotBlank() && chatId.isNotBlank()
}

class SettingsRepository(
    context: Context,
    scope: CoroutineScope,
) {
    private val dataStore = context.applicationContext.dataStore

    val snapshot: StateFlow<AppSettings> = dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val ready: StateFlow<Boolean> = dataStore.data
        .map { true }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    val cached: AppSettings
        get() = snapshot.value

    suspend fun current(): AppSettings = settings.first()

    suspend fun setForwardingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setBotToken(token: String) {
        dataStore.edit { it[KEY_TOKEN] = TelegramSender.sanitizeToken(token) }
    }

    suspend fun setChatId(chatId: String) {
        dataStore.edit { it[KEY_CHAT] = chatId.trim() }
    }

    suspend fun setIgnoreTelegram(ignore: Boolean) {
        dataStore.edit { it[KEY_IGNORE_TG] = ignore }
    }

    suspend fun setHourlyPingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_HOURLY_PING] = enabled }
    }

    suspend fun setPackageAllowed(packageName: String, allowed: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_ALLOWLIST].orEmpty().toMutableSet()
            if (allowed) current.add(packageName) else current.remove(packageName)
            prefs[KEY_ALLOWLIST] = current
        }
    }

    suspend fun replaceAllowlist(packages: Set<String>) {
        dataStore.edit { it[KEY_ALLOWLIST] = packages }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        forwardingEnabled = this[KEY_ENABLED] ?: true,
        botToken = this[KEY_TOKEN].orEmpty(),
        chatId = this[KEY_CHAT].orEmpty(),
        allowlist = this[KEY_ALLOWLIST].orEmpty(),
        ignoreTelegram = this[KEY_IGNORE_TG] ?: true,
        hourlyPingEnabled = this[KEY_HOURLY_PING] ?: true,
    )

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("forwarding_enabled")
        val KEY_TOKEN = stringPreferencesKey("bot_token")
        val KEY_CHAT = stringPreferencesKey("chat_id")
        val KEY_ALLOWLIST = stringSetPreferencesKey("allowlist")
        val KEY_IGNORE_TG = booleanPreferencesKey("ignore_telegram")
        val KEY_HOURLY_PING = booleanPreferencesKey("hourly_ping")
    }
}
