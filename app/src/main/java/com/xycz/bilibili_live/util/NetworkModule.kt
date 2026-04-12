package com.xycz.bilibili_live.util

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.xycz.bilibili_live.data.remote.api.BilibiliApi
import com.xycz.bilibili_live.data.remote.api.PassportApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络模块
 */
object NetworkModule {
    private const val BASE_URL = "https://api.live.bilibili.com/"
    private const val PASSPORT_URL = "https://passport.bilibili.com/"

    private var cookie: String = ""
    private var buvid3: String = ""
    private var buvid4: String = ""

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // 内部OkHttpClient实例
    private val _okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
                    )
                    .addHeader("Referer", "https://live.bilibili.com/")
                    .apply {
                        if (cookie.isNotEmpty()) {
                            val fullCookie = if (cookie.contains("buvid3")) cookie else "$cookie;buvid3=$buvid3;buvid4=$buvid4;"
                            addHeader("Cookie", fullCookie)
                        } else {
                            addHeader("Cookie", "buvid3=$buvid3;buvid4=$buvid4;")
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // 公开的OkHttpClient访问器（供DanmakuService等使用）
    val okHttpClient: OkHttpClient get() = _okHttpClient

    val bilibiliApi: BilibiliApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BilibiliApi::class.java)
    }

    val passportApi: PassportApi by lazy {
        Retrofit.Builder()
            .baseUrl(PASSPORT_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PassportApi::class.java)
    }

    fun setCookie(newCookie: String) {
        cookie = newCookie
        SecureSettingsManager.saveCookie(newCookie)
    }

    fun getCookie(): String = cookie

    fun setBuvid(buvid3: String, buvid4: String) {
        this.buvid3 = buvid3
        this.buvid4 = buvid4
    }

    fun getBuvid3(): String = buvid3
    fun getBuvid4(): String = buvid4

    fun init(context: android.content.Context) {
        SecureSettingsManager.init(context)
        // 从安全存储加载
        val savedCookie = SecureSettingsManager.getCookie()
        if (savedCookie.isNotEmpty()) {
            cookie = savedCookie
        }
        val savedBuvid3 = SecureSettingsManager.getBuvid3()
        val savedBuvid4 = SecureSettingsManager.getBuvid4()
        if (savedBuvid3.isNotEmpty() && savedBuvid4.isNotEmpty()) {
            buvid3 = savedBuvid3
            buvid4 = savedBuvid4
        }
    }
}
