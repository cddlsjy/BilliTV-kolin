package com.xycz.bilibili_live.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 历史记录实体
 * 支持直播和点播两种类型
 */
@Entity(tableName = "history")
data class History(
    @PrimaryKey val roomId: String,
    val siteId: String = "bilibili",
    val type: String = "live",  // "live" 或 "vod"
    val userName: String,
    val face: String,
    val updateTime: Long,
    val bvid: String? = null,     // 点播视频的bvid
    val cid: Long? = null,         // 点播视频的cid（分P）
    val progress: Long = 0         // 点播播放进度（秒）
)

