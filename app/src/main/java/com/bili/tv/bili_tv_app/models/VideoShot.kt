package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class VideoShot(
    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("index")
    val index: Int = 0,

    @SerializedName("picture")
    val picture: String = "",

    @SerializedName("timestamp")
    val timestamp: Int = 0
)

data class VideoShotResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: List<VideoShot>? = null
)
