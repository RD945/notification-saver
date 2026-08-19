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

    @Query("SELECT COUNT(*) FROM delivery_logs")
    fun observeCount(): Flow<Int>

    @Query("SELECT MAX(queuedAt) FROM delivery_logs")
    fun observeLastQueuedAt(): Flow<Long?>

    @Query(
        """
        UPDATE delivery_logs
        SET telegramStatus = :telegramStatus,
            npointStatus = :npointStatus,
            status = :status,
            error = :error,
            retryCount = retryCount + :retryDelta
        WHERE id = :id
        """,
    )
    suspend fun updateDelivery(
        id: Long,
        telegramStatus: String,
        npointStatus: String,
        status: String,
        error: String?,
        retryDelta: Int,
    )

    @Query(
        """
        UPDATE delivery_logs
        SET telegramStatus = CASE WHEN telegramStatus = 'FAILED' THEN 'QUEUED' ELSE telegramStatus END,
            npointStatus = CASE WHEN npointStatus = 'FAILED' THEN 'QUEUED' ELSE npointStatus END,
            status = 'QUEUED',
            error = NULL
        WHERE id = :id
        """,
    )
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
