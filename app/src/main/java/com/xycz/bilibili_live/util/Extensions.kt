package com.xycz.bilibili_live.util

/**
 * 扩展函数
 */

/**
 * Int格式化显示（人气、播放量等）
 */
fun Int.formatCount(): String {
    return when {
        this >= 100000000 -> String.format("%.1f亿", this / 100000000.0)
        this >= 10000 -> String.format("%.1f万", this / 10000.0)
        else -> this.toString()
    }
}

/**
 * Long格式化显示（时间戳等）
 */
fun Long.formatTime(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}天前"
        hours > 0 -> "${hours}小时前"
        minutes > 0 -> "${minutes}分钟前"
        else -> "刚刚"
    }
}

/**
 * String正则提取
 */
fun String.extractFirst(pattern: String, groupIndex: Int = 1): String? {
    return Regex(pattern).find(this)?.groupValues?.getOrNull(groupIndex)
}

/**
 * 安全URL编码
 */
fun String.urlEncode(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
}

/**
 * 安全URL解码
 */
fun String.urlDecode(): String {
    return java.net.URLDecoder.decode(this, "UTF-8")
}

/**
 * 检查字符串是否为空或空白
 */
fun String?.isNullOrBlank(): Boolean = this.isNullOrBlank()

/**
 * 检查字符串是否不为空且非空白
 */
fun String?.isNotNullOrBlank(): Boolean = !this.isNullOrBlank()
