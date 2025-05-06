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
}