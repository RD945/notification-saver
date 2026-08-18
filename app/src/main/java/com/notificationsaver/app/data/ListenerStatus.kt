package com.notificationsaver.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ListenerStatus {
    private val connectedState = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = connectedState.asStateFlow()

    fun setConnected(value: Boolean) {
        connectedState.value = value
    }
}

object NotificationDeduplicator {
    private const val WINDOW_MS = 3_000L
    private const val MAX_KEYS = 200
    private val recent = LinkedHashMap<String, Long>(MAX_KEYS, 0.75f, true)

    @Synchronized
    fun isDuplicate(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val iterator = recent.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > WINDOW_MS) iterator.remove()
        }
        if (recent.containsKey(key)) return true
        recent[key] = now
        while (recent.size > MAX_KEYS) {
            val oldest = recent.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            } else {
                break
            }
        }
        return false
    }
}
