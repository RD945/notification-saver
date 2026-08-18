package com.notificationsaver.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.notificationsaver.app.data.crypto.SealedBoxCrypto
import com.notificationsaver.app.data.npoint.NpointSender
import com.notificationsaver.app.data.telegram.TelegramSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val forwardingEnabled: Boolean = true,
    val botToken: String = "",
    val chatId: String = "",
    val allowlist: Set<String> = emptySet(),
    val ignoreTelegram: Boolean = true,
    val hourlyPingEnabled: Boolean = true,
    val npointEnabled: Boolean = false,
    val npointUrl: String = "",
    val npointBearer: String = "",
    val otpOnly: Boolean = false,
    val npointEncodeKey: String = "",
    val npointDecodeKey: String = "",
) {
    val telegramConfigured: Boolean
        get() = botToken.isNotBlank() && chatId.isNotBlank()

    val npointConfigured: Boolean
        get() = NpointSender.isValidUrl(npointUrl) &&
            npointEncodeKey.isNotBlank() &&
            npointDecodeKey.isNotBlank()

    val setupComplete: Boolean
        get() = telegramConfigured || npointConfigured

    val telegramActive: Boolean
        get() = forwardingEnabled && telegramConfigured

    val npointActive: Boolean
        get() = npointEnabled && npointConfigured

    val listenerShouldRun: Boolean
        get() = telegramActive || npointActive
}

class SettingsRepository(
    context: Context,
    scope: CoroutineScope,
    private val crypto: SealedBoxCrypto,
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

    suspend fun setNpointEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_NPOINT_ENABLED] = enabled }
    }

    suspend fun setNpointUrl(url: String) {
        dataStore.edit { it[KEY_NPOINT_URL] = NpointSender.sanitizeUrl(url) ?: url.trim() }
    }

    suspend fun setNpointBearer(token: String) {
        dataStore.edit { it[KEY_NPOINT_BEARER] = token.trim() }
    }

    suspend fun setOtpOnly(enabled: Boolean) {
        dataStore.edit { it[KEY_OTP_ONLY] = enabled }
    }

    suspend fun ensureNpointKeys(): AppSettings {
        val current = current()
        if (current.npointEncodeKey.isNotBlank() && current.npointDecodeKey.isNotBlank()) {
            return current
        }
        return resetNpointKeys()
    }

    suspend fun resetNpointKeys(): AppSettings {
        val pair = crypto.generateKeyPair()
        dataStore.edit { prefs ->
            prefs[KEY_NPOINT_ENCODE] = pair.encodeKey
            prefs[KEY_NPOINT_DECODE] = pair.decodeKey
        }
        return current()
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
        npointEnabled = this[KEY_NPOINT_ENABLED] ?: false,
        npointUrl = this[KEY_NPOINT_URL].orEmpty(),
        npointBearer = this[KEY_NPOINT_BEARER].orEmpty(),
        otpOnly = this[KEY_OTP_ONLY] ?: false,
        npointEncodeKey = this[KEY_NPOINT_ENCODE].orEmpty(),
        npointDecodeKey = this[KEY_NPOINT_DECODE].orEmpty(),
    )

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("forwarding_enabled")
        val KEY_TOKEN = stringPreferencesKey("bot_token")
        val KEY_CHAT = stringPreferencesKey("chat_id")
        val KEY_ALLOWLIST = stringSetPreferencesKey("allowlist")
        val KEY_IGNORE_TG = booleanPreferencesKey("ignore_telegram")
        val KEY_HOURLY_PING = booleanPreferencesKey("hourly_ping")
        val KEY_NPOINT_ENABLED = booleanPreferencesKey("npoint_enabled")
        val KEY_NPOINT_URL = stringPreferencesKey("npoint_url")
        val KEY_NPOINT_BEARER = stringPreferencesKey("npoint_bearer")
        val KEY_OTP_ONLY = booleanPreferencesKey("otp_only")
        val KEY_NPOINT_ENCODE = stringPreferencesKey("npoint_encode_key")
        val KEY_NPOINT_DECODE = stringPreferencesKey("npoint_decode_key")
    }
}
