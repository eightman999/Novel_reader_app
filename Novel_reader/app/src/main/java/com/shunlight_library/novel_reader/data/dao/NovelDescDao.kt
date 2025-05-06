package com.shunlight_library.novel_reader.data.dao

import androidx.room.*
import com.shunlight_library.novel_reader.data.entity.NovelDescEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDescDao {
    @Query("SELECT * FROM novels_descs ORDER BY last_update_date DESC")
    fun getAllNovels(): Flow<List<NovelDescEntity>>

    @Query("SELECT * FROM novels_descs WHERE ncode = :ncode")
    suspend fun getNovelByNcode(ncode: String): NovelDescEntity?

    @Query("SELECT * FROM novels_descs WHERE main_tag = :tag OR sub_tag LIKE '%' || :tag || '%'")
    fun getNovelsByTag(tag: String): Flow<List<NovelDescEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: NovelDescEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovels(novels: List<NovelDescEntity>)

    @Update
    suspend fun updateNovel(novel: NovelDescEntity)

    @Delete
    suspend fun deleteNovel(novel: NovelDescEntity)

    @Query("SELECT * FROM novels_descs ORDER BY last_update_date DESC LIMIT :limit")
    fun getRecentlyUpdatedNovels(limit: Int): Flow<List<NovelDescEntity>>
    @Query("SELECT * FROM novels_descs WHERE rating IN (1, 2, 3) OR rating IS NULL")
    suspend fun getNovelsForUpdate(): List<NovelDescEntity>

}