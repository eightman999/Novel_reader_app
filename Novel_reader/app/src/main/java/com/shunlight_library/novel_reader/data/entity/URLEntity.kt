/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * N コードと各種 URL を紐付けるエンティティ。
 */
package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "url_entity",
    indices = [Index(value = ["ncode"], name = "idx_url_entity_ncode")]
)
/**
 * 小説の API URL と Web URL を保持する。
 *
 * @property ncode 主キーとなる N コード
 * @property api_url 小説情報取得用 API の URL
 * @property url 閲覧用 Web ページの URL
 * @property is_r18 R18 作品かどうか (0=一般, 1=R18)
 */
data class URLEntity(
    @PrimaryKey val ncode: String,
    val api_url: String,
    val url: String,
    val is_r18: Int = 0  // 0=一般, 1=R18
)
