/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * 小説のエピソード本文を保持するエンティティ。
 */
package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "episodes",
    primaryKeys = ["ncode", "episode_no"],
    indices = [
        Index(value = ["ncode", "episode_no"], name = "idx_episodes_ncode"),
        Index(value = ["is_read"], name = "idx_episodes_is_read"),
        Index(value = ["is_bookmark"], name = "idx_episodes_is_bookmark"),
        Index(value = ["ncode", "is_read"], name = "idx_episodes_ncode_read"),
        Index(value = ["ncode", "is_bookmark"], name = "idx_episodes_ncode_bookmark")
    ]
)
/**
 * 1 話分の情報を表すエンティティ。
 *
 * @property ncode このエピソードが属する小説の N コード
 * @property episode_no エピソード番号（1 からの連番）
 * @property body 本文 HTML
 * @property e_title エピソードタイトル
 * @property update_time 最終更新日時（yyyy-MM-dd HH:mm:ss）
 * @property is_read 既読フラグ
 * @property is_bookmark しおり登録フラグ
 * @property reading_rate 読了率（0.0～1.0）
 */
data class EpisodeEntity(
    val ncode: String,
    val episode_no: String,
    val body: String,
    val e_title: String,
    val update_time: String,
    val is_read: Boolean = false,
    val is_bookmark: Boolean = false,
    val reading_rate: Float = 0f
)
