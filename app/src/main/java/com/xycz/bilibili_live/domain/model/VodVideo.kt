package com.xycz.bilibili_live.domain.model

/**
 * 点播视频信息
 */
data class VodVideo(
    val bvid: String,
    val title: String,
    val cover: String,
    val description: String,
    val duration: Long,
    val ownerMid: Long,
    val ownerName: String,
    val ownerFace: String,
    val pages: List<VodEpisode> = emptyList()
)

/**
 * 点播分P/剧集信息
 */
data class VodEpisode(
    val cid: Long,
    val page: Int,
    val title: String,
    val duration: Long,
    val description: String = ""
)

/**
 * 推荐视频信息
 */
data class VodRecommend(
    val bvid: String,
    val title: String,
    val cover: String,
    val ownerMid: Long,
    val ownerName: String,
    val ownerFace: String,
    val duration: Int,
    val shortLink: String = ""
)
