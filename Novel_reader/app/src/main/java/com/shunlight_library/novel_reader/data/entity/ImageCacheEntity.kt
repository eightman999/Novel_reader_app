package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 画像のキャッシュを管理するエンティティ。
 * 画像の内容ハッシュをファイル名とし、
 * 元URLと保存先パス、MIMEタイプを保持する。
 */
@Entity(tableName = "image_cache")
data class ImageCacheEntity(
    @PrimaryKey val hash: String,
    val original_url: String,
    val local_path: String,
    val mime_type: String
)
