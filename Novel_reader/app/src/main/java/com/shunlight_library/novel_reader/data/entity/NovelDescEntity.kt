package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "novels_descs",
    primaryKeys = ["ncode"],
    indices = [
        Index(value = ["last_update_date"], name = "idx_novels_last_update"),
        Index(value = ["ncode", "rating", "total_ep", "general_all_no", "updated_at"], name = "idx_novels_update_check")
    ]
)
data class NovelDescEntity(
    val ncode: String,
    val title: String,
    val author: String,
    val Synopsis: String,  // 注意: データベース名と一致させるため頭文字大文字
    val main_tag: String,
    val sub_tag: String,
    val rating: Int,
    val last_update_date: String,
    val total_ep: Int,
    val general_all_no: Int,
    val updated_at: String
)

//https://api.syosetu.com/novel18api/api/?of=t-w-ga-s-ua&ncode={ncode}&gzip=5&json