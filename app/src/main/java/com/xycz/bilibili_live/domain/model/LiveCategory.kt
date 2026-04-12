package com.xycz.bilibili_live.domain.model

/**
 * 直播分类
 */
data class LiveCategory(
    val id: String,
    val name: String,
    val children: List<LiveSubCategory>
)

/**
 * 子分类
 */
data class LiveSubCategory(
    val id: String,
    val name: String,
    val parentId: String,
    val pic: String
)
