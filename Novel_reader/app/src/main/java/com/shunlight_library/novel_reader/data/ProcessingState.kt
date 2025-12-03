/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * Data class representing the state of a novel processing operation.
 */
package com.shunlight_library.novel_reader.data

import androidx.compose.ui.graphics.Color

/**
 * 処理ステータスタイプ
 */
enum class ProcessingStatusType(val displayName: String, val color: Color) {
    ERROR("エラー", Color.Red),           // エラー: 赤
    RETRY("再試行", Color.Yellow),        // 再試行: 黄色
    CHECK("更新確認", Color.Green),       // 更新確認: 緑
    IDLE("アイドル", Color.Gray),         // アイドル: 灰色
    FETCHING("取得中", Color.Cyan)        // 取得: 水色
}

/**
 * 小説の更新・取得処理の状態を表すデータクラス
 *
 * @param id 処理のユニークID（ncode）
 * @param novelTitle 小説のタイトル
 * @param progress 進捗率（0.0-1.0）
 * @param currentEpisode 現在処理中のエピソード番号
 * @param totalEpisodes 総エピソード数
 * @param statusType 処理の状態タイプ
 * @param startTime 処理開始時刻（ミリ秒）
 * @param errorMessage エラーメッセージ（エラー時のみ）
 */
data class ProcessingState(
    val id: String,
    val novelTitle: String,
    val progress: Float = 0.0f,
    val currentEpisode: Int = 0,
    val totalEpisodes: Int = 0,
    val statusType: ProcessingStatusType = ProcessingStatusType.FETCHING,
    val startTime: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    companion object {
        // 後方互換性のための定数
        @Deprecated("Use ProcessingStatusType.FETCHING instead")
        const val STATUS_PROCESSING = "取得中"
        @Deprecated("Use ProcessingStatusType.IDLE or completion logic instead")
        const val STATUS_COMPLETED = "完了"
        @Deprecated("Use ProcessingStatusType.ERROR instead")
        const val STATUS_ERROR = "エラー"
    }

    /**
     * 進捗率を計算
     */
    fun calculateProgress(): Float {
        return if (totalEpisodes > 0) {
            currentEpisode.toFloat() / totalEpisodes.toFloat()
        } else {
            0.0f
        }
    }

    /**
     * 経過時間を計算（秒）
     */
    fun getElapsedSeconds(): Long {
        return (System.currentTimeMillis() - startTime) / 1000
    }
}
