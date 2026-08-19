package com.notificationsaver.app.ui.npoint

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notificationsaver.app.NotificationSaverApp
import com.notificationsaver.app.data.db.NpointBinItem
import com.notificationsaver.app.data.npoint.NpointItemPayload
import com.notificationsaver.app.data.npoint.NpointSender
import com.notificationsaver.app.data.telegram.SendResult
import com.notificationsaver.app.ui.components.AppNotice
import com.notificationsaver.app.ui.components.NoticeAction
import com.notificationsaver.app.worker.SendTelegramWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class NpointUiState(
    val url: String = "",
    val bearer: String = "",
    val encodeKey: String = "",
    val decodeKey: String = "",
    val keysEditable: Boolean = true,
    val configured: Boolean = false,
    val notice: AppNotice? = null,
    val busy: Boolean = false,
)

class NpointViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotificationSaverApp
    private val urlDraft = MutableStateFlow<String?>(null)
    private val bearerDraft = MutableStateFlow<String?>(null)
    private val encodeDraft = MutableStateFlow<String?>(null)
    private val decodeDraft = MutableStateFlow<String?>(null)
    private val keysUnlocked = MutableStateFlow(false)
    private val flash = MutableStateFlow<AppNotice?>(null)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<NpointUiState> = combine(
        app.container.settings.snapshot,
        combine(urlDraft, bearerDraft) { url, bearer -> url to bearer },
        combine(encodeDraft, decodeDraft, keysUnlocked) { encode, decode, unlocked ->
            Triple(encode, decode, unlocked)
        },
        flash,
        busy,
    ) { settings, urlBearer, keys, notice, isBusy ->
        val storedKeys = settings.npointEncodeKey.isNotBlank() &&
            settings.npointDecodeKey.isNotBlank()
        NpointUiState(
            url = urlBearer.first ?: settings.npointUrl,
            bearer = urlBearer.second ?: settings.npointBearer,
            encodeKey = keys.first ?: settings.npointEncodeKey,
            decodeKey = keys.second ?: settings.npointDecodeKey,
            keysEditable = !storedKeys || keys.third,
            configured = settings.npointConfigured,
            notice = notice,
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NpointUiState())

    fun onUrlChange(value: String) {
        urlDraft.value = value
    }

    fun onBearerChange(value: String) {
        bearerDraft.value = value
    }

    fun onEncodeChange(value: String) {
        encodeDraft.value = value
    }

    fun onDecodeChange(value: String) {
        decodeDraft.value = value
    }

    fun editKeys() {
        keysUnlocked.value = true
    }

    fun generateKeys() {
        val stored = app.container.settings.cached
        if (stored.npointEncodeKey.isNotBlank() || stored.npointDecodeKey.isNotBlank()) {
            flash.value = AppNotice(
                title = "Generate keys",
                message = "Old npoint items cannot be decrypted with the new pair. Clear the bin after this if other apps should not keep ciphertext they cannot open.",
                actionLabel = "Generate",
                action = NoticeAction.ConfirmResetKeys,
            )
            return
        }
        fillGeneratedKeys()
    }

    fun consumeNotice() {
        flash.value = null
    }

    fun performNoticeAction(action: NoticeAction) {
        when (action) {
            NoticeAction.ConfirmResetKeys -> applyGeneratedKeys()
            NoticeAction.ConfirmClearBin -> clearBin()
            else -> Unit
        }
    }

    fun save() {
        viewModelScope.launch {
            persistDrafts()?.let { return@launch }
            flash.value = AppNotice(
                title = "Saved",
                message = "The bin URL stays on this phone. Keys never go to npoint.",
            )
        }
    }

    fun copyEncodeKey() = copy("encode key", state.value.encodeKey)

    fun copyDecodeKey() = copy("decode key", state.value.decodeKey)

    fun requestClearBin() {
        flash.value = AppNotice(
            title = "Clear bin",
            message = "This replaces the npoint document with an empty encrypted list.",
            actionLabel = "Clear",
            action = NoticeAction.ConfirmClearBin,
        )
    }

    fun testConnection() {
        viewModelScope.launch {
            persistDrafts()?.let { return@launch }
            val settings = app.container.settings.current()
            if (!settings.npointConfigured) {
                flash.value = AppNotice(
                    title = "npoint",
                    message = "Save the npoint API URL and keys first",
                )
                return@launch
            }
            busy.value = true
            val sealed = runCatching {
                app.container.crypto.seal(testPayload(), settings.npointEncodeKey)
            }.getOrElse { error ->
                busy.value = false
                flash.value = AppNotice(title = "npoint", message = error.message ?: "encrypt failed")
                return@launch
            }
            val ts = System.currentTimeMillis()
            val pending = app.container.database.npointItemDao().all()
                .map { NpointItemPayload(it.ts, it.box) } + NpointItemPayload(ts, sealed)
            val capped = pending.takeLast(SendTelegramWorker.MAX_NPOINT)
            val result = app.container.npoint.post(
                settings.npointUrl,
                settings.npointBearer,
                settings.npointEncodeKey,
                capped,
            )
            if (result == SendResult.Ok) {
                app.container.database.npointItemDao().insert(NpointBinItem(ts = ts, box = sealed))
                app.container.database.npointItemDao().trim(SendTelegramWorker.MAX_NPOINT)
            }
            busy.value = false
            flash.value = when (result) {
                SendResult.Ok -> AppNotice(
                    title = "npoint",
                    message = "Test item appended. Other apps can decrypt it with the decode key.",
                )
                is SendResult.RetryAfter -> AppNotice(
                    title = "npoint",
                    message = "Rate limited, retry in ${result.seconds}s",
                )
                is SendResult.Failed -> AppNotice(title = "npoint", message = result.message)
            }
        }
    }

    private fun applyGeneratedKeys() {
        fillGeneratedKeys()
        flash.value = AppNotice(
            title = "Keys generated",
            message = "Save to keep this pair. Old npoint items cannot be decrypted with it. Clear the bin if you want to drop old ciphertext.",
        )
    }

    private fun fillGeneratedKeys() {
        val pair = app.container.crypto.generateKeyPair()
        encodeDraft.value = pair.encodeKey
        decodeDraft.value = pair.decodeKey
        keysUnlocked.value = true
    }

    private suspend fun persistDrafts(): AppNotice? {
        val current = state.value
        if (!NpointSender.isValidUrl(current.url) && current.url.isNotBlank()) {
            val notice = AppNotice(title = "npoint", message = NpointSender.INVALID_URL)
            flash.value = notice
            return notice
        }
        if (current.encodeKey.isBlank() || current.decodeKey.isBlank()) {
            val notice = AppNotice(
                title = "npoint",
                message = "Generate keys or paste encode and decode keys",
            )
            flash.value = notice
            return notice
        }
        val keysError = runCatching {
            app.container.settings.setNpointKeys(current.encodeKey, current.decodeKey)
        }.exceptionOrNull()
        if (keysError != null) {
            val notice = AppNotice(
                title = "npoint",
                message = keysError.message ?: "Invalid keys",
            )
            flash.value = notice
            return notice
        }
        app.container.settings.setNpointUrl(current.url)
        app.container.settings.setNpointBearer(current.bearer)
        urlDraft.value = null
        bearerDraft.value = null
        encodeDraft.value = null
        decodeDraft.value = null
        keysUnlocked.value = false
        return null
    }

    private fun clearBin() {
        viewModelScope.launch {
            val settings = app.container.settings.current()
            if (!settings.npointConfigured) {
                flash.value = AppNotice(
                    title = "npoint",
                    message = "Save the npoint API URL and keys first",
                )
                return@launch
            }
            busy.value = true
            val result = app.container.npoint.post(
                settings.npointUrl,
                settings.npointBearer,
                settings.npointEncodeKey,
                emptyList(),
            )
            if (result == SendResult.Ok) {
                app.container.database.npointItemDao().clear()
            }
            busy.value = false
            flash.value = when (result) {
                SendResult.Ok -> AppNotice(title = "npoint", message = "Bin cleared.")
                is SendResult.RetryAfter -> AppNotice(
                    title = "npoint",
                    message = "Rate limited, retry in ${result.seconds}s",
                )
                is SendResult.Failed -> AppNotice(title = "npoint", message = result.message)
            }
        }
    }

    private fun copy(label: String, value: String) {
        if (value.isBlank()) return
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        flash.value = AppNotice(title = "Copied", message = "The $label is on the clipboard.")
    }

    private fun testPayload(): String = JSONObject()
        .put("packageName", app.packageName)
        .put("appName", "Notification Saver")
        .put("title", "Connection test")
        .put("text", "npoint forwarding is working.")
        .put("otp", JSONObject.NULL)
        .put("postedAt", System.currentTimeMillis())
        .toString()
}
