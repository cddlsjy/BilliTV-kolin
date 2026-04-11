package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class LiveRoom(
    @SerializedName("roomid")
    val roomId: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("uname")
    val uname: String = "",

    @SerializedName("face")
    val face: String = "",

    @SerializedName("cover")
    val cover: String = "",

    @SerializedName("online")
    val online: Int = 0,

    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("area_name")
    val areaName: String = "",

    @SerializedName("live_status")
    val liveStatus: Int = 0 // 1: live, 0: not live
)

data class LiveStreamResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: LiveStreamData? = null
)

data class LiveStreamData(
    @SerializedName("durl")
    val durl: List<LiveStreamUrl>? = null
)

data class LiveStreamUrl(
    @SerializedName("url")
    val url: String = "",

    @SerializedName("length")
    val length: Long = 0,

    @SerializedName("order")
    val order: Int = 0
)
