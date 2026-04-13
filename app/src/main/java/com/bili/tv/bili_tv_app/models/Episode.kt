package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class Episode(
    @SerializedName("cid") val cid: Long = 0,
    @SerializedName("page") val page: Int = 0,
    @SerializedName("part") val part: String = "",
    @SerializedName("duration") val duration: Int = 0
)