package com.notificationsaver.app.data.npoint

import android.net.Uri
import com.notificationsaver.app.data.telegram.SendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NpointSender(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    suspend fun post(
        url: String,
        bearer: String,
        encodeKey: String,
        items: List<NpointItemPayload>,
    ): SendResult = withContext(Dispatchers.IO) {
        val endpoint = sanitizeUrl(url) ?: return@withContext SendResult.Failed(INVALID_URL, retryable = false)
        val body = document(encodeKey, items).toString().toRequestBody(JSON)
        val builder = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Content-Type", "application/json")
        val token = bearer.trim()
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        runCatching {
            client.newCall(builder.build()).execute().use { response ->
                when {
                    response.isSuccessful -> SendResult.Ok
                    response.code == 429 -> SendResult.RetryAfter(30)
                    response.code in 500..599 ->
                        SendResult.Failed("npoint is unavailable (HTTP ${response.code}). Try again.", retryable = true)
                    response.code == 401 -> SendResult.Failed(UNAUTHORIZED, retryable = false)
                    response.code == 402 -> SendResult.Failed(PAYMENT, retryable = false)
                    response.code == 404 -> SendResult.Failed(NOT_FOUND, retryable = false)
                    else -> SendResult.Failed("npoint error (HTTP ${response.code})", retryable = false)
                }
            }
        }.getOrElse { error ->
            SendResult.Failed(error.message ?: "network error", retryable = true)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val TOKEN = Regex("^[A-Za-z0-9]{8,64}$")
        private val HOSTS = setOf("api.npoint.io", "www.npoint.io", "npoint.io")
        const val ALG = "crypto_box_seal"
        const val INVALID_URL =
            "Paste an npoint API URL such as https://api.npoint.io/5a69d9d34ee340a1d9fd"
        const val UNAUTHORIZED =
            "This bin is owned. Create it while logged out on npoint.io, or paste a bearer token."
        const val PAYMENT =
            "This owned bin needs a premium npoint account. Use an unowned bin instead."
        const val NOT_FOUND =
            "That npoint bin was not found. Check the URL."

        fun sanitizeUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null
            if (TOKEN.matches(trimmed)) {
                return "https://api.npoint.io/$trimmed"
            }
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (host !in HOSTS) return null
            val id = uri.pathSegments.lastOrNull { it.isNotBlank() && it != "docs" } ?: return null
            if (!TOKEN.matches(id)) return null
            return "https://api.npoint.io/$id"
        }

        fun isValidUrl(raw: String): Boolean = sanitizeUrl(raw) != null

        fun document(encodeKey: String, items: List<NpointItemPayload>): JSONObject {
            val array = JSONArray()
            for (item in items) {
                array.put(
                    JSONObject()
                        .put("ts", item.ts)
                        .put("box", item.box),
                )
            }
            return JSONObject()
                .put("v", 1)
                .put("alg", ALG)
                .put("encodeKey", encodeKey)
                .put("items", array)
        }
    }
}

data class NpointItemPayload(
    val ts: Long,
    val box: String,
)
