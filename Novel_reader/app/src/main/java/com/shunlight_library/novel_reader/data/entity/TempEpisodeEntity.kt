/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * 一時エピソードエンティティ（ダウンロード中のステージング用）
 */
package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * ダウンロード中のエピソードを一時保存するためのエンティティ。
 *
 * エピソード取得中はこのテーブルに保存し、
 * 完了またはタイムアウト時に本体の episodes テーブルに統合する。
 * これにより、タイムアウト時にも途中までのデータを保持し、リトライ時に続きから取得できる。
 *
 * @property ncode このエピソードが属する小説の N コード
 * @property episode_no エピソード番号（1 からの連番）
 * @property body 本文 HTML
 * @property e_title エピソードタイトル
 * @property update_time 最終更新日時（yyyy-MM-dd HH:mm:ss）
 * @property is_read 既読フラグ (0=未読, 1=既読)
 * @property is_bookmark しおり登録フラグ (0=未登録, 1=登録済み)
 * @property reading_rate 読了率（0.0～1.0）
 * @property queue_id 関連する登録キューID
 */
@Entity(
    tableName = "temp_episodes",
    primaryKeys = ["ncode", "episode_no"],
    indices = [
        Index(value = ["ncode", "episode_no"], name = "idx_temp_episodes_ncode"),
        Index(value = ["queue_id"], name = "idx_temp_episodes_queue_id")
    ]
)
data class TempEpisodeEntity(
    val ncode: String,
    val episode_no: String,
    val body: String,
    val e_title: String,
    val update_time: String,
    val is_read: Int = 0,
    val is_bookmark: Int = 0,
    val reading_rate: Float = 0f,
    val queue_id: Long = 0          // 関連する登録キューID
) {
    /**
     * 本体テーブル用の EpisodeEntity に変換する
     */
    fun toEpisodeEntity(): EpisodeEntity {
        return EpisodeEntity(
            ncode = ncode,
            episode_no = episode_no,
            body = body,
            e_title = e_title,
            update_time = update_time,
            is_read = is_read,
            is_bookmark = is_bookmark,
            reading_rate = reading_rate
        )
    }

    companion object {
        /**
         * EpisodeEntity から TempEpisodeEntity に変換する
         */
        fun fromEpisodeEntity(episode: EpisodeEntity, queueId: Long): TempEpisodeEntity {
            return TempEpisodeEntity(
                ncode = episode.ncode,
                episode_no = episode.episode_no,
                body = episode.body,
                e_title = episode.e_title,
                update_time = episode.update_time,
                is_read = episode.is_read,
                is_bookmark = episode.is_bookmark,
                reading_rate = episode.reading_rate,
                queue_id = queueId
            )
        }
    }
}
