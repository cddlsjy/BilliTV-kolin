package com.bili.tv.bili_tv_app.services.api

import com.bili.tv.bili_tv_app.models.LoginQRCodeResponse
import com.bili.tv.bili_tv_app.models.LoginStatusResponse
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AuthApi {

    private val client: OkHttpClient
    private val gson = Gson()
    private val baseUrl = "https://passport.bilibili.com"

    companion object {
        private const val APP_KEY = "4409e2ce8ffd12b8"

        @Volatile
        private var instance: AuthApi? = null

        fun getInstance(): AuthApi {
            return instance ?: synchronized(this) {
                instance ?: AuthApi().also { instance = it }
            }
        }
    }

    init {
        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getQRCode(): LoginQRCodeResponse {
        val url = "$baseUrl/x/passport-login/web/qrcode/generate?appkey=$APP_KEY"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://passport.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            gson.fromJson(body, LoginQRCodeResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            LoginQRCodeResponse(code = -1, message = e.message ?: "Network error")
        }
    }

    suspend fun checkQRCodeStatus(qrcodeKey: String): LoginStatusResponse {
        val url = "$baseUrl/x/passport-login/web/qrcode/poll?appkey=$APP_KEY&qrcode_key=$qrcodeKey"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://passport.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            gson.fromJson(body, LoginStatusResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            LoginStatusResponse(code = -1, message = e.message ?: "Network error")
        }
    }

    suspend fun getLoginInfo(mid: Long): UserInfoResponse {
        val url = "https://api.bilibili.com/x/web-interface/card?mid=$mid"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://space.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            gson.fromJson(body, UserInfoResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            UserInfoResponse(code = -1, message = e.message ?: "Network error")
        }
    }

    data class UserInfoResponse(
        val code: Int = 0,
        val message: String = "",
        val data: UserInfoData? = null
    )

    data class UserInfoData(
        val card: UserCard? = null
    )

    data class UserCard(
        val mid: Long = 0,
        val name: String = "",
        val face: String = "",
        val sign: String = "",
        val level: Int = 0,
        val vipType: Int = 0,
        val vipStatus: Int = 0
    )
}
