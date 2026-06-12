/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * エピソード一覧表示用の軽量メタデータ（本文を含まない）。
 */
package com.shunlight_library.novel_reader.data.entity

/**
 * エピソード一覧画面用の軽量DTO。
 *
 * EpisodeEntity から本文（body）を除いた射影。
 * 1000話超の作品でも一覧表示時に本文をメモリへロードしないために使用する。
 *
 * @property ncode このエピソードが属する小説の N コード
 * @property episode_no エピソード番号（1 からの連番）
 * @property e_title エピソードタイトル
 * @property update_time 最終更新日時（yyyy-MM-dd HH:mm:ss）
 * @property is_read 既読フラグ (0=未読, 1=既読)
 * @property is_bookmark しおり登録フラグ (0=未登録, 1=登録済み)
 * @property reading_rate 読了率（0.0～1.0）
 * @property body_empty 本文が空かどうか (0=本文あり, 1=空) — エラー検出用
 */
data class EpisodeMeta(
    val ncode: String,
    val episode_no: String,
    val e_title: String,
    val update_time: String,
    val is_read: Int = 0,
    val is_bookmark: Int = 0,
    val reading_rate: Float = 0f,
    val body_empty: Int = 0
)
