package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class PlayUrl(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("result")
    val result: PlayUrlResult? = null
)

data class PlayUrlResult(
    @SerializedName("durl")
    val durl: List<DUrl>? = null,

    @SerializedName("quality")
    val quality: Int = 0,

    @SerializedName("format")
    val format: String = "",

    @SerializedName("timelength")
    val timelength: Int = 0,

    @SerializedName("accept_format")
    val acceptFormat: String = "",

    @SerializedName("accept_description")
    val acceptDescription: List<String>? = null,

    @SerializedName("accept_quality")
    val acceptQuality: List<Int>? = null
)

data class DUrl(
    @SerializedName("url")
    val url: String = "",

    @SerializedName("length")
    val length: Int = 0,

    @SerializedName("size")
    val size: Long = 0,

    @SerializedName("order")
    val order: Int = 0
)

data class Dash(
    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("minBufferTime")
    val minBufferTime: Float = 0f,

    @SerializedName("video")
    val video: List<VideoTrack>? = null,

    @SerializedName("audio")
    val audio: List<AudioTrack>? = null
)

data class VideoTrack(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("baseUrl")
    val baseUrl: String = "",

    @SerializedName("backupUrl")
    val backupUrl: List<String>? = null,

    @SerializedName("bandwidth")
    val bandwidth: Long = 0,

    @SerializedName("mimeType")
    val mimeType: String = "",

    @SerializedName("codecs")
    val codecs: String = "",

    @SerializedName("width")
    val width: Int = 0,

    @SerializedName("height")
    val height: Int = 0,

    @SerializedName("frameRate")
    val frameRate: String = "",

    @SerializedName("sar")
    val sar: String = "",

    @SerializedName("startWithSap")
    val startWithSap: Int = 0
)

data class AudioTrack(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("baseUrl")
    val baseUrl: String = "",

    @SerializedName("backupUrl")
    val backupUrl: List<String>? = null,

    @SerializedName("bandwidth")
    val bandwidth: Long = 0,

    @SerializedName("mimeType")
    val mimeType: String = "",

    @SerializedName("codecs")
    val codecs: String = "",

    @SerializedName("sampleRate")
    val sampleRate: Int = 0,

    @SerializedName("channelCount")
    val channelCount: Int = 0,

    @SerializedName("startWithSap")
    val startWithSap: Int = 0
)

data class PlaybackProgress(
    val bvid: String,
    val cid: Long,
    val progress: Long,
    val duration: Long,
    val timestamp: Long = System.currentTimeMillis()
)
