/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * カクヨム用のエピソード番号とIDのマッピングテーブル
 */
package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "episode_mapping",
    primaryKeys = ["ncode", "episode_no"],
    indices = [
        Index(value = ["ncode", "episode_no"], name = "idx_episode_mapping_ncode_no"),
        Index(value = ["ncode", "kakuyomu_episode_id"], name = "idx_episode_mapping_ncode_id")
    ]
)
/**
 * カクヨム小説のエピソード番号とエピソードIDのマッピングを保持する。
 * 小説家になろうの小説には使用されない。
 *
 * @property ncode カクヨム小説のPseudo-Ncode（KK-で始まる）
 * @property episode_no 表示用のエピソード番号（1, 2, 3...の連番）
 * @property kakuyomu_episode_id カクヨムの実際のエピソードID（19桁の数値文字列）
 */
data class EpisodeMappingEntity(
    val ncode: String,
    val episode_no: Int,
    val kakuyomu_episode_id: String
)
