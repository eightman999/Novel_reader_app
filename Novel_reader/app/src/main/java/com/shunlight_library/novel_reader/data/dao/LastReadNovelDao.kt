package com.shunlight_library.novel_reader.data.dao

import androidx.room.*
import com.shunlight_library.novel_reader.data.entity.LastReadNovelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LastReadNovelDao {
    @Query("SELECT * FROM last_read_novel ORDER BY date DESC")
    fun getAllLastReadNovels(): Flow<List<LastReadNovelEntity>>

    @Query("SELECT * FROM last_read_novel WHERE ncode = :ncode")
    suspend fun getLastReadByNcode(ncode: String): LastReadNovelEntity?

    @Query("SELECT * FROM last_read_novel ORDER BY date DESC LIMIT 1")
    suspend fun getMostRecentlyReadNovel(): LastReadNovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLastRead(lastRead: LastReadNovelEntity)

    @Update
    suspend fun updateLastRead(lastRead: LastReadNovelEntity)

    @Delete
    suspend fun deleteLastRead(lastRead: LastReadNovelEntity)
}