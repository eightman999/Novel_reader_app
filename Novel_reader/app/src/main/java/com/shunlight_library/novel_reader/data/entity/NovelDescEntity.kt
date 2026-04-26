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
        Index(value = ["noveltype"], name = "idx_novels_type"),
        Index(value = ["site_type"], name = "idx_novels_site"),
        Index(value = ["registered_at"], name = "idx_novels_registered"),
        Index(value = ["total_ep"], name = "idx_novels_total_ep"),
        Index(value = ["author"], name = "idx_novels_author"),
        Index(value = ["title"], name = "idx_novels_title"),
        Index(value = ["site_type", "is_favorite"], name = "idx_novels_site_favorite"),
        Index(value = ["sub_site"], name = "idx_novels_sub_site"),
        Index(value = ["end_flag"], name = "idx_novels_end_flag"),
        Index(value = ["last_checked_at"], name = "idx_novels_last_checked")
    ]
)
/**
 * 小説の基本情報をまとめたエンティティ。
 *
 * @property ncode 小説を一意に識別する N コード（Syosetu: そのまま、Kakuyomu: K+Base62形式）
 * @property title タイトル
 * @property author 作者名
 * @property Synopsis あらすじ（DB の列名に合わせて先頭大文字）
 * @property main_tag メインタグ
 * @property sub_tag サブタグ
 * @property rating レーティング種別 (1=R18, 2=一般)
 * @property last_update_date 小説の最終更新日（サイト上の更新日）
 * @property total_ep エピソード総数
 * @property general_all_no 評価ポイント総数
 * @property userid 作者ID
 * @property noveltype 作品種別 (1=連載, 2=短編)
 * @property length 文字数
 * @property updated_at 小説メタデータの最終更新日時（サイト上の更新日時）
 * @property is_favorite お気に入り登録フラグ
 * @property site_type サイト種別 (1=小説家になろう, 2=カクヨム)
 * @property registered_at データベースへの登録日時（ダウンロード日時）
 * @property sub_site サブサイト種別 (0=不明, 1=小説家になろう, 2=ノクターンノベルズ, 3=ムーンライトノベルズ, 4=ミッドナイトノベルズ)
 * @property end_flag 完結フラグ (0=不明, 1=完結, 2=連載中)
 * @property last_checked_at 最終更新確認日時 (yyyy-MM-dd HH:mm:ss)
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
    val is_favorite: Int = 0,  // 0=未登録, 1=登録済み
    val site_type: Int = 1,  // 1=小説家になろう, 2=カクヨム
    val registered_at: String,  // データベース登録日時
    val sub_site: Int = 0,  // 0=不明, 1=小説家になろう, 2=ノクターンノベルズ, 3=ムーンライトノベルズ, 4=ミッドナイトノベルズ
    val end_flag: Int = 0,  // 0=不明, 1=完結, 2=連載中
    val last_checked_at: String = ""  // 最終更新確認日時 yyyy-MM-dd HH:mm:ss
)

//https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode={ncode}&gzip=5&json
