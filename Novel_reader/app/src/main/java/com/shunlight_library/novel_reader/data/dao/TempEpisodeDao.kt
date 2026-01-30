/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * DAO for temporary episode staging during downloads.
 */
package com.shunlight_library.novel_reader.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shunlight_library.novel_reader.data.entity.TempEpisodeEntity

/**
 * 一時エピソードテーブルのデータアクセスオブジェクト。
 *
 * ダウンロード中のエピソードを一時保存し、
 * 完了/タイムアウト時に本体テーブルへ統合するために使用。
 */
@Dao
interface TempEpisodeDao {
    /**
     * 一時エピソードを挿入（既存データは置換）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: TempEpisodeEntity)

    /**
     * 一時エピソードを一括挿入（既存データは置換）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<TempEpisodeEntity>)

    /**
     * 指定ncodeの一時エピソード一覧を取得
     */
    @Query("SELECT * FROM temp_episodes WHERE ncode = :ncode ORDER BY CAST(episode_no AS INTEGER) ASC")
    suspend fun getByNcode(ncode: String): List<TempEpisodeEntity>

    /**
     * 指定ncodeの一時エピソード数を取得
     */
    @Query("SELECT COUNT(*) FROM temp_episodes WHERE ncode = :ncode")
    suspend fun getCountByNcode(ncode: String): Int

    /**
     * 指定キューIDの一時エピソード一覧を取得
     */
    @Query("SELECT * FROM temp_episodes WHERE queue_id = :queueId ORDER BY CAST(episode_no AS INTEGER) ASC")
    suspend fun getByQueueId(queueId: Long): List<TempEpisodeEntity>

    /**
     * 指定キューIDの一時エピソード数を取得
     */
    @Query("SELECT COUNT(*) FROM temp_episodes WHERE queue_id = :queueId")
    suspend fun getCountByQueueId(queueId: Long): Int

    /**
     * 指定ncodeの一時エピソードを全て削除
     */
    @Query("DELETE FROM temp_episodes WHERE ncode = :ncode")
    suspend fun deleteByNcode(ncode: String)

    /**
     * 指定キューIDの一時エピソードを全て削除
     */
    @Query("DELETE FROM temp_episodes WHERE queue_id = :queueId")
    suspend fun deleteByQueueId(queueId: Long)

    /**
     * 全ての一時エピソードを削除
     */
    @Query("DELETE FROM temp_episodes")
    suspend fun deleteAll()

    /**
     * 指定ncodeの最大エピソード番号を取得（リトライ時の続きから取得用）
     */
    @Query("SELECT MAX(CAST(episode_no AS INTEGER)) FROM temp_episodes WHERE ncode = :ncode")
    suspend fun getMaxEpisodeNo(ncode: String): Int?
}
