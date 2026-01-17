/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * DAO for new novel registration queue.
 */
package com.shunlight_library.novel_reader.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.shunlight_library.novel_reader.data.entity.RegistrationQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * 新規小説登録キューのデータアクセスオブジェクト
 */
@Dao
interface RegistrationQueueDao {
    @Query("SELECT * FROM registration_queue ORDER BY created_at ASC")
    fun getAll(): Flow<List<RegistrationQueueEntity>>

    @Query("SELECT * FROM registration_queue WHERE status = :status ORDER BY created_at ASC")
    fun getByStatus(status: Int): Flow<List<RegistrationQueueEntity>>

    @Query("SELECT * FROM registration_queue WHERE id = :id")
    suspend fun getById(id: Long): RegistrationQueueEntity?

    @Insert
    suspend fun insert(queue: RegistrationQueueEntity): Long

    @Update
    suspend fun update(queue: RegistrationQueueEntity)

    @Delete
    suspend fun delete(queue: RegistrationQueueEntity)

    @Query("DELETE FROM registration_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM registration_queue WHERE status = :status")
    suspend fun deleteByStatus(status: Int)

    @Query("SELECT COUNT(*) FROM registration_queue WHERE status = 1")
    fun getProcessingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM registration_queue WHERE status = 0")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM registration_queue WHERE status = 1")
    suspend fun getProcessingCountSync(): Int

    @Query("SELECT * FROM registration_queue WHERE status = 0 ORDER BY created_at ASC LIMIT 1")
    suspend fun getNextPendingQueue(): RegistrationQueueEntity?

    @Query("UPDATE registration_queue SET status = :status, error_message = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int, errorMessage: String? = null)

    @Query("UPDATE registration_queue SET title = :title, total_episodes = :totalEpisodes WHERE id = :id")
    suspend fun updateNovelInfo(id: Long, title: String, totalEpisodes: Int)

    @Query("UPDATE registration_queue SET current_episode = :currentEpisode WHERE id = :id")
    suspend fun updateProgress(id: Long, currentEpisode: Int)
}
