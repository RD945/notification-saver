package com.notificationsaver.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "npoint_items",
    indices = [Index(value = ["ts"])],
)
data class NpointBinItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val box: String,
)
