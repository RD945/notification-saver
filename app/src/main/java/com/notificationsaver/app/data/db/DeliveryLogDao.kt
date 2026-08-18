package com.notificationsaver.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryLogDao {
    @Insert
    suspend fun insert(log: DeliveryLog): Long

    @Query("SELECT * FROM delivery_logs ORDER BY queuedAt DESC")
    fun observeAll(): Flow<List<DeliveryLog>>

    @Query("SELECT * FROM delivery_logs WHERE status = 'QUEUED' ORDER BY queuedAt ASC LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<DeliveryLog>

    @Query("SELECT COUNT(*) FROM delivery_logs WHERE status = 'QUEUED'")
    suspend fun queuedCount(): Int

    @Query(
        """
        UPDATE delivery_logs
        SET status = :status, error = :error, retryCount = retryCount + :retryDelta
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(id: Long, status: String, error: String?, retryDelta: Int)

    @Query("UPDATE delivery_logs SET status = 'QUEUED', error = NULL WHERE id = :id")
    suspend fun requeue(id: Long)

    @Query("DELETE FROM delivery_logs")
    suspend fun clear()

    @Query(
        """
        DELETE FROM delivery_logs
        WHERE id NOT IN (SELECT id FROM delivery_logs ORDER BY queuedAt DESC LIMIT :keep)
        """,
    )
    suspend fun trim(keep: Int)
}
