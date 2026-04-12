package com.xycz.bilibili_live.domain.model

/**
 * 用户信息
 */
data class UserInfo(
    val uid: Long,
    val uname: String,
    val face: String,
    val level: Int
)
