package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class Video(
    @SerializedName("bvid")
    val bvid: String = "",

    @SerializedName("aid")
    val aid: Long = 0,

    @SerializedName("pic")
    val pic: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("pubdate")
    val pubdate: Long = 0,

    @SerializedName("desc")
    val desc: String = "",

    @SerializedName("duration")
    val duration: String = "",

    @SerializedName("author")
    val author: String = "",

    @SerializedName("view")
    val view: Int = 0,

    @SerializedName("like")
    val like: Int = 0,

    @SerializedName("coin")
    val coin: Int = 0,

    @SerializedName("favorite")
    val favorite: Int = 0,

    @SerializedName("share")
    val share: Int = 0,

    @SerializedName("danmaku")
    val danmaku: Int = 0,

    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("tid")
    val tid: Int = 0,

    @SerializedName("tname")
    val tname: String = "",

    @SerializedName("short_link_v2")
    val shortLinkV2: String = "",

    @SerializedName("owner")
    val owner: Owner? = null,

    @SerializedName("stat")
    val stat: Stat? = null,

    @SerializedName("dimension")
    val dimension: Dimension? = null,

    @SerializedName("season_id")
    val seasonId: Long = 0,

    @SerializedName("season_type")
    val seasonType: Int = 0
) {
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
        val view: Int = 0,

        @SerializedName("like")
        val like: Int = 0,

        @SerializedName("coin")
        val coin: Int = 0,

        @SerializedName("favorite")
        val favorite: Int = 0,

        @SerializedName("share")
        val share: Int = 0,

        @SerializedName("danmaku")
        val danmaku: Int = 0,

        @SerializedName("reply")
        val reply: Int = 0
    )

    data class Dimension(
        @SerializedName("width")
        val width: Int = 0,

        @SerializedName("height")
        val height: Int = 0,

        @SerializedName("rotate")
        val rotate: Int = 0
    )

    fun getFormattedViews(): String {
        val statView = stat?.view ?: view
        return when {
            statView >= 100000000 -> String.format("%.1f亿", statView / 100000000.0)
            statView >= 10000 -> String.format("%.1f万", statView / 10000.0)
            else -> statView.toString()
        }
    }

    fun getFormattedDuration(): String {
        // Duration is in seconds
        return duration
    }
}
