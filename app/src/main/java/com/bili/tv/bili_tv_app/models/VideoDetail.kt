package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class VideoDetail(
    @SerializedName("bvid")
    val bvid: String = "",

    @SerializedName("aid")
    val aid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("desc")
    val desc: String = "",

    @SerializedName("pic")
    val pic: String = "",

    @SerializedName("owner")
    val owner: Owner? = null,

    @SerializedName("stat")
    val stat: Stat? = null,

    @SerializedName("pages")
    val pages: List<Episode> = emptyList()
) {
    fun getPageIndex(cid: Long): Int {
        return pages.indexOfFirst { it.cid == cid }
    }
}

data class Owner(
    @SerializedName("mid")
    val mid: Long = 0,

    @SerializedName("name")
    val name: String = "",

    @SerializedName("face")
    val face: String = ""
)

data class Stat(
    @SerializedName("view")
    val view: Long = 0,

    @SerializedName("danmaku")
    val danmaku: Long = 0,

    @SerializedName("reply")
    val reply: Long = 0,

    @SerializedName("favorite")
    val favorite: Long = 0,

    @SerializedName("coin")
    val coin: Long = 0,

    @SerializedName("share")
    val share: Long = 0
)
