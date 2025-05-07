// app/src/main/java/com/shunlight_library/novel_reader/data/dao/URLEntityDao.kt
package com.shunlight_library.novel_reader.data.dao

import androidx.room.*
import com.shunlight_library.novel_reader.data.entity.URLEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface URLEntityDao {
    @Query("SELECT * FROM url_entity")
    fun getAllURLs(): Flow<List<URLEntity>>

    @Query("SELECT * FROM url_entity WHERE ncode = :ncode")
    suspend fun getURLByNcode(ncode: String): URLEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertURL(urlEntity: URLEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertURLs(urlEntities: List<URLEntity>)

    @Update
    suspend fun updateURL(urlEntity: URLEntity)

    @Delete
    suspend fun deleteURL(urlEntity: URLEntity)

    @Query("DELETE FROM url_entity WHERE ncode = :ncode")
    suspend fun deleteURLByNcode(ncode: String)
}