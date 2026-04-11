package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class LoginQRCode(
    @SerializedName("url")
    val url: String = "",
    @SerializedName("qrcode_key")
    val qrcodeKey: String = ""
)

data class LoginQRCodeResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("data")
    val data: LoginQRCodeData? = null
)

data class LoginQRCodeData(
    @SerializedName("url")
    val url: String = "",
    @SerializedName("qrcode_key")
    val qrcodeKey: String = ""
)

data class LoginStatusResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("data")
    val data: LoginStatusData? = null
)

data class LoginStatusData(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("timestamp")
    val timestamp: Long = 0,
    @SerializedName("token_info")
    val tokenInfo: TokenInfo? = null,
    @SerializedName("cookie_info")
    val cookieInfo: CookieInfo? = null
)

data class TokenInfo(
    @SerializedName("mid")
    val mid: Long = 0,          // 关键修复：直接返回 mid
    @SerializedName("access_token")
    val accessToken: String = "",
    @SerializedName("refresh_token")
    val refreshToken: String = "",
    @SerializedName("expires_in")
    val expiresIn: Long = 0,
    @SerializedName("token_info")
    val tokenInfo: TokenInfoDetail? = null
)

data class TokenInfoDetail(
    @SerializedName("mid")
    val mid: Long = 0,
    @SerializedName("access_token")
    val accessToken: String = "",
    @SerializedName("refresh_token")
    val refreshToken: String = "",
    @SerializedName("expires_in")
    val expiresIn: Long = 0
)

data class CookieInfo(
    @SerializedName("cookies")
    val cookies: List<Cookie>? = null
)

data class Cookie(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("value")
    val value: String = "",
    @SerializedName("http_only")
    val httpOnly: Int = 0,
    @SerializedName("expires")
    val expires: Int = 0,
    @SerializedName("secure")
    val secure: Int = 0
)