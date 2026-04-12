package com.xycz.bilibili_live.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 关注用户实体
 */
@Entity(tableName = "follow_users")
data class FollowUser(
    @PrimaryKey val id: String,
    val roomId: String,
    val siteId: String = "bilibili",
    val userName: String,
    val face: String,
    val addTime: Long,
    val tag: String = "全部"
)
