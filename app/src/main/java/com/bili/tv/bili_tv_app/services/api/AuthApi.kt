package com.bili.tv.bili_tv_app.services.api

import com.bili.tv.bili_tv_app.models.LoginQRCodeResponse
import com.bili.tv.bili_tv_app.models.LoginStatusResponse
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AuthApi private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    companion object {
        @Volatile
        private var instance: AuthApi? = null
        fun getInstance(): AuthApi {
            return instance ?: synchronized(this) {
                instance ?: AuthApi().also { instance = it }
            }
        }
    }

    suspend fun getQRCode(): LoginQRCodeResponse {
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            gson.fromJson(body, LoginQRCodeResponse::class.java)
        } catch (e: Exception) {
            LoginQRCodeResponse(code = -1, message = e.message ?: "Network error")
        }
    }

    suspend fun checkQRCodeStatus(qrcodeKey: String): LoginStatusResponse {
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            gson.fromJson(body, LoginStatusResponse::class.java)
        } catch (e: Exception) {
            LoginStatusResponse(code = -1, message = e.message ?: "Network error")
        }
    }

    suspend fun getUserInfoByCookie(cookies: String): UserInfoResponse {
        val url = "https://api.bilibili.com/x/web-interface/nav"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Cookie", cookies)
            .build()
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            gson.fromJson(body, UserInfoResponse::class.java)
        } catch (e: Exception) {
            UserInfoResponse(code = -1, message = e.message ?: "Network error")
        }
    }

    suspend fun getLoginInfo(mid: Long): UserInfoResponse {
        val url = "https://api.bilibili.com/x/web-interface/card?mid=$mid"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            gson.fromJson(body, UserInfoResponse::class.java)
        } catch (e: Exception) {
            UserInfoResponse(code = -1, message = e.message ?: "Network error")
        }
    }

    data class UserInfoResponse(
        val code: Int = 0,
        val message: String = "",
        val data: UserInfoData? = null
    )
    data class UserInfoData(val card: UserCard? = null)
    data class UserCard(
        val mid: Long = 0,
        val name: String = "",
        val face: String = "",
        val sign: String = "",
        val levelInfo: LevelInfo? = null,
        val vipInfo: VipInfo? = null
    )
    data class LevelInfo(val currentLevel: Int = 0)
    data class VipInfo(val type: Int = 0, val status: Int = 0)
}