/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * 小説のメタデータを保持するエンティティ。
 */
package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "novels_descs",
    primaryKeys = ["ncode"],
    indices = [
        Index(value = ["last_update_date"], name = "idx_novels_last_update"),
        Index(value = ["ncode", "rating", "total_ep", "general_all_no", "updated_at"], name = "idx_novels_update_check"),
        Index(value = ["is_favorite"], name = "idx_novels_favorite"),
        Index(value = ["length"], name = "idx_novels_length"),
        Index(value = ["noveltype"], name = "idx_novels_type")
    ]
)
/**
 * 小説の基本情報をまとめたエンティティ。
 *
 * @property ncode 小説を一意に識別する N コード
 * @property title タイトル
 * @property author 作者名
 * @property Synopsis あらすじ（DB の列名に合わせて先頭大文字）
 * @property main_tag メインタグ
 * @property sub_tag サブタグ
 * @property rating レーティング種別 (1=R18, 2=一般)
 * @property last_update_date 最終更新日
 * @property total_ep エピソード総数
 * @property general_all_no 評価ポイント総数
 * @property updated_at メタデータ更新日時
 * @property is_favorite お気に入り登録フラグ
 */
data class NovelDescEntity(
    val ncode: String,
    val title: String,
    val author: String,
    val Synopsis: String,
    val main_tag: String,
    val sub_tag: String,
    val rating: Int,
    val last_update_date: String,
    val total_ep: Int,
    val general_all_no: Int,
    val userid: String? = null,
    val noveltype: Int? = null,
    val length: Int? = null,
    val updated_at: String,
    val is_favorite: Boolean = false
)

//https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode={ncode}&gzip=5&json
