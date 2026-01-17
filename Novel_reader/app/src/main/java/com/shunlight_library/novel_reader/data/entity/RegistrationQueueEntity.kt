/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Entity for new novel registration queue.
 */
package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 新規小説登録キューのエンティティ
 *
 * 同時処理数（2件）の制限を実現するために、登録要求をキュー管理する。
 * 処理完了後は即時削除される。
 */
@Entity(
    tableName = "registration_queue",
    indices = [
        Index(name = "idx_registration_queue_status", value = ["status"]),
        Index(name = "idx_registration_queue_ncode", value = ["ncode"]),
        Index(name = "idx_registration_queue_created_at", value = ["created_at"])
    ]
)
data class RegistrationQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val ncode: String,                    // 小説ID（Pseudo-Ncode含む）
    val site_type: Int,                   // サイト種別（1=小説家になろう, 2=カクヨム）
    val title: String,                    // 小説名（取得後に更新）
    val url: String,                      // 元のURL
    val is_r18: Boolean,                  // R18フラグ

    // ステータス（0=待機中, 1=処理中, 2=完了, 3=エラー）
    val status: Int,

    val current_episode: Int,             // 現在のエピソード数
    val total_episodes: Int,              // 総エピソード数
    val error_message: String?,           // エラーメッセージ

    val created_at: String,               // 作成日時（yyyy-MM-dd HH:mm:ss）
    val started_at: String?,              // 開始日時（yyyy-MM-dd HH:mm:ss）
    val completed_at: String?             // 完了日時（yyyy-MM-dd HH:mm:ss）
) {
    companion object {
        const val STATUS_PENDING = 0      // 待機中
        const val STATUS_PROCESSING = 1   // 処理中
        const val STATUS_COMPLETED = 2    // 完了
        const val STATUS_ERROR = 3        // エラー
    }
}
