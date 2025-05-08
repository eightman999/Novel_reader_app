package com.shunlight_library.novel_reader.data.dao

import androidx.room.*
import com.shunlight_library.novel_reader.data.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE ncode = :ncode ORDER BY episode_no")
    fun getEpisodesByNcode(ncode: String): Flow<List<EpisodeEntity>>

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
    suspend fun updateReadStatus(ncode: String, episodeNo: String, isRead: Boolean)

    /**
     * エピソードのしおり状態を更新
     */
    @Query("UPDATE episodes SET is_bookmark = :isBookmark WHERE ncode = :ncode AND episode_no = :episodeNo")
    suspend fun updateBookmarkStatus(ncode: String, episodeNo: String, isBookmark: Boolean)

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

    

}