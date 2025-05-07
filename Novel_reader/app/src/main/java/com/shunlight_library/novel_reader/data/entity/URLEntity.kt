// app/src/main/java/com/shunlight_library/novel_reader/data/entity/URLEntity.kt
package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "url_entity",
    indices = [Index(value = ["ncode"], name = "idx_url_entity_ncode")]
)
data class URLEntity(
    @PrimaryKey val ncode: String,
    val api_url: String,
    val url: String,
    val is_r18: Boolean = false
)