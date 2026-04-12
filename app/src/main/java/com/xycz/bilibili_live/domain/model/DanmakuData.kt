package com.xycz.bilibili_live.domain.model

/**
 * 弹幕服务器数据
 */
data class DanmakuData(
    val roomId: Int,
    val uid: Int,
    val token: String,
    val serverHost: String,
    val buvid: String,
    val cookie: String
)
