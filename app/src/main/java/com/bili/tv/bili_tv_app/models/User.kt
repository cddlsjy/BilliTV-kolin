package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("mid")
    val mid: Long = 0,

    @SerializedName("uname")
    val uname: String = "",

    @SerializedName("sex")
    val sex: String = "",

    @SerializedName("face")
    val face: String = "",

    @SerializedName("face_nft")
    val faceNft: Int = 0,

    @SerializedName("face_nft_type")
    val faceNftType: Int = 0,

    @SerializedName("sign")
    val sign: String = "",

    @SerializedName("rank")
    val rank: Int = 0,

    @SerializedName("level")
    val level: Int = 0,

    @SerializedName("jointime")
    val joinTime: Int = 0,

    @SerializedName("moral")
    val moral: Int = 0,

    @SerializedName("silence")
    val silence: Int = 0,

    @SerializedName("email_verified")
    val emailVerified: Int = 0,

    @SerializedName("telephone_verified")
    val telephoneVerified: Int = 0,

    @SerializedName("coin")
    val coin: Double = 0.0,

    @SerializedName("birthday")
    val birthday: String = "",

    @SerializedName("tiny_id")
    val tinyId: String = "",

    @SerializedName("is_tourist")
    val isTourist: Int = 0,

    @SerializedName("is_friend")
    val isFriend: Int = 0,

    @SerializedName("level_desc")
    val levelDesc: String = "",

    @SerializedName("ugrade")
    val ugrade: Int = 0,

    @SerializedName("vip_type")
    val vipType: Int = 0,

    @SerializedName("vip_status")
    val vipStatus: Int = 0,

    @SerializedName("vip_pay_type")
    val vipPayType: Int = 0,

    @SerializedName("vip_theme_type")
    val vipThemeType: Int = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("name_color")
    val nameColor: String = "",

    @SerializedName("official")
    val official: Official? = null
) {
    data class Official(
        @SerializedName("role")
        val role: Int = 0,

        @SerializedName("title")
        val title: String = "",

        @SerializedName("desc")
        val desc: String = ""
    )

    fun isVip(): Boolean = vipStatus == 1

    fun isGoldVip(): Boolean = vipType == 2
}
