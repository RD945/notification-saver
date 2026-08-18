package com.notificationsaver.app.data.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class SendResult {
    data object Ok : SendResult()
    data class RetryAfter(val seconds: Int) : SendResult()
    data class Failed(val message: String, val retryable: Boolean) : SendResult()
}

class TelegramSender(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    suspend fun send(token: String, chatId: String, text: String): SendResult = withContext(Dispatchers.IO) {
        val cleanToken = sanitizeToken(token)
        val cleanChat = chatId.trim()
        if (!TOKEN_PATTERN.matches(cleanToken)) {
            return@withContext SendResult.Failed(INVALID_TOKEN, retryable = false)
        }
        if (cleanChat.isBlank()) {
            return@withContext SendResult.Failed(INVALID_CHAT, retryable = false)
        }

        val body = JSONObject()
            .put("chat_id", cleanChat)
            .put("text", text.take(4096))
            .put("parse_mode", "HTML")
            .put("disable_web_page_preview", true)
            .toString()
            .toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$cleanToken/sendMessage")
            .post(body)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(payload) }.getOrNull()
                when {
                    response.isSuccessful && json?.optBoolean("ok") == true -> SendResult.Ok
                    response.code == 429 -> {
                        val retry = json
                            ?.optJSONObject("parameters")
                            ?.optInt("retry_after", 30)
                            ?: 30
                        SendResult.RetryAfter(retry.coerceAtLeast(1))
                    }
                    response.code in 500..599 ->
                        SendResult.Failed("Telegram is unavailable (HTTP ${response.code}). Try again.", retryable = true)
                    else -> {
                        val description = json?.optString("description").orEmpty()
                        SendResult.Failed(
                            userMessage(response.code, description),
                            retryable = false,
                        )
                    }
                }
            }
        }.getOrElse { error ->
            SendResult.Failed(error.message ?: "network error", retryable = true)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val TOKEN_PATTERN = Regex("""^\d+:[A-Za-z0-9_-]+$""")
        const val INVALID_TOKEN =
            "Bot token is invalid. Paste the token from @BotFather without the word bot."
        const val INVALID_CHAT =
            "Chat ID is wrong. Send a message to the bot, then copy the chat ID."
        private const val FORBIDDEN =
            "Open Telegram and tap Start on this bot first."

        fun sanitizeToken(raw: String): String {
            var token = raw.trim().trimStart('/')
            if (token.startsWith("bot", ignoreCase = true)) {
                token = token.drop(3).trimStart()
            }
            return token.trim()
        }

        fun userMessage(code: Int, description: String): String {
            val detail = description.lowercase()
            return when {
                code == 404 || detail == "not found" -> INVALID_TOKEN
                detail.contains("chat not found") -> INVALID_CHAT
                detail.contains("blocked") || detail.contains("forbidden") -> FORBIDDEN
                description.isNotBlank() -> description
                else -> "Telegram error (HTTP $code)"
            }
        }

        fun formatMessage(appName: String, title: String, text: String): String = buildString {
            append("<b>").append(escape(appName.ifBlank { "Unknown app" })).append("</b>")
            if (title.isNotBlank()) {
                append('\n').append("<i>").append(escape(title)).append("</i>")
            }
            if (text.isNotBlank()) {
                append('\n').append(escape(text))
            }
        }

        private fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
