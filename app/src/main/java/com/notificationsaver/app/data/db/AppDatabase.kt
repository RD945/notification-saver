package com.notificationsaver.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DeliveryLog::class, NpointBinItem::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deliveryLogDao(): DeliveryLogDao
    abstract fun npointItemDao(): NpointItemDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "notification-saver.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
