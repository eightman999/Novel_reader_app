package com.shunlight_library.novel_reader.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shunlight_library.novel_reader.data.entity.ImageCacheEntity

/**
 * 画像キャッシュテーブルへのアクセスを提供するDAO。
 */
@Dao
interface ImageCacheDao {
    @Query("SELECT * FROM image_cache WHERE hash = :hash")
    suspend fun getImageByHash(hash: String): ImageCacheEntity?

    @Query("SELECT * FROM image_cache WHERE original_url = :url LIMIT 1")
    suspend fun getImageByUrl(url: String): ImageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ImageCacheEntity)
}
