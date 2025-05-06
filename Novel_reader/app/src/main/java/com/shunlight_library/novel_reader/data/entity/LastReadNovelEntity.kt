package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "last_read_novel", // 注意: 仕様通りの "rast" を使用
    primaryKeys = ["ncode"],
    indices = [Index(value = ["ncode", "date"], name = "idx_last_read")]
)
data class LastReadNovelEntity(
    val ncode: String,
    val date: String,
    val episode_no: Int
)