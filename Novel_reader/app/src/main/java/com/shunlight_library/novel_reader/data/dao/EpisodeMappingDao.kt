/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Room DAO for episode_mapping table.
 */
package com.shunlight_library.novel_reader.data.dao

import androidx.room.*
import com.shunlight_library.novel_reader.data.entity.EpisodeMappingEntity

@Dao
interface EpisodeMappingDao {
    /**
     * エピソード番号からカクヨムのエピソードIDを取得
     */
    @Query("SELECT kakuyomu_episode_id FROM episode_mapping WHERE ncode = :ncode AND episode_no = :episodeNo")
    suspend fun getKakuyomuEpisodeId(ncode: String, episodeNo: Int): String?

    /**
     * カクヨムのエピソードIDからエピソード番号を取得
     */
    @Query("SELECT episode_no FROM episode_mapping WHERE ncode = :ncode AND kakuyomu_episode_id = :kakuyomuEpisodeId")
    suspend fun getEpisodeNo(ncode: String, kakuyomuEpisodeId: String): Int?

    /**
     * マッピングを挿入または更新
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: EpisodeMappingEntity)

    /**
     * 複数のマッピングを一括挿入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMappings(mappings: List<EpisodeMappingEntity>)

    /**
     * 指定された小説のマッピングを全て削除
     */
    @Query("DELETE FROM episode_mapping WHERE ncode = :ncode")
    suspend fun deleteMappingsByNcode(ncode: String)

    /**
     * 全てのマッピングを取得（デバッグ用）
     */
    @Query("SELECT * FROM episode_mapping WHERE ncode = :ncode ORDER BY episode_no")
    suspend fun getMappingsByNcode(ncode: String): List<EpisodeMappingEntity>
}
