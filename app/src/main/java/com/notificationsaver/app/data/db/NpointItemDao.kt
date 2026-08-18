package com.notificationsaver.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NpointItemDao {
    @Insert
    suspend fun insert(item: NpointBinItem): Long

    @Query("SELECT * FROM npoint_items ORDER BY ts ASC, id ASC")
    suspend fun all(): List<NpointBinItem>

    @Query("DELETE FROM npoint_items")
    suspend fun clear()

    @Query(
        """
        DELETE FROM npoint_items
        WHERE id NOT IN (SELECT id FROM npoint_items ORDER BY ts DESC, id DESC LIMIT :keep)
        """,
    )
    suspend fun trim(keep: Int)
}
