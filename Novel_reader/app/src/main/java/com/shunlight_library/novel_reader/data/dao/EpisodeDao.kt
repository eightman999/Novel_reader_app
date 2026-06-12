/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Room DAO for episodes table.
 */
package com.shunlight_library.novel_reader.data.dao

import androidx.room.*
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import com.shunlight_library.novel_reader.data.entity.EpisodeMeta
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE ncode = :ncode ORDER BY episode_no")
    fun getEpisodesByNcode(ncode: String): Flow<List<EpisodeEntity>>

    /**
     * 一覧表示用の軽量メタデータを取得（本文を含まない）
     * 1000話超の作品でも本文をメモリにロードせずに一覧表示できる
     */
    @Query(
        "SELECT ncode, episode_no, e_title, update_time, is_read, is_bookmark, reading_rate, " +
                "CASE WHEN body = '' THEN 1 ELSE 0 END AS body_empty " +
                "FROM episodes WHERE ncode = :ncode ORDER BY episode_no"
    )
    fun getEpisodeMetasByNcode(ncode: String): Flow<List<EpisodeMeta>>

    @Query("SELECT * FROM episodes WHERE ncode = :ncode AND episode_no = :episodeNo")
    suspend fun getEpisode(ncode: String, episodeNo: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Delete
    suspend fun deleteEpisode(episode: EpisodeEntity)

    @Query("DELETE FROM episodes WHERE ncode = :ncode")
    suspend fun deleteEpisodesByNcode(ncode: String)
// EpisodeDao.kt - 追加メソッド

    /**
     * エピソードの既読状態を更新
     */
    @Query("UPDATE episodes SET is_read = :isRead WHERE ncode = :ncode AND episode_no = :episodeNo")
    suspend fun updateReadStatus(ncode: String, episodeNo: String, isRead: Int)

    /**
     * エピソードのしおり状態を更新
     */
    @Query("UPDATE episodes SET is_bookmark = :isBookmark WHERE ncode = :ncode AND episode_no = :episodeNo")
    suspend fun updateBookmarkStatus(ncode: String, episodeNo: String, isBookmark: Int)

    /**
     * 指定されたエピソードまでを既読に設定
     */
    @Query("UPDATE episodes SET is_read = 1 WHERE ncode = :ncode AND CAST(episode_no AS INTEGER) <= :episodeNo")
    suspend fun markEpisodesAsReadUpTo(ncode: String, episodeNo: Int)

    /**
     * しおりが付いたエピソードを取得
     */
    @Query("SELECT * FROM episodes WHERE ncode = :ncode AND is_bookmark = 1 ORDER BY CAST(episode_no AS INTEGER)")
    fun getBookmarkedEpisodes(ncode: String): Flow<List<EpisodeEntity>>

    /**
     * 既読エピソードを取得
     */
    @Query("SELECT * FROM episodes WHERE ncode = :ncode AND is_read = 1 ORDER BY CAST(episode_no AS INTEGER)")
    fun getReadEpisodes(ncode: String): Flow<List<EpisodeEntity>>

    @Query("UPDATE episodes SET reading_rate = :readingRate WHERE ncode = :ncode AND episode_no = :episodeNo")
    suspend fun updateReadingRate(ncode: String, episodeNo: String, readingRate: Float)

    @Query("SELECT COUNT(*) FROM episodes")
    suspend fun getEpisodeCount(): Int

    @Query("SELECT * FROM episodes WHERE ncode = :ncode AND (body = '' OR e_title = '') ORDER BY CAST(episode_no AS INTEGER)")
    suspend fun getErrorEpisodes(ncode: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE ncode = :ncode ORDER BY CAST(episode_no AS INTEGER)")
    suspend fun getEpisodesByNcodeList(ncode: String): List<EpisodeEntity>

    /**
     * 指定ncodeの最大エピソード番号を取得（リトライ時のレジュームポイント検出用）
     */
    @Query("SELECT MAX(CAST(episode_no AS INTEGER)) FROM episodes WHERE ncode = :ncode")
    suspend fun getMaxEpisodeNo(ncode: String): Int?

    /**
     * 指定ncodeのエピソード数を取得
     */
    @Query("SELECT COUNT(*) FROM episodes WHERE ncode = :ncode")
    suspend fun getEpisodeCountByNcode(ncode: String): Int

    /**
     * エピソードテーブルに存在する全ての異なるncodeを取得
     */
    @Query("SELECT DISTINCT ncode FROM episodes")
    suspend fun getDistinctNcodes(): List<String>

}