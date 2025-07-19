/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * 各小説の最終読了位置を記録するエンティティ。
 */
package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "last_read_novel", // 注意: 仕様通りの "rast" を使用
    primaryKeys = ["ncode"],
    indices = [Index(value = ["ncode", "date"], name = "idx_last_read")]
)
/**
 * 最終読了エピソードを保持する。
 *
 * @property ncode 対象小説の N コード
 * @property date 読了日時（ISO 8601 形式）
 * @property episode_no 最後に読んだエピソード番号
 */
data class LastReadNovelEntity(
    val ncode: String,
    val date: String,
    val episode_no: Int
)
