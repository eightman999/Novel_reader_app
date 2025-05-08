package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "episodes",
    primaryKeys = ["ncode", "episode_no"],
    indices = [Index(value = ["ncode", "episode_no"], name = "idx_episodes_ncode")]
)
data class EpisodeEntity(
    val ncode: String,
    val episode_no: String,
    val body: String,
    val e_title: String,
    val update_time: String,
    val is_read: Boolean = false,      // 既読フラグ
    val is_bookmark: Boolean = false,   // しおりフラグ
    val reading_rate: Float = 0f       // 追加：読書の進行度（0.0f～1.0f）
)