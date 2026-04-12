package com.xycz.bilibili_live.data.remote.api

import com.xycz.bilibili_live.data.remote.dto.QRCodeResponse
import com.xycz.bilibili_live.data.remote.dto.QRStatusResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * B站通行证API接口
 */
interface PassportApi {

    // 获取二维码登录信息
    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQRCode(): Response<QRCodeResponse>

    // 轮询扫码状态
    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQRStatus(
        @Query("qrcode_key") qrcodeKey: String
    ): Response<QRStatusResponse>

    // 获取用户信息
    @GET("x/space/myinfo")
    suspend fun getUserInfo(): Response<com.xycz.bilibili_live.data.remote.dto.UserInfoResponse>

    // 获取 WBI 签名密钥
    @GET("x/web-interface/nav")
    suspend fun getWbiKeys(): Response<com.xycz.bilibili_live.data.remote.dto.WbiKeysResponse>
}
