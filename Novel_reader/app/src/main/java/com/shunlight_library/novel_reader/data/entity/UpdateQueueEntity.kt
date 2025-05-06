package com.shunlight_library.novel_reader.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "update_queue",
    primaryKeys = ["ncode"],
    indices = [Index(value = ["update_time"], name = "idx_update_queue_time")]
)
data class UpdateQueueEntity(
    val ncode: String,
    val total_ep: Int,
    val general_all_no: Int,
    val update_time: String
)