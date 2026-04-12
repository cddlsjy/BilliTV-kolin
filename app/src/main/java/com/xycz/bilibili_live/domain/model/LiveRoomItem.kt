package com.xycz.bilibili_live.domain.model

/**
 * 直播间列表项
 */
data class LiveRoomItem(
    val roomId: String,
    val title: String,
    val cover: String,
    val userName: String,
    val online: Int = 0
)
