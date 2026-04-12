package com.xycz.bilibili_live.domain.model

/**
 * 播放画质
 */
data class LivePlayQuality(
    val quality: String,
    val data: Int
)

/**
 * 播放地址
 */
data class LivePlayUrl(
    val urls: List<String>,
    val headers: Map<String, String>
)
