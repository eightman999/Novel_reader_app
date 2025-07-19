/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * 更新待ち情報を保持するエンティティ。
 */
package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "update_queue",
    primaryKeys = ["ncode"],
    indices = [Index(value = ["update_time"], name = "idx_update_queue_time")]
)
/**
 * 更新キューの 1 行を表す。
 *
 * @property ncode 小説の N コード
 * @property total_ep チェック時点のエピソード総数
 * @property general_all_no チェック時点の評価ポイント総数
 * @property update_time 更新検出日時
 */
data class UpdateQueueEntity(
    val ncode: String,
    val total_ep: Int,
    val general_all_no: Int,
    val update_time: String
)
