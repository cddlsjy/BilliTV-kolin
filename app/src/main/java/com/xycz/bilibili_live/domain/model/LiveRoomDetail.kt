package com.xycz.bilibili_live.domain.model

/**
 * 直播间详情
 */
data class LiveRoomDetail(
    val roomId: String,
    val title: String,
    val cover: String,
    val userName: String,
    val userAvatar: String,
    val online: Int,
    val status: Boolean,
    val url: String,
    val danmakuData: DanmakuData? = null,
    val showTime: String? = null,
    val introduction: String? = null
)
