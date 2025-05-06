package com.shunlight_library.novel_reader.data.dao

import androidx.room.*
import com.shunlight_library.novel_reader.data.entity.UpdateQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateQueueDao {
    @Query("SELECT * FROM update_queue ORDER BY update_time DESC")
    fun getAllUpdateQueue(): Flow<List<UpdateQueueEntity>>

    @Query("SELECT * FROM update_queue WHERE ncode = :ncode")
    suspend fun getUpdateQueueByNcode(ncode: String): UpdateQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpdateQueue(updateQueue: UpdateQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpdateQueues(updateQueues: List<UpdateQueueEntity>)

    @Update
    suspend fun updateUpdateQueue(updateQueue: UpdateQueueEntity)

    @Delete
    suspend fun deleteUpdateQueue(updateQueue: UpdateQueueEntity)

    @Query("DELETE FROM update_queue WHERE ncode = :ncode")
    suspend fun deleteUpdateQueueByNcode(ncode: String)

    @Query("SELECT * FROM update_queue")
    suspend fun getAllUpdateQueueList(): List<UpdateQueueEntity>
}