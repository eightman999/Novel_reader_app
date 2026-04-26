/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * DAO for novel descriptions.
 */
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
    @Query("SELECT * FROM novels_descs")
    suspend fun getNovelsForUpdate(): List<NovelDescEntity>

    @Query("UPDATE novels_descs SET is_favorite = :isFavorite WHERE ncode = :ncode")
    suspend fun updateFavoriteStatus(ncode: String, isFavorite: Int)

    @Query("SELECT * FROM novels_descs WHERE is_favorite = 1 ORDER BY last_update_date DESC")
    fun getFavoriteNovels(): Flow<List<NovelDescEntity>>

    @Query("SELECT COUNT(*) FROM novels_descs")
    suspend fun getNovelCount(): Int

    @Query("SELECT * FROM novels_descs WHERE ncode IN (:ncodes)")
    suspend fun getNovelsByNcodes(ncodes: List<String>): List<NovelDescEntity>

    @Query("UPDATE novels_descs SET end_flag = :endFlag WHERE ncode = :ncode")
    suspend fun updateEndFlag(ncode: String, endFlag: Int)

    @Query("UPDATE novels_descs SET last_checked_at = :dateTime WHERE ncode = :ncode")
    suspend fun updateLastCheckedAt(ncode: String, dateTime: String)

    @Query("UPDATE novels_descs SET sub_site = :subSite WHERE ncode = :ncode")
    suspend fun updateSubSite(ncode: String, subSite: Int)

    @Query("SELECT * FROM novels_descs WHERE sub_site = :subSite")
    suspend fun getNovelsBySubSite(subSite: Int): List<NovelDescEntity>

}