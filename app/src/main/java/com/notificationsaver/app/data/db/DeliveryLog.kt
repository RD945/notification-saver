package com.notificationsaver.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DeliveryStatus {
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
)
