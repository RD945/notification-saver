package com.notificationsaver.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DeliveryStatus {
    QUEUED,
    SENT,
    FAILED,
}

enum class DestStatus {
    SKIPPED,
    QUEUED,
    SENT,
    FAILED,
}

@Entity(
    tableName = "delivery_logs",
    indices = [
        Index(value = ["status"]),
        Index(value = ["queuedAt"]),
    ],
)
data class DeliveryLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val queuedAt: Long,
    val status: String,
    val error: String? = null,
    val retryCount: Int = 0,
    val notificationKey: String,
    val otp: String? = null,
    val telegramStatus: String = DestStatus.SKIPPED.name,
    val npointStatus: String = DestStatus.SKIPPED.name,
) {
    fun overallStatus(): String {
        val dests = listOf(telegramStatus, npointStatus)
        if (dests.any { it == DestStatus.QUEUED.name }) return DeliveryStatus.QUEUED.name
        if (dests.any { it == DestStatus.FAILED.name }) return DeliveryStatus.FAILED.name
        if (dests.all { it == DestStatus.SKIPPED.name }) return DeliveryStatus.FAILED.name
        return DeliveryStatus.SENT.name
    }
}
