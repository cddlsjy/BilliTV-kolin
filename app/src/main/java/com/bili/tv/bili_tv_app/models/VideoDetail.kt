package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class VideoDetail(
    @SerializedName("bvid") val bvid: String = "",
    @SerializedName("aid") val aid: Long = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("pic") val pic: String = "",
    @SerializedName("pages") val pages: List<Episode> = emptyList()
) {
    fun getPageIndex(cid: Long): Int = pages.indexOfFirst { it.cid == cid }.coerceAtLeast(0)
}