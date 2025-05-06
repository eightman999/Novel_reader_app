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
    val update_time: String
)