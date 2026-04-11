## 问题分析与修复方案

### Bug 1：二维码登录失败根因
`LoginStatusData.tokenInfo` 模型中 `mid` 字段定义在嵌套的 `TokenInfoDetail` 里，但B站API实际返回的 `token_info.mid` 是**直接在第一层**的，导致 `tokenInfo.tokenInfo?.mid` 永远是 `null`，用户信息获取失败，登录状态未持久化。

### Bug 2：自动播放上次视频
需要在 `SettingsService` 增加持久化字段，`SettingsFragment` 增加开关，`HomeFragment` 启动时判断并跳转。

### Bug 3：方向键切换逻辑
需要在 `PlayerFragment` 拦截 D-pad 按键，根据直播/点播模式分发到不同的数据源。

---

## 修改文件清单

### 1. `models/Login.kt` — 修复 TokenInfo 模型，增加 mid 字段

```kt
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
    val mid: Long = 0,          // ← 修复：B站API直接在token_info下返回mid

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
```

### 2. `services/AuthService.kt` — 完整重写，基于 Cookie 持久化登录

```kt
package com.bili.tv.bili_tv_app.services

import android.content.Context
import android.content.SharedPreferences
import com.bili.tv.bili_tv_app.models.User
import com.google.gson.Gson

object AuthService {

    private const val PREFS_NAME = "bili_auth"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_IN = "expires_in"
    private const val KEY_USER_JSON = "user_json"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    var isLoggedIn: Boolean = false
        private set
    var currentUser: User? = null
        private set
    var cookies: String = ""
        private set
    var accessToken: String = ""
        private set

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        restoreLoginState()
    }

    private fun restoreLoginState() {
        isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            cookies = prefs.getString(KEY_COOKIES, "") ?: ""
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
            val userJson = prefs.getString(KEY_USER_JSON, null)
            if (userJson != null) {
                try {
                    currentUser = gson.fromJson(userJson, User::class.java)
                } catch (e: Exception) {
                    // 数据损坏，清除登录状态
                    clearLogin()
                }
            }
            // 校验：如果没有 cookies 或用户信息，视为未登录
            if (cookies.isEmpty() || currentUser == null) {
                clearLogin()
            }
        }
    }

    /**
     * 保存登录信息 — 以 Cookie 为主，token 为辅
     */
    fun saveLoginInfo(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
        cookies: String,
        user: User
    ) {
        this.accessToken = accessToken
        this.cookies = cookies
        this.currentUser = user
        this.isLoggedIn = true

        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_COOKIES, cookies)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_IN, expiresIn)
            .putString(KEY_USER_JSON, gson.toJson(user))
            .apply()
    }

    /**
     * 从 Cookie 字符串中提取指定字段值
     */
    fun getCookieValue(name: String): String {
        val pattern = "$name="
        val start = cookies.indexOf(pattern)
        if (start == -1) return ""
        val valueStart = start + pattern.length
        val end = cookies.indexOf(';', valueStart)
        return if (end == -1) cookies.substring(valueStart) else cookies.substring(valueStart, end)
    }

    /**
     * 获取 DedeUserID（B站用户ID，存在于Cookie中）
     */
    fun getDedeUserId(): Long {
        val uid = getCookieValue("DedeUserID").trim()
        return uid.toLongOrNull() ?: 0L
    }

    fun clearLogin() {
        isLoggedIn = false
        currentUser = null
        cookies = ""
        accessToken = ""
        prefs.edit().clear().apply()
    }
}
```

### 3. `services/api/AuthApi.kt` — 完整实现，支持 Cookie 方式获取用户信息

```kt
package com.bili.tv.bili_tv_app.services.api

import com.bili.tv.bili_tv_app.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AuthApi private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://passport.bilibili.com"
        private const val API_URL = "https://api.bilibili.com"

        @Volatile
        private var instance: AuthApi? = null

        fun getInstance(): AuthApi {
            return instance ?: synchronized(this) {
                instance ?: AuthApi().also { instance = it }
            }
        }
    }

    /**
     * 获取二维码登录链接
     */
    fun getQRCode(): LoginQRCodeResponse {
        val url = "$BASE_URL/x/passport-login/web/qrcode/generate"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return LoginQRCodeResponse(code = -1, message = "网络错误")

        return try {
            val resp = gson.fromJson(body, LoginQRCodeResponse::class.java)
            if (resp.code == 0 && resp.data != null) {
                resp
            } else {
                LoginQRCodeResponse(code = resp.code, message = resp.message)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LoginQRCodeResponse(code = -1, message = "解析错误: ${e.message}")
        }
    }

    /**
     * 轮询二维码扫描状态
     * 返回 null 表示请求失败（应继续轮询）
     */
    fun checkQRCodeStatus(qrcodeKey: String): LoginStatusResponse? {
        val url = "$BASE_URL/x/passport-login/web/qrcode/poll?qrcode_key=${URLEncoder.encode(qrcodeKey, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, LoginStatusResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 通过 Cookie 获取当前登录用户信息（推荐方式）
     */
    fun getUserInfoByCookie(cookies: String): UserInfoResponse? {
        val url = "$API_URL/x/web-interface/nav"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://www.bilibili.com")
            .header("Cookie", cookies)
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, UserInfoResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 通过 mid 获取用户信息卡片
     */
    fun getLoginInfo(mid: Long): UserInfoCardResponse? {
        if (mid <= 0) return null
        val url = "$API_URL/x/web-interface/card?mid=$mid"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://www.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, UserInfoCardResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// ============ 响应数据模型 ============

data class UserInfoResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("data")
    val data: UserInfoData? = null
)

data class UserInfoData(
    @SerializedName("mid")
    val mid: Long = 0,
    @SerializedName("uname")
    val uname: String = "",
    @SerializedName("face")
    val face: String = "",
    @SerializedName("sign")
    val sign: String = "",
    @SerializedName("level")
    val level: Int = 0,
    @SerializedName("vip_type")
    val vipType: Int = 0,
    @SerializedName("vip_status")
    val vipStatus: Int = 0,
    @SerializedName("isLogin")
    val isLogin: Boolean = false
)

data class UserInfoCardResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("data")
    val data: UserCardData? = null
)

data class UserCardData(
    @SerializedName("card")
    val card: UserCard? = null
)

data class UserCard(
    @SerializedName("mid")
    val mid: Long = 0,
    @SerializedName("name")
    val name: String = "",
    @SerializedName("face")
    val face: String = "",
    @SerializedName("sign")
    val sign: String = "",
    @SerializedName("level")
    val level: Int = 0,
    @SerializedName("vip_type")
    val vipType: Int = 0,
    @SerializedName("vip_status")
    val vipStatus: Int = 0
)

data class DanmakuSegment(
    val p: String = "",
    val m: String = ""
)
```

### 4. `screens/home/login/LoginFragment.kt` — 修复登录流程

```kt
package com.bili.tv.bili_tv_app.screens.home.login

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bili.tv.bili_tv_app.databinding.FragmentLoginBinding
import com.bili.tv.bili_tv_app.models.User
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.api.AuthApi
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private var qrcodeKey: String = ""
    private var isPolling = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        generateQRCode()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun generateQRCode() {
        lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE
            binding.loginStatus.text = "正在获取二维码..."

            val qrCode = withContext(Dispatchers.IO) {
                AuthApi.getInstance().getQRCode()
            }

            binding.loadingProgress.visibility = View.GONE

            if (qrCode.code == 0 && qrCode.data != null) {
                qrcodeKey = qrCode.data.qrcodeKey

                try {
                    val barcodeEncoder = BarcodeEncoder()
                    val bitmap = barcodeEncoder.encodeBitmap(
                        qrCode.data.url,
                        com.google.zxing.BarcodeFormat.QR_CODE,
                        400,
                        400
                    )
                    binding.qrCodeImage.setImageBitmap(bitmap)
                    binding.loginStatus.text = "请使用B站APP扫描二维码登录"
                } catch (e: Exception) {
                    e.printStackTrace()
                    binding.loginStatus.text = "二维码生成失败"
                    Toast.makeText(requireContext(), "二维码生成失败", Toast.LENGTH_SHORT).show()
                }

                startPolling()
            } else {
                binding.loginStatus.text = "获取二维码失败: ${qrCode.message}"
                Toast.makeText(requireContext(), "获取二维码失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true

        lifecycleScope.launch {
            while (isPolling && qrcodeKey.isNotEmpty() && isAdded) {
                delay(2000)

                if (!isAdded) break

                val status = withContext(Dispatchers.IO) {
                    AuthApi.getInstance().checkQRCodeStatus(qrcodeKey)
                }

                if (status == null) continue  // 网络错误，继续轮询

                status.data?.let { data ->
                    when (data.code) {
                        0 -> {
                            // 登录成功
                            isPolling = false
                            binding.loginStatus.text = "登录成功，正在获取用户信息..."
                            onLoginSuccess(data)
                        }
                        -1, 86038 -> {
                            // 二维码过期
                            isPolling = false
                            binding.loginStatus.text = "二维码已过期"
                            Toast.makeText(requireContext(), "二维码已过期，请重新获取", Toast.LENGTH_SHORT).show()
                            delay(1000)
                            generateQRCode()
                        }
                        86090 -> {
                            // 未扫码
                            if (binding.loginStatus.text.toString() != "请使用B站APP扫描二维码登录") {
                                binding.loginStatus.text = "请使用B站APP扫描二维码登录"
                            }
                        }
                        86101 -> {
                            // 已扫码，未确认
                            binding.loginStatus.text = "已扫码，请在手机上确认登录"
                        }
                    }
                }
            }
        }
    }

    private fun onLoginSuccess(data: com.bili.tv.bili_tv_app.models.LoginStatusData) {
        lifecycleScope.launch {
            try {
                // ========== 核心：先拼装 Cookie 字符串 ==========
                val cookiesList = data.cookieInfo?.cookies
                if (cookiesList.isNullOrEmpty()) {
                    Log.e("LoginFragment", "登录成功但未返回Cookie信息")
                    Toast.makeText(requireContext(), "登录异常：未获取到Cookie", Toast.LENGTH_LONG).show()
                    binding.loginStatus.text = "登录异常，请重试"
                    return@launch
                }

                val cookiesString = cookiesList.joinToString("; ") { "${it.name}=${it.value}" }
                Log.d("LoginFragment", "Cookie获取成功: ${cookiesString.take(50)}...")

                // ========== 用 Cookie 获取用户信息（最可靠的方式） ==========
                val userInfo = withContext(Dispatchers.IO) {
                    AuthApi.getInstance().getUserInfoByCookie(cookiesString)
                }

                val user: User? = if (userInfo != null && userInfo.code == 0 && userInfo.data != null) {
                    val info = userInfo.data
                    Log.d("LoginFragment", "通过Cookie获取用户信息成功: ${info.uname}, mid: ${info.mid}")
                    User(
                        mid = info.mid,
                        uname = info.uname,
                        face = info.face,
                        sign = info.sign,
                        level = info.level,
                        vipType = info.vipType,
                        vipStatus = info.vipStatus
                    )
                } else {
                    // 降级方案：从 tokenInfo.mid 获取（修复后模型已有此字段）
                    val mid = data.tokenInfo?.mid ?: 0L
                    if (mid > 0) {
                        Log.d("LoginFragment", "降级：通过mid=$mid 获取用户信息")
                        val cardResp = withContext(Dispatchers.IO) {
                            AuthApi.getInstance().getLoginInfo(mid)
                        }
                        cardResp?.data?.card?.let { card ->
                            User(
                                mid = card.mid,
                                uname = card.name,
                                face = card.face,
                                sign = card.sign,
                                level = card.level,
                                vipType = card.vipType,
                                vipStatus = card.vipStatus
                            )
                        }
                    } else {
                        // 最终降级：从 Cookie 中提取 DedeUserID
                        val dedeUserId = cookiesList.find { it.name == "DedeUserID" }?.value?.toLongOrNull() ?: 0L
                        if (dedeUserId > 0) {
                            Log.d("LoginFragment", "最终降级：从Cookie提取DedeUserID=$dedeUserId")
                            val cardResp = withContext(Dispatchers.IO) {
                                AuthApi.getInstance().getLoginInfo(dedeUserId)
                            }
                            cardResp?.data?.card?.let { card ->
                                User(
                                    mid = card.mid,
                                    uname = card.name,
                                    face = card.face,
                                    sign = card.sign,
                                    level = card.level,
                                    vipType = card.vipType,
                                    vipStatus = card.vipStatus
                                )
                            }
                        } else {
                            null
                        }
                    }

                // ========== 持久化登录状态 ==========
                if (user != null) {
                    val token = data.tokenInfo?.accessToken ?: ""
                    val refreshToken = data.tokenInfo?.refreshToken ?: data.refreshToken ?: ""
                    val expiresIn = data.tokenInfo?.expiresIn ?: 0L

                    AuthService.saveLoginInfo(
                        accessToken = token,
                        refreshToken = refreshToken,
                        expiresIn = expiresIn,
                        cookies = cookiesString,
                        user = user
                    )

                    Log.d("LoginFragment", "登录状态保存成功: isLoggedIn=${AuthService.isLoggedIn}")
                    Toast.makeText(requireContext(), "登录成功: ${user.uname}", Toast.LENGTH_SHORT).show()
                    binding.loginStatus.text = "登录成功！"

                    // 确保保存完成后再返回
                    delay(300)
                    parentFragmentManager.popBackStack()
                } else {
                    Log.e("LoginFragment", "无法获取用户信息")
                    Toast.makeText(requireContext(), "登录异常：无法获取用户信息", Toast.LENGTH_LONG).show()
                    binding.loginStatus.text = "获取用户信息失败，请重试"
                }
            } catch (e: Exception) {
                Log.e("LoginFragment", "登录处理异常", e)
                Toast.makeText(requireContext(), "登录处理异常: ${e.message}", Toast.LENGTH_LONG).show()
                binding.loginStatus.text = "登录异常，请重试"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isPolling = false
        _binding = null
    }
}
```

### 5. `services/SettingsService.kt` — 增加自动播放上次视频设置

```kt
package com.bili.tv.bili_tv_app.services

import android.content.Context
import android.content.SharedPreferences

object SettingsService {

    private const val PREFS_NAME = "bili_settings"

    // 播放设置
    var defaultQuality: Int
        get() = prefs.getInt("default_quality", 80)
        set(value) = prefs.edit().putInt("default_quality", value).apply()

    var autoPlay: Boolean
        get() = prefs.getBoolean("auto_play", true)
        set(value) = prefs.edit().putBoolean("auto_play", value).apply()

    // ↓↓↓ 新增：自动播放上次视频 ↓↓↓
    var autoPlayLastVideo: Boolean
        get() = prefs.getBoolean("auto_play_last_video", false)
        set(value) = prefs.edit().putBoolean("auto_play_last_video", value).apply()

    // 上次播放的视频信息
    var lastPlayedBvid: String
        get() = prefs.getString("last_played_bvid", "") ?: ""
        set(value) = prefs.edit().putString("last_played_bvid", value).apply()

    var lastPlayedTitle: String
        get() = prefs.getString("last_played_title", "") ?: ""
        set(value) = prefs.edit().putString("last_played_title", value).apply()

    var lastPlayedCover: String
        get() = prefs.getString("last_played_cover", "") ?: ""
        set(value) = prefs.edit().putString("last_played_cover", value).apply()

    var lastPlayedCid: Long
        get() = prefs.getLong("last_played_cid", 0L)
        set(value) = prefs.edit().putLong("last_played_cid", value).apply()

    var lastPlayedProgress: Long
        get() = prefs.getLong("last_played_progress", 0L)
        set(value) = prefs.edit().putLong("last_played_progress", value).apply()

    var lastPlayedIsLive: Boolean
        get() = prefs.getBoolean("last_played_is_live", false)
        set(value) = prefs.edit().putBoolean("last_played_is_live", value).apply()

    var lastPlayedRoomId: Long
        get() = prefs.getLong("last_played_room_id", 0L)
        set(value) = prefs.edit().putLong("last_played_room_id", value).apply()

    /**
     * 保存上次播放的视频信息
     */
    fun saveLastPlayedVideo(
        bvid: String,
        title: String,
        cover: String,
        cid: Long,
        progress: Long,
        isLive: Boolean = false,
        roomId: Long = 0L
    ) {
        lastPlayedBvid = bvid
        lastPlayedTitle = title
        lastPlayedCover = cover
        lastPlayedCid = cid
        lastPlayedProgress = progress
        lastPlayedIsLive = isLive
        lastPlayedRoomId = roomId
    }

    /**
     * 清除上次播放记录
     */
    fun clearLastPlayedVideo() {
        prefs.edit()
            .remove("last_played_bvid")
            .remove("last_played_title")
            .remove("last_played_cover")
            .remove("last_played_cid")
            .remove("last_played_progress")
            .remove("last_played_is_live")
            .remove("last_played_room_id")
            .apply()
    }

    /**
     * 是否有有效的上次播放记录
     */
    fun hasLastPlayedVideo(): Boolean {
        return lastPlayedBvid.isNotEmpty()
    }

    // 弹幕设置
    var danmakuEnabled: Boolean
        get() = prefs.getBoolean("danmaku_enabled", true)
        set(value) = prefs.edit().putBoolean("danmaku_enabled", value).apply()

    var danmakuFontSize: Float
        get() = prefs.getFloat("danmaku_font_size", 25f)
        set(value) = prefs.edit().putFloat("danmaku_font_size", value).apply()

    var danmakuOpacity: Float
        get() = prefs.getFloat("danmaku_opacity", 0.8f)
        set(value) = prefs.edit().putFloat("danmaku_opacity", value).apply()

    var danmakuDensity: Float
        get() = prefs.getFloat("danmaku_density", 1.0f)
        set(value) = prefs.edit().putFloat("danmaku_density", value).apply()

    // 插件设置
    var danmakuEnhanceEnabled: Boolean
        get() = prefs.getBoolean("danmaku_enhance_enabled", true)
        set(value) = prefs.edit().putBoolean("danmaku_enhance_enabled", value).apply()

    var adFilterEnabled: Boolean
        get() = prefs.getBoolean("ad_filter_enabled", true)
        set(value) = prefs.edit().putBoolean("ad_filter_enabled", value).apply()

    var sponsorBlockEnabled: Boolean
        get() = prefs.getBoolean("sponsor_block_enabled", false)
        set(value) = prefs.edit().putBoolean("sponsor_block_enabled", value).apply()

    // 其他
    var splashAnimationEnabled: Boolean
        get() = prefs.getBoolean("splash_animation_enabled", true)
        set(value) = prefs.edit().putBoolean("splash_animation_enabled", value).apply()

    // 内部
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
```

### 6. `res/layout/fragment_settings.xml` — 增加自动播放上次视频开关

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#1A1A2E"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="40dp">

        <!-- 顶部栏 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:layout_marginBottom="30dp">

            <ImageButton
                android:id="@+id/backButton"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="返回"
                android:src="@android:drawable/ic_menu_revert" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:text="设置"
                android:textColor="#FFFFFF"
                android:textSize="28sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- 播放设置分区 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="播放设置"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp"
            android:layout_marginBottom="24dp">

            <!-- 自动连播 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="自动连播"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/autoPlaySwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- ↓↓↓ 新增：自动播放上次视频 ↓↓↓ -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="启动时自动播放上次视频"
                        android:textColor="#E0E0E0"
                        android:textSize="18sp" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:text="开启后启动应用将自动进入上次播放的视频"
                        android:textColor="#888888"
                        android:textSize="13sp" />
                </LinearLayout>

                <Switch
                    android:id="@+id/autoPlayLastVideoSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- 画质选择 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="默认画质"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Spinner
                    android:id="@+id/qualitySpinner"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:entries="@array/quality_options" />
            </LinearLayout>
        </LinearLayout>

        <!-- 弹幕设置分区 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="弹幕设置"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp"
            android:layout_marginBottom="24dp">

            <!-- 弹幕开关 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="显示弹幕"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/danmakuSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- 弹幕增强 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="弹幕增强过滤"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/adFilterSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- 赞助跳过 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="赞助内容跳过"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/sponsorBlockSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>
        </LinearLayout>

        <!-- 其他设置 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="其他"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp"
            android:layout_marginBottom="24dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="启动动画"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/splashAnimationSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>
        </LinearLayout>

        <!-- 关于 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="关于"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp">

            <TextView
                android:id="@+id/aboutText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#AAAAAA"
                android:textSize="16sp"
                android:lineSpacingExtra="4dp" />
        </LinearLayout>

    </LinearLayout>
</ScrollView>
```

### 7. `res/values/arrays.xml` — 确保有画质选项数组

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="quality_options">
        <item>360P</item>
        <item>480P</item>
        <item>720P</item>
        <item>1080P</item>
        <item>1080P+</item>
    </string-array>
</resources>
```

### 8. `screens/home/settings/SettingsFragment.kt` — 绑定新开关

```kt
package com.bili.tv.bili_tv_app.screens.home.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bili.tv.bili_tv_app.databinding.FragmentSettingsBinding
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        // 返回按钮
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 加载当前设置状态
        binding.splashAnimationSwitch.isChecked = SettingsService.splashAnimationEnabled
        binding.danmakuSwitch.isChecked = SettingsService.danmakuEnabled
        binding.adFilterSwitch.isChecked = SettingsService.adFilterEnabled
        binding.sponsorBlockSwitch.isChecked = SettingsService.sponsorBlockEnabled
        binding.autoPlaySwitch.isChecked = SettingsService.autoPlay
        // ↓↓↓ 新增 ↓↓↓
        binding.autoPlayLastVideoSwitch.isChecked = SettingsService.autoPlayLastVideo

        // 保存开关状态
        binding.splashAnimationSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.splashAnimationEnabled = isChecked
        }

        binding.danmakuSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.danmakuEnabled = isChecked
        }

        binding.adFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.adFilterEnabled = isChecked
        }

        binding.sponsorBlockSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.sponsorBlockEnabled = isChecked
        }

        binding.autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.autoPlay = isChecked
        }

        // ↓↓↓ 新增：自动播放上次视频开关 ↓↓↓
        binding.autoPlayLastVideoSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.autoPlayLastVideo = isChecked
            if (!isChecked) {
                // 关闭时清除记录
                SettingsService.clearLastPlayedVideo()
                Toast.makeText(requireContext(), "已清除上次播放记录", Toast.LENGTH_SHORT).show()
            }
        }

        // 画质选择
        val qualities = arrayOf("360P", "480P", "720P", "1080P", "1080P+")
        binding.qualitySpinner.setSelection(getCurrentQualityIndex())

        binding.qualitySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val quality = when (position) {
                    0 -> 16   // 360P
                    1 -> 32   // 480P
                    2 -> 64   // 720P
                    3 -> 80   // 1080P
                    4 -> 112  // 1080P+
                    else -> 80
                }
                SettingsService.defaultQuality = quality
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // 关于
        binding.aboutText.text = "BiliTV v1.2.3\n哔哩哔哩电视版客户端\n\n基于Flutter源码转换"
    }

    private fun getCurrentQualityIndex(): Int {
        return when (SettingsService.defaultQuality) {
            16 -> 0
            32 -> 1
            64 -> 2
            80 -> 3
            112 -> 4
            else -> 3
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 9. `models/Episode.kt` — 新增分集模型

```kt
package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class Episode(
    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("part")
    val part: String = "",

    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("dimension")
    val dimension: Video.Dimension? = null,

    @SerializedName("first_frame")
    val firstFrame: String? = null
) {
    fun getFormattedDuration(): String {
        val totalSeconds = (duration / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
```

### 10. `models/VideoDetail.kt` — 新增视频详情模型（含分集信息）

```kt
package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class VideoDetailResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: VideoDetail? = null
)

data class VideoDetail(
    @SerializedName("bvid")
    val bvid: String = "",

    @SerializedName("aid")
    val aid: Long = 0,

    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("pic")
    val pic: String = "",

    @SerializedName("desc")
    val desc: String = "",

    @SerializedName("pubdate")
    val pubdate: Long = 0,

    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("owner")
    val owner: Video.Owner? = null,

    @SerializedName("stat")
    val stat: Video.Stat? = null,

    @SerializedName("dimension")
    val dimension: Video.Dimension? = null,

    @SerializedName("pages")
    val pages: List<Episode> = emptyList(),

    @SerializedName("tid")
    val tid: Int = 0,

    @SerializedName("tname")
    val tname: String = "",

    @SerializedName("videos")
    val videos: Int = 0,

    @SerializedName("season_id")
    val seasonId: Long = 0,

    @SerializedName("season_type")
    val seasonType: Int = 0,

    @SerializedName("ugc_season")
    val ugcSeason: UgcSeason? = null
) {
    /**
     * 是否是多P视频
     */
    fun isMultiPart(): Boolean = pages.size > 1

    /**
     * 获取当前分集在列表中的索引
     */
    fun getPageIndex(cid: Long): Int {
        return pages.indexOfFirst { it.cid == cid }.coerceAtLeast(0)
    }
}

data class UgcSeason(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("sections")
    val sections: List<UgcSeasonSection> = emptyList()
)

data class UgcSeasonSection(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("episodes")
    val episodes: List<UgcSeasonEpisode> = emptyList()
)

data class UgcSeasonEpisode(
    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("bvid")
    val bvid: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("duration")
    val duration: Long = 0
)
```

### 11. `models/LiveRoom.kt` — 新增直播间模型

```kt
package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class LiveRoom(
    @SerializedName("room_id")
    val roomId: Long = 0,

    @SerializedName("uid")
    val uid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("cover")
    val cover: String = "",

    @SerializedName("uname")
    val uname: String = "",

    @SerializedName("face")
    val face: String = "",

    @SerializedName("live_status")
    val liveStatus: Int = 0,  // 0:未开播, 1:直播中, 2:轮播中

    @SerializedName("area_id")
    val areaId: Int = 0,

    @SerializedName("area_name")
    val areaName: String = "",

    @SerializedName("online")
    val online: Int = 0,

    @SerializedName("play_url")
    val playUrl: String? = null
) {
    fun isLive(): Boolean = liveStatus == 1

    fun getFormattedOnline(): String {
        return when {
            online >= 10000 -> String.format("%.1f万", online / 10000.0)
            else -> online.toString()
        }
    }
}

data class LiveRoomListResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: LiveRoomListData? = null
)

data class LiveRoomListData(
    @SerializedName("list")
    val list: List<LiveRoomItem>? = null,

    @SerializedName("count")
    val count: Int = 0
)

data class LiveRoomItem(
    @SerializedName("roomid")
    val roomid: Long = 0,

    @SerializedName("uid")
    val uid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("cover")
    val cover: String = "",

    @SerializedName("uname")
    val uname: String = "",

    @SerializedName("face")
    val face: String = "",

    @SerializedName("live_status")
    val liveStatus: Int = 0,

    @SerializedName("area_id")
    val areaId: Int = 0,

    @SerializedName("area_name")
    val areaName: String = "",

    @SerializedName("online")
    val online: Int = 0
) {
    fun toLiveRoom(): LiveRoom {
        return LiveRoom(
            roomId = roomid,
            uid = uid,
            title = title,
            cover = cover,
            uname = uname,
            face = face,
            liveStatus = liveStatus,
            areaId = areaId,
            areaName = areaName,
            online = online
        )
    }
}

data class LiveStreamResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: LiveStreamData? = null
)

data class LiveStreamData(
    @SerializedName("playurl_info")
    val playurlInfo: LivePlayUrlInfo? = null
)

data class LivePlayUrlInfo(
    @SerializedName("playurl")
    val playurl: LivePlayUrl? = null
)

data class LivePlayUrl(
    @SerializedName("stream")
    val stream: List<LiveStream>? = null
)

data class LiveStream(
    @SerializedName("protocol_name")
    val protocolName: String = "",

    @SerializedName("format")
    val format: List<LiveFormat>? = null
)

data class LiveFormat(
    @SerializedName("format_name")
    val formatName: String = "",

    @SerializedName("codec")
    val codec: List<LiveCodec>? = null
)

data class LiveCodec(
    @SerializedName("codec_name")
    val codecName: String = "",

    @SerializedName("current_qn")
    val currentQn: Int = 0,

    @SerializedName("base_url")
    val baseUrl: String = "",

    @SerializedName("url_info")
    val urlInfo: List<LiveUrlInfo>? = null
)

data class LiveUrlInfo(
    @SerializedName("host")
    val host: String = "",

    @SerializedName("extra")
    val extra: String = "",

    @SerializedName("stream")
    val stream: String? = null
) {
    fun buildFullUrl(baseUrl: String): String {
        return if (stream != null) {
            "$host$baseUrl?$extra$stream"
        } else {
            "$host$baseUrl?$extra"
        }
    }
}
```

### 12. `services/api/BilibiliApiService.kt` — 增加直播和分集API

```kt
package com.bili.tv.bili_tv_app.services.api

import com.bili.tv.bili_tv_app.models.*
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class BilibiliApiService private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://api.bilibili.com"
        private const val LIVE_URL = "https://api.live.bilibili.com"

        @Volatile
        private var instance: BilibiliApiService? = null

        fun getInstance(): BilibiliApiService {
            return instance ?: synchronized(this) {
                instance ?: BilibiliApiService().also { instance = it }
            }
        }
    }

    /**
     * 构建带认证信息的请求
     */
    private fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://www.bilibili.com")

        val cookies = AuthService.cookies
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies)
        }

        return builder
    }

    /**
     * 获取推荐视频列表
     */
    fun getRecommendVideos(page: Int): List<Video> {
        val url = "$BASE_URL/x/web-interface/popular?ps=20&pn=${page + 1}"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val list = data["list"] as? List<*> ?: return emptyList()

            list.mapNotNull { item ->
                try {
                    gson.fromJson(gson.toJson(item), Video::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取视频详情（含分集信息）
     */
    fun getVideoInfo(bvid: String): VideoDetail? {
        val url = "$BASE_URL/x/web-interface/view?bvid=$bvid"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val resp = gson.fromJson(body, VideoDetailResponse::class.java)
            if (resp.code == 0) resp.data else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取视频播放地址
     */
    fun getPlayUrl(aid: Long, cid: Long, quality: Int): String? {
        val url = "$BASE_URL/x/player/playurl?avid=$aid&cid=$cid&qn=$quality&fnval=16&fourk=1"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return null
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return null

            val data = json["data"] as? Map<*, *> ?: return null

            // 优先使用 DASH 格式
            val dash = data["dash"] as? Map<*, *>
            if (dash != null) {
                val videoList = dash["video"] as? List<*> ?: return null
                // 找到匹配或最高的画质
                val targetVideo = videoList.mapNotNull { v ->
                    try {
                        gson.fromJson(gson.toJson(v), VideoTrack::class.java)
                    } catch (e: Exception) { null }
                }.maxByOrNull { it.bandwidth }

                if (targetVideo != null && targetVideo.baseUrl.isNotEmpty()) {
                    return targetVideo.baseUrl
                }
            }

            // 降级使用 DURL 格式
            val durl = data["durl"] as? List<*>
            if (durl != null && durl.isNotEmpty()) {
                val first = durl[0] as? Map<*, *>
                return first?.get("url") as? String
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 搜索视频
     */
    fun searchVideos(keyword: String): List<Video> {
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val url = "$BASE_URL/x/web-interface/search/type?search_type=video&keyword=$encodedKeyword&page=1"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val result = data["result"] as? List<*> ?: return emptyList()

            result.mapNotNull { item ->
                try {
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    Video(
                        bvid = map["bvid"] as? String ?: "",
                        aid = ((map["aid"] as? Double)?.toLong() ?: 0L),
                        title = (map["title"] as? String)?.replace(Regex("<[^>]+>"), "") ?: "",
                        pic = map["pic"] as? String ?: "",
                        author = map["author"] as? String ?: "",
                        duration = map["duration"] as? String ?: "",
                        view = ((map["play"] as? Double)?.toInt() ?: 0),
                        danmaku = ((map["video_review"] as? Double)?.toInt() ?: 0),
                        owner = Video.Owner(
                            mid = ((map["mid"] as? Double)?.toLong() ?: 0L),
                            name = map["author"] as? String ?: ""
                        )
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ==================== 直播相关 API ====================

    /**
     * 获取关注的主播直播间列表（左右键数据源）
     */
    fun getFollowLiveRooms(page: Int = 1): List<LiveRoom> {
        if (!AuthService.isLoggedIn) return emptyList()

        val url = "$LIVE_URL/xlive/web-ucenter/v1/xfetter/room_list?page=$page&page_size=20"
        val request = buildRequest(url)
            .header("Referer", "https://live.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val list = data["list"] as? List<*> ?: return emptyList()

            list.mapNotNull { item ->
                try {
                    val room = gson.fromJson(gson.toJson(item), LiveRoomItem::class.java)
                    room.toLiveRoom()
                } catch (e: Exception) { null }
            }.filter { it.isLive() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取推荐直播间列表（上下键数据源）
     */
    fun getRecommendLiveRooms(page: Int = 1): List<LiveRoom> {
        val url = "$LIVE_URL/xlive/web-interface/v1/second/getList?platform=web&sort_type=online&page=$page&page_size=20"
        val request = buildRequest(url)
            .header("Referer", "https://live.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val list = data["list"] as? List<*> ?: return emptyList()

            list.mapNotNull { item ->
                try {
                    val room = gson.fromJson(gson.toJson(item), LiveRoomItem::class.java)
                    room.toLiveRoom()
                } catch (e: Exception) { null }
            }.filter { it.isLive() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取直播间播放地址
     */
    fun getLiveStreamUrl(roomId: Long): String? {
        val url = "$LIVE_URL/xlive/web-room/v1/index/getRoomPlayInfo?room_id=$roomId&protocol=0,1&format=0,1,2&codec=0,1&qn=10000&platform=web"
        val request = buildRequest(url)
            .header("Referer", "https://live.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val resp = gson.fromJson(body, LiveStreamResponse::class.java)

            if (resp.code != 0 || resp.data?.playurlInfo?.playurl == null) return null

            val streams = resp.data.playurlInfo.playurl.stream
            if (streams.isNullOrEmpty()) return null

            // 优先找 http-stream (FLV)，其次找 http-hls (HLS)
            for (protocol in streams) {
                if (protocol.formatName == "http_stream" || protocol.formatName == "http_hls") {
                    val codecs = protocol.format?.firstOrNull()?.codec
                    if (!codecs.isNullOrEmpty()) {
                        val codec = codecs.maxByOrNull { it.currentQn }
                        if (codec != null && codec.baseUrl.isNotEmpty()) {
                            val urlInfo = codec.urlInfo?.firstOrNull()
                            if (urlInfo != null) {
                                return urlInfo.buildFullUrl(codec.baseUrl)
                            }
                            return codec.baseUrl
                        }
                    }
                }
            }

            // 最终降级：返回第一个可用的流
            for (stream in streams) {
                for (format in stream.format ?: emptyList()) {
                    for (codec in format.codec ?: emptyList()) {
                        if (codec.baseUrl.isNotEmpty()) {
                            val urlInfo = codec.urlInfo?.firstOrNull()
                            if (urlInfo != null) {
                                return urlInfo.buildFullUrl(codec.baseUrl)
                            }
                            return codec.baseUrl
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
```

### 13. `screens/home/HomeFragment.kt` — 启动时自动播放上次视频

```kt
package com.bili.tv.bili_tv_app.screens.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.databinding.FragmentHomeBinding
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.screens.home.settings.SettingsFragment
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService
import com.bili.tv.bili_tv_app.widgets.VideoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private val videoList = mutableListOf<Video>()

    // 标记是否已经处理过自动播放（防止从播放器返回时再次触发）
    private var autoPlayHandled = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated called")
        setupUI()
        loadContent()
    }

    private fun setupUI() {
        // 视频网格
        videoAdapter = VideoAdapter(videoList) { video ->
            navigateToPlayer(video)
        }

        binding.videosRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = videoAdapter
        }

        // 搜索按钮
        binding.searchButton.setOnClickListener {
            navigateToSearch()
        }

        // 设置按钮
        binding.settingsButton.setOnClickListener {
            navigateToSettings()
        }

        // 用户按钮
        binding.userButton.setOnClickListener {
            if (AuthService.isLoggedIn) {
                showUserInfo()
            } else {
                navigateToLogin()
            }
        }

        // 下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            loadContent()
        }
    }

    private fun loadContent() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true

            val videos = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getRecommendVideos(0)
            }

            videoList.clear()
            videoList.addAll(videos)
            videoAdapter.notifyDataSetChanged()

            binding.swipeRefresh.isRefreshing = false

            if (videoList.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.videosRecyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.videosRecyclerView.visibility = View.VISIBLE
            }

            // ↓↓↓ 自动播放上次视频逻辑 ↓↓↓
            checkAndAutoPlayLastVideo()
        }
    }

    /**
     * 检查并自动播放上次观看的视频
     */
    private fun checkAndAutoPlayLastVideo() {
        if (autoPlayHandled) return
        if (!SettingsService.autoPlayLastVideo) return
        if (!SettingsService.hasLastPlayedVideo()) return
        // 如果是直播，暂不自动播放（直播可能已结束）
        if (SettingsService.lastPlayedIsLive) return

        autoPlayHandled = true

        lifecycleScope.launch {
            delay(500) // 稍微延迟，让首页先渲染出来

            val lastBvid = SettingsService.lastPlayedBvid
            val lastTitle = SettingsService.lastPlayedTitle
            val lastCover = SettingsService.lastPlayedCover

            Log.d("HomeFragment", "自动播放上次视频: $lastBvid - $lastTitle")

            // 显示提示
            Toast.makeText(
                requireContext(),
                "继续播放: $lastTitle",
                Toast.LENGTH_SHORT
            ).show()

            navigateToPlayerWithList(
                Video(
                    bvid = lastBvid,
                    title = lastTitle,
                    pic = lastCover,
                    cid = SettingsService.lastPlayedCid
                ),
                videoList.toList(),
                videoList.indexOfFirst { it.bvid == lastBvid }.coerceAtLeast(0)
            )
        }
    }

    private fun navigateToPlayer(video: Video) {
        navigateToPlayerWithList(video, videoList.toList(), videoList.indexOf(video).coerceAtLeast(0))
    }

    private fun navigateToPlayerWithList(video: Video, list: List<Video>, index: Int) {
        val fragment = com.bili.tv.bili_tv_app.screens.player.PlayerFragment.newInstance(
            bvid = video.bvid,
            title = video.title,
            coverUrl = video.pic,
            isLive = false,
            categoryVideoList = list,
            categoryVideoIndex = index
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToSearch() {
        val fragment = com.bili.tv.bili_tv_app.screens.home.search.SearchFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToSettings() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToLogin() {
        val fragment = com.bili.tv.bili_tv_app.screens.home.login.LoginFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showUserInfo() {
        AuthService.currentUser?.let { user ->
            Toast.makeText(
                requireContext(),
                "欢迎, ${user.uname} (Lv.${user.level})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 14. `res/layout/fragment_player.xml` — 增加切换提示覆盖层

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/playerRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- 播放器 -->
    <androidx.media3.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 弹幕层 -->
    <com.bili.tv.bili_tv_app.widgets.DanmakuView
        android:id="@+id/danmakuView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clickable="false"
        android:focusable="false" />

    <!-- 加载进度 -->
    <ProgressBar
        android:id="@+id/loadingProgress"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_gravity="center"
        android:visibility="gone" />

    <!-- 顶部信息栏 -->
    <LinearLayout
        android:id="@+id/topBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/gradient_top"
        android:orientation="vertical"
        android:padding="16dp"
        android:visibility="visible">

        <!-- 返回按钮 + 标题 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/backButton"
                android:layout_width="44dp"
                android:layout_height="44dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="返回"
                android:src="@android:drawable/ic_menu_revert" />

            <TextView
                android:id="@+id/videoTitle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:layout_weight="1"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="#FFFFFF"
                android:textSize="20sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- 分集/模式指示器 -->
        <LinearLayout
            android:id="@+id/episodeIndicator"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="56dp"
            android:layout_marginTop="4dp"
            android:orientation="horizontal"
            android:visibility="gone">

            <TextView
                android:id="@+id/modeLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="#33FB7299"
                android:paddingHorizontal="8dp"
                android:paddingVertical="2dp"
                android:text="点播"
                android:textColor="#FFFFFF"
                android:textSize="12sp" />

            <TextView
                android:id="@+id/episodeLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:textColor="#AAAAAA"
                android:textSize="14sp" />
        </LinearLayout>
    </LinearLayout>

    <!-- 切换提示浮层 -->
    <LinearLayout
        android:id="@+id/switchOverlay"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="#CC000000"
        android:gravity="center"
        android:orientation="vertical"
        android:paddingHorizontal="32dp"
        android:paddingVertical="20dp"
        android:visibility="gone">

        <ImageView
            android:id="@+id/switchIcon"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:contentDescription="切换方向"
            android:src="@android:drawable/ic_media_previous" />

        <TextView
            android:id="@+id/switchTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:ellipsize="end"
            android:maxLines="2"
            android:maxWidth="280dp"
            android:textColor="#FFFFFF"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/switchSubtitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="#AAAAAA"
            android:textSize="13sp" />
    </LinearLayout>

    <!-- 底部提示 -->
    <TextView
        android:id="@+id/hintText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="24dp"
        android:text="← → 切换分集  ↑ ↓ 切换视频"
        android:textColor="#66FFFFFF"
        android:textSize="13sp"
        android:visibility="gone" />

</FrameLayout>
```

### 15. `screens/player/PlayerFragment.kt` — 完整重写，支持方向键切换

```kt
package com.bili.tv.bili_tv_app.screens.player

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.util.UnstableApi
import com.bili.tv.bili_tv_app.databinding.FragmentPlayerBinding
import com.bili.tv.bili_tv_app.models.Episode
import com.bili.tv.bili_tv_app.models.LiveRoom
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.models.VideoDetail
import com.bili.tv.bili_tv_app.services.SettingsService
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.widgets.DanmakuView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var danmakuView: DanmakuView? = null

    // 当前播放信息
    private var bvid: String = ""
    private var title: String = ""
    private var coverUrl: String = ""
    private var currentAid: Long = 0
    private var currentCid: Long = 0

    // 模式：直播 or 点播
    private var isLiveMode: Boolean = false
    private var liveRoomId: Long = 0

    // 点播模式 - 分集数据
    private var videoDetail: VideoDetail? = null
    private var episodeList: List<Episode> = emptyList()
    private var currentEpisodeIndex: Int = 0

    // 点播模式 - 分类列表（上下键）
    private var categoryVideoList: List<Video> = emptyList()
    private var categoryVideoIndex: Int = 0

    // 直播模式 - 数据源
    private var followLiveList: List<LiveRoom> = emptyList()
    private var followLiveIndex: Int = 0
    private var recommendLiveList: List<LiveRoom> = emptyList()
    private var recommendLiveIndex: Int = 0

    // 切换提示
    private val handler = Handler(Looper.getMainLooper())
    private var hideSwitchOverlayRunnable: Runnable? = null
    private var hideHintRunnable: Runnable? = null

    // 上次按键时间（防抖）
    private var lastKeyTime: Long = 0
    private val keyDebounceMs = 600L

    // 加载协程
    private var loadJob: Job? = null

    companion object {
        private const val ARG_BVID = "bvid"
        private const val ARG_TITLE = "title"
        private const val ARG_COVER = "cover"
        private const val ARG_IS_LIVE = "is_live"
        private const val ARG_LIVE_ROOM_ID = "live_room_id"
        private const val ARG_CATEGORY_VIDEOS = "category_videos"
        private const val ARG_CATEGORY_INDEX = "category_index"

        fun newInstance(
            bvid: String,
            title: String,
            coverUrl: String,
            isLive: Boolean = false,
            liveRoomId: Long = 0L,
            categoryVideoList: List<Video> = emptyList(),
            categoryVideoIndex: Int = 0
        ): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BVID, bvid)
                    putString(ARG_TITLE, title)
                    putString(ARG_COVER, coverUrl)
                    putBoolean(ARG_IS_LIVE, isLive)
                    putLong(ARG_LIVE_ROOM_ID, liveRoomId)
                    putStringArrayList(ARG_CATEGORY_VIDEOS, ArrayList(categoryVideoList.map {
                        "${it.bvid}|${it.title}|${it.pic}|${it.cid}"
                    }))
                    putInt(ARG_CATEGORY_INDEX, categoryVideoIndex)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            bvid = it.getString(ARG_BVID, "")
            title = it.getString(ARG_TITLE, "")
            coverUrl = it.getString(ARG_COVER, "")
            isLiveMode = it.getBoolean(ARG_IS_LIVE, false)
            liveRoomId = it.getLong(ARG_LIVE_ROOM_ID, 0L)

            // 解析分类视频列表
            val videoStrings = it.getStringArrayList(ARG_CATEGORY_VIDEOS)
            if (videoStrings != null) {
                categoryVideoList = videoStrings.mapNotNull { str ->
                    val parts = str.split("|")
                    if (parts.size >= 3) {
                        Video(
                            bvid = parts[0],
                            title = parts[1],
                            pic = parts[2],
                            cid = if (parts.size > 3) parts[3].toLongOrNull() ?: 0L else 0L
                        )
                    } else null
                }
            }
            categoryVideoIndex = it.getInt(ARG_CATEGORY_INDEX, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPlayer()
        setupKeyHandler()
        setupUI()

        if (isLiveMode) {
            loadLiveRoom()
        } else {
            loadVideo()
        }
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            savePlaybackProgress()
            parentFragmentManager.popBackStack()
        }

        binding.videoTitle.text = title

        // 根据模式更新UI
        updateModeUI()
    }

    private fun updateModeUI() {
        if (isLiveMode) {
            binding.modeLabel.text = "直播"
            binding.episodeIndicator.visibility = View.VISIBLE
            binding.episodeLabel.text = "房间号: $liveRoomId"
            binding.hintText.text = "← → 切换关注直播间  ↑ ↓ 切换推荐直播间"
            binding.hintText.visibility = View.VISIBLE
            hideHintRunnable?.let { handler.removeCallbacks(it) }
            hideHintRunnable = Runnable { binding.hintText.visibility = View.GONE }
            handler.postDelayed(hideHintRunnable!!, 5000)
        } else {
            binding.modeLabel.text = "点播"
            binding.episodeIndicator.visibility = View.GONE
            binding.hintText.text = "← → 切换分集  ↑ ↓ 切换视频"
            binding.hintText.visibility = View.VISIBLE
            hideHintRunnable?.let { handler.removeCallbacks(it) }
            hideHintRunnable = Runnable { binding.hintText.visibility = View.GONE }
            handler.postDelayed(hideHintRunnable!!, 5000)
        }
    }

    private fun setupPlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://www.bilibili.com",
                "Origin" to "https://www.bilibili.com"
            ))

        val dataSourceFactory = DefaultDataSource.Factory(requireContext(), httpDataSourceFactory)

        player = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory))
            .build().also {
                binding.playerView.player = it

                it.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                binding.loadingProgress.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                binding.loadingProgress.visibility = View.GONE
                                it.volume = 1.0f
                                it.playWhenReady = true
                                // 恢复进度
                                if (!isLiveMode) {
                                    val savedProgress = SettingsService.lastPlayedProgress
                                    if (savedProgress > 0 && savedProgress < (it.duration * 0.95)) {
                                        it.seekTo(savedProgress)
                                    }
                                }
                            }
                            Player.STATE_ENDED -> {
                                binding.loadingProgress.visibility = View.GONE
                                if (!isLiveMode) {
                                    playNextEpisodeOrVideo()
                                }
                            }
                            Player.STATE_IDLE -> {
                                binding.loadingProgress.visibility = View.GONE
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("PlayerFragment", "播放错误: ${error.message}", error)
                        binding.loadingProgress.visibility = View.GONE
                        Toast.makeText(requireContext(), "播放错误: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }

        if (SettingsService.danmakuEnabled) {
            setupDanmaku()
        }
    }

    private fun setupDanmaku() {
        danmakuView = binding.danmakuView
        danmakuView?.apply {
            setTextSize(SettingsService.danmakuFontSize)
            setAlpha(SettingsService.danmakuOpacity)
            setSpeed(SettingsService.danmakuDensity)
        }
    }

    // ==================== 方向键处理 ====================

    private fun setupKeyHandler() {
        // 在 PlayerView 上设置按键监听，拦截默认的快进快退行为
        binding.playerView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleDpadKey(keyCode)
            } else false
        }

        // 同时在根布局上设置，作为备用
        binding.playerRoot.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleDpadKey(keyCode)
            } else false
        }

        binding.playerRoot.isFocusableInTouchMode = true
        binding.playerRoot.requestFocus()
    }

    private fun handleDpadKey(keyCode: Int): Boolean {
        // 防抖：短时间内不重复触发
        val now = System.currentTimeMillis()
        if (now - lastKeyTime < keyDebounceMs) return false
        lastKeyTime = now

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isLiveMode) switchFollowLiveRoom(-1) else switchEpisode(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isLiveMode) switchFollowLiveRoom(1) else switchEpisode(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isLiveMode) switchRecommendLiveRoom(-1) else switchCategoryVideo(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isLiveMode) switchRecommendLiveRoom(1) else switchCategoryVideo(1)
                return true
            }
        }
        return false
    }

    // ==================== 点播模式：切换逻辑 ====================

    /**
     * 切换分集（左右键）
     * @param direction -1=上一集, +1=下一集
     */
    private fun switchEpisode(direction: Int) {
        if (episodeList.size <= 1) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
                title = "没有更多分集",
                subtitle = ""
            )
            return
        }

        val newIndex = currentEpisodeIndex + direction
        if (newIndex < 0 || newIndex >= episodeList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
                title = "已是${if (direction < 0) "第一" else "最后一"}集",
                subtitle = ""
            )
            return
        }

        currentEpisodeIndex = newIndex
        val episode = episodeList[newIndex]
        currentCid = episode.cid

        Log.d("PlayerFragment", "切换分集: P${episode.page} - ${episode.part}")

        updateEpisodeUI()
        showSwitchOverlay(
            iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
            title = "P${episode.page} ${episode.part}",
            subtitle = "${newIndex + 1}/${episodeList.size}"
        )

        // 加载新分集
        loadEpisodePlayUrl(episode.cid)
    }

    /**
     * 切换分类视频（上下键）
     * @param direction -1=上一个视频, +1=下一个视频
     */
    private fun switchCategoryVideo(direction: Int) {
        if (categoryVideoList.isEmpty()) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
                title = "没有更多视频",
                subtitle = ""
            )
            return
        }

        val newIndex = categoryVideoIndex + direction
        if (newIndex < 0 || newIndex >= categoryVideoList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
                title = "已是${if (direction < 0) "第一" else "最后"}个视频",
                subtitle = ""
            )
            return
        }

        categoryVideoIndex = newIndex
        val video = categoryVideoList[newIndex]

        Log.d("PlayerFragment", "切换分类视频: ${video.bvid} - ${video.title}")

        showSwitchOverlay(
            iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
            title = video.title,
            subtitle = "${newIndex + 1}/${categoryVideoList.size}"
        )

        // 切换到新视频（重置分集状态）
        bvid = video.bvid
        title = video.title
        coverUrl = video.pic
        currentCid = video.cid
        currentEpisodeIndex = 0
        episodeList = emptyList()
        videoDetail = null

        binding.videoTitle.text = title
        loadVideo()
    }

    /**
     * 播放下一个分集或下一个视频
     */
    private fun playNextEpisodeOrVideo() {
        // 优先尝试下一集
        if (episodeList.size > 1 && currentEpisodeIndex < episodeList.size - 1) {
            switchEpisode(1)
            return
        }
        // 其次尝试下一个视频
        if (categoryVideoList.isNotEmpty() && categoryVideoIndex < categoryVideoList.size - 1) {
            switchCategoryVideo(1)
        }
    }

    // ==================== 直播模式：切换逻辑 ====================

    /**
     * 切换关注列表直播间（左右键）
     */
    private fun switchFollowLiveRoom(direction: Int) {
        if (followLiveList.isEmpty()) {
            // 如果还没有加载过，先加载
            loadFollowLiveRooms()
            return
        }

        val newIndex = followLiveIndex + direction
        if (newIndex < 0 || newIndex >= followLiveList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
                title = "已是关注列表${if (direction < 0) "第一个" else "最后一个"}直播间",
                subtitle = "(关注列表)"
            )
            return
        }

        followLiveIndex = newIndex
        val room = followLiveList[newIndex]
        switchToLiveRoom(room, "关注列表")
    }

    /**
     * 切换推荐直播间（上下键）
     */
    private fun switchRecommendLiveRoom(direction: Int) {
        if (recommendLiveList.isEmpty()) {
            // 如果还没有加载过，先加载
            loadRecommendLiveRooms()
            return
        }

        val newIndex = recommendLiveIndex + direction
        if (newIndex < 0 || newIndex >= recommendLiveList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
                title = "已是推荐列表${if (direction < 0) "第一个" else "最后一个"}直播间",
                subtitle = "(推荐列表)"
            )
            return
        }

        recommendLiveIndex = newIndex
        val room = recommendLiveList[newIndex]
        switchToLiveRoom(room, "推荐列表")
    }

    /**
     * 切换到指定直播间
     */
    private fun switchToLiveRoom(room: LiveRoom, source: String) {
        liveRoomId = room.roomId
        title = room.title
        bvid = "" // 直播没有bvid

        binding.videoTitle.text = title
        binding.episodeLabel.text = "${room.uname} - 房间号: ${room.roomId}"

        showSwitchOverlay(
            iconRes = android.R.drawable.ic_media_play,
            title = room.title,
            subtitle = "$source · ${room.uname} · ${room.getFormattedOnline()}人观看"
        )

        Log.d("PlayerFragment", "切换直播间: ${room.roomId} - ${room.title}")
        loadLiveStream(room.roomId)
    }

    private fun loadFollowLiveRooms() {
        lifecycleScope.launch {
            showSwitchOverlay(
                iconRes = android.R.drawable.ic_menu_rotate,
                title = "正在加载关注列表...",
                subtitle = ""
            )
            val rooms = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getFollowLiveRooms()
            }
            if (rooms.isNotEmpty()) {
                followLiveList = rooms
                followLiveIndex = 0
                hideSwitchOverlay()
                switchFollowLiveRoom(1)
            } else {
                showSwitchOverlay(
                    iconRes = android.R.drawable.ic_dialog_alert,
                    title = "关注列表为空或未登录",
                    subtitle = "请先在B站APP关注一些主播"
                )
            }
        }
    }

    private fun loadRecommendLiveRooms() {
        lifecycleScope.launch {
            showSwitchOverlay(
                iconRes = android.R.drawable.ic_menu_rotate,
                title = "正在加载推荐列表...",
                subtitle = ""
            )
            val rooms = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getRecommendLiveRooms()
            }
            if (rooms.isNotEmpty()) {
                recommendLiveList = rooms
                recommendLiveIndex = 0
                hideSwitchOverlay()
                switchRecommendLiveRoom(1)
            } else {
                showSwitchOverlay(
                    iconRes = android.R.drawable.ic_dialog_alert,
                    title = "推荐列表加载失败",
                    subtitle = "请稍后重试"
                )
            }
        }
    }

    // ==================== 加载逻辑 ====================

    private fun loadVideo() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                Log.d("PlayerFragment", "加载视频: $bvid")

                val info = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getVideoInfo(bvid)
                }

                if (info == null) {
                    Toast.makeText(requireContext(), "获取视频信息失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                videoDetail = info
                currentAid = info.aid

                // 如果没有指定cid，使用视频默认cid
                if (currentCid == 0L) {
                    currentCid = info.cid
                }

                // 设置分集列表
                episodeList = info.pages
                currentEpisodeIndex = info.getPageIndex(currentCid)

                binding.videoTitle.text = info.title
                title = info.title

                updateEpisodeUI()

                // 获取播放地址
                val videoUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getPlayUrl(
                        info.aid,
                        currentCid,
                        SettingsService.defaultQuality
                    )
                }

                if (videoUrl == null) {
                    Toast.makeText(requireContext(), "获取播放地址失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                playUrl(videoUrl)

                // 保存播放记录
                savePlaybackRecord()

            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载视频异常", e)
                Toast.makeText(requireContext(), "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadEpisodePlayUrl(cid: Long) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                val videoUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getPlayUrl(
                        currentAid,
                        cid,
                        SettingsService.defaultQuality
                    )
                }

                if (videoUrl == null) {
                    Toast.makeText(requireContext(), "获取分集播放地址失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                playUrl(videoUrl)
                savePlaybackRecord()

            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载分集异常", e)
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadLiveRoom() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                Log.d("PlayerFragment", "加载直播间: $liveRoomId")

                // 预加载直播列表
                launch { loadLiveListsInBackground() }

                loadLiveStream(liveRoomId)
            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载直播异常", e)
                Toast.makeText(requireContext(), "加载直播失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadLiveStream(roomId: Long) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                val streamUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getLiveStreamUrl(roomId)
                }

                if (streamUrl == null) {
                    Toast.makeText(requireContext(), "直播间未开播或获取流失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                Log.d("PlayerFragment", "直播流地址: ${streamUrl.take(80)}...")
                playUrl(streamUrl)

                // 保存播放记录
                SettingsService.saveLastPlayedVideo(
                    bvid = "",
                    title = title,
                    cover = "",
                    cid = 0,
                    progress = 0,
                    isLive = true,
                    roomId = roomId
                )

            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载直播流异常", e)
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    /**
     * 后台预加载直播列表
     */
    private suspend fun loadLiveListsInBackground() {
        withContext(Dispatchers.IO) {
            try {
                val follow = BilibiliApiService.getInstance().getFollowLiveRooms()
                if (follow.isNotEmpty()) {
                    followLiveList = follow
                    followLiveIndex = follow.indexOfFirst { it.roomId == liveRoomId }.coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Log.w("PlayerFragment", "预加载关注列表失败", e)
            }

            try {
                val recommend = BilibiliApiService.getInstance().getRecommendLiveRooms()
                if (recommend.isNotEmpty()) {
                    recommendLiveList = recommend
                    recommendLiveIndex = recommend.indexOfFirst { it.roomId == liveRoomId }.coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Log.w("PlayerFragment", "预加载推荐列表失败", e)
            }
        }
    }

    private fun playUrl(url: String) {
        player?.let {
            it.stop()
            val mediaItem = MediaItem.fromUri(url)
            it.setMediaItem(mediaItem)
            it.prepare()
            it.playWhenReady = true
        }
    }

    // ==================== UI 更新 ====================

    private fun updateEpisodeUI() {
        if (episodeList.size > 1) {
            val episode = episodeList.getOrNull(currentEpisodeIndex)
            binding.episodeIndicator.visibility = View.VISIBLE
            binding.episodeLabel.text = if (episode != null) {
                "P${episode.page} ${episode.part}  (${currentEpisodeIndex + 1}/${episodeList.size})"
            } else {
                "${currentEpisodeIndex + 1}/${episodeList.size}"
            }
        } else {
            binding.episodeIndicator.visibility = if (isLiveMode) View.VISIBLE else View.GONE
        }
    }

    private fun showSwitchOverlay(iconRes: Int, titleText: String, subtitle: String) {
        binding.switchOverlay.visibility = View.VISIBLE
        binding.switchIcon.setImageResource(iconRes)
        binding.switchTitle.text = titleText
        binding.switchSubtitle.text = subtitle

        hideSwitchOverlayRunnable?.let { handler.removeCallbacks(it) }
        hideSwitchOverlayRunnable = Runnable {
            binding.switchOverlay.visibility = View.GONE
        }
        handler.postDelayed(hideSwitchOverlayRunnable!!, 2000)
    }

    private fun hideSwitchOverlay() {
        binding.switchOverlay.visibility = View.GONE
        hideSwitchOverlayRunnable?.let { handler.removeCallbacks(it) }
    }

    // ==================== 播放记录 ====================

    private fun savePlaybackRecord() {
        if (isLiveMode) return

        SettingsService.saveLastPlayedVideo(
            bvid = bvid,
            title = title,
            cover = coverUrl,
            cid = currentCid,
            progress = 0 // 进入时进度为0，退出时在 savePlaybackProgress 保存
        )
    }

    private fun savePlaybackProgress() {
        if (isLiveMode) return
        val currentPosition = player?.currentPosition ?: 0L
        val duration = player?.duration ?: 0L

        if (currentPosition > 0 && duration > 0) {
            SettingsService.lastPlayedProgress = currentPosition
        }
    }## 问题分析与修复方案

### Bug 1：二维码登录失败根因
`LoginStatusData.tokenInfo` 模型中 `mid` 字段定义在嵌套的 `TokenInfoDetail` 里，但B站API实际返回的 `token_info.mid` 是**直接在第一层**的，导致 `tokenInfo.tokenInfo?.mid` 永远是 `null`，用户信息获取失败，登录状态未持久化。

### Bug 2：自动播放上次视频
需要在 `SettingsService` 增加持久化字段，`SettingsFragment` 增加开关，`HomeFragment` 启动时判断并跳转。

### Bug 3：方向键切换逻辑
需要在 `PlayerFragment` 拦截 D-pad 按键，根据直播/点播模式分发到不同的数据源。

---

## 修改文件清单

### 1. `models/Login.kt` — 修复 TokenInfo 模型，增加 mid 字段

```kt
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
    val mid: Long = 0,          // ← 修复：B站API直接在token_info下返回mid

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
```

### 2. `services/AuthService.kt` — 完整重写，基于 Cookie 持久化登录

```kt
package com.bili.tv.bili_tv_app.services

import android.content.Context
import android.content.SharedPreferences
import com.bili.tv.bili_tv_app.models.User
import com.google.gson.Gson

object AuthService {

    private const val PREFS_NAME = "bili_auth"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_IN = "expires_in"
    private const val KEY_USER_JSON = "user_json"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    var isLoggedIn: Boolean = false
        private set
    var currentUser: User? = null
        private set
    var cookies: String = ""
        private set
    var accessToken: String = ""
        private set

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        restoreLoginState()
    }

    private fun restoreLoginState() {
        isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            cookies = prefs.getString(KEY_COOKIES, "") ?: ""
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
            val userJson = prefs.getString(KEY_USER_JSON, null)
            if (userJson != null) {
                try {
                    currentUser = gson.fromJson(userJson, User::class.java)
                } catch (e: Exception) {
                    // 数据损坏，清除登录状态
                    clearLogin()
                }
            }
            // 校验：如果没有 cookies 或用户信息，视为未登录
            if (cookies.isEmpty() || currentUser == null) {
                clearLogin()
            }
        }
    }

    /**
     * 保存登录信息 — 以 Cookie 为主，token 为辅
     */
    fun saveLoginInfo(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
        cookies: String,
        user: User
    ) {
        this.accessToken = accessToken
        this.cookies = cookies
        this.currentUser = user
        this.isLoggedIn = true

        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_COOKIES, cookies)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_IN, expiresIn)
            .putString(KEY_USER_JSON, gson.toJson(user))
            .apply()
    }

    /**
     * 从 Cookie 字符串中提取指定字段值
     */
    fun getCookieValue(name: String): String {
        val pattern = "$name="
        val start = cookies.indexOf(pattern)
        if (start == -1) return ""
        val valueStart = start + pattern.length
        val end = cookies.indexOf(';', valueStart)
        return if (end == -1) cookies.substring(valueStart) else cookies.substring(valueStart, end)
    }

    /**
     * 获取 DedeUserID（B站用户ID，存在于Cookie中）
     */
    fun getDedeUserId(): Long {
        val uid = getCookieValue("DedeUserID").trim()
        return uid.toLongOrNull() ?: 0L
    }

    fun clearLogin() {
        isLoggedIn = false
        currentUser = null
        cookies = ""
        accessToken = ""
        prefs.edit().clear().apply()
    }
}
```

### 3. `services/api/AuthApi.kt` — 完整实现，支持 Cookie 方式获取用户信息

```kt
package com.bili.tv.bili_tv_app.services.api

import com.bili.tv.bili_tv_app.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AuthApi private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://passport.bilibili.com"
        private const val API_URL = "https://api.bilibili.com"

        @Volatile
        private var instance: AuthApi? = null

        fun getInstance(): AuthApi {
            return instance ?: synchronized(this) {
                instance ?: AuthApi().also { instance = it }
            }
        }
    }

    /**
     * 获取二维码登录链接
     */
    fun getQRCode(): LoginQRCodeResponse {
        val url = "$BASE_URL/x/passport-login/web/qrcode/generate"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return LoginQRCodeResponse(code = -1, message = "网络错误")

        return try {
            val resp = gson.fromJson(body, LoginQRCodeResponse::class.java)
            if (resp.code == 0 && resp.data != null) {
                resp
            } else {
                LoginQRCodeResponse(code = resp.code, message = resp.message)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LoginQRCodeResponse(code = -1, message = "解析错误: ${e.message}")
        }
    }

    /**
     * 轮询二维码扫描状态
     * 返回 null 表示请求失败（应继续轮询）
     */
    fun checkQRCodeStatus(qrcodeKey: String): LoginStatusResponse? {
        val url = "$BASE_URL/x/passport-login/web/qrcode/poll?qrcode_key=${URLEncoder.encode(qrcodeKey, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, LoginStatusResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 通过 Cookie 获取当前登录用户信息（推荐方式）
     */
    fun getUserInfoByCookie(cookies: String): UserInfoResponse? {
        val url = "$API_URL/x/web-interface/nav"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://www.bilibili.com")
            .header("Cookie", cookies)
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, UserInfoResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 通过 mid 获取用户信息卡片
     */
    fun getLoginInfo(mid: Long): UserInfoCardResponse? {
        if (mid <= 0) return null
        val url = "$API_URL/x/web-interface/card?mid=$mid"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://www.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, UserInfoCardResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// ============ 响应数据模型 ============

data class UserInfoResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("data")
    val data: UserInfoData? = null
)

data class UserInfoData(
    @SerializedName("mid")
    val mid: Long = 0,
    @SerializedName("uname")
    val uname: String = "",
    @SerializedName("face")
    val face: String = "",
    @SerializedName("sign")
    val sign: String = "",
    @SerializedName("level")
    val level: Int = 0,
    @SerializedName("vip_type")
    val vipType: Int = 0,
    @SerializedName("vip_status")
    val vipStatus: Int = 0,
    @SerializedName("isLogin")
    val isLogin: Boolean = false
)

data class UserInfoCardResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("data")
    val data: UserCardData? = null
)

data class UserCardData(
    @SerializedName("card")
    val card: UserCard? = null
)

data class UserCard(
    @SerializedName("mid")
    val mid: Long = 0,
    @SerializedName("name")
    val name: String = "",
    @SerializedName("face")
    val face: String = "",
    @SerializedName("sign")
    val sign: String = "",
    @SerializedName("level")
    val level: Int = 0,
    @SerializedName("vip_type")
    val vipType: Int = 0,
    @SerializedName("vip_status")
    val vipStatus: Int = 0
)

data class DanmakuSegment(
    val p: String = "",
    val m: String = ""
)
```

### 4. `screens/home/login/LoginFragment.kt` — 修复登录流程

```kt
package com.bili.tv.bili_tv_app.screens.home.login

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bili.tv.bili_tv_app.databinding.FragmentLoginBinding
import com.bili.tv.bili_tv_app.models.User
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.api.AuthApi
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private var qrcodeKey: String = ""
    private var isPolling = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        generateQRCode()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun generateQRCode() {
        lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE
            binding.loginStatus.text = "正在获取二维码..."

            val qrCode = withContext(Dispatchers.IO) {
                AuthApi.getInstance().getQRCode()
            }

            binding.loadingProgress.visibility = View.GONE

            if (qrCode.code == 0 && qrCode.data != null) {
                qrcodeKey = qrCode.data.qrcodeKey

                try {
                    val barcodeEncoder = BarcodeEncoder()
                    val bitmap = barcodeEncoder.encodeBitmap(
                        qrCode.data.url,
                        com.google.zxing.BarcodeFormat.QR_CODE,
                        400,
                        400
                    )
                    binding.qrCodeImage.setImageBitmap(bitmap)
                    binding.loginStatus.text = "请使用B站APP扫描二维码登录"
                } catch (e: Exception) {
                    e.printStackTrace()
                    binding.loginStatus.text = "二维码生成失败"
                    Toast.makeText(requireContext(), "二维码生成失败", Toast.LENGTH_SHORT).show()
                }

                startPolling()
            } else {
                binding.loginStatus.text = "获取二维码失败: ${qrCode.message}"
                Toast.makeText(requireContext(), "获取二维码失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true

        lifecycleScope.launch {
            while (isPolling && qrcodeKey.isNotEmpty() && isAdded) {
                delay(2000)

                if (!isAdded) break

                val status = withContext(Dispatchers.IO) {
                    AuthApi.getInstance().checkQRCodeStatus(qrcodeKey)
                }

                if (status == null) continue  // 网络错误，继续轮询

                status.data?.let { data ->
                    when (data.code) {
                        0 -> {
                            // 登录成功
                            isPolling = false
                            binding.loginStatus.text = "登录成功，正在获取用户信息..."
                            onLoginSuccess(data)
                        }
                        -1, 86038 -> {
                            // 二维码过期
                            isPolling = false
                            binding.loginStatus.text = "二维码已过期"
                            Toast.makeText(requireContext(), "二维码已过期，请重新获取", Toast.LENGTH_SHORT).show()
                            delay(1000)
                            generateQRCode()
                        }
                        86090 -> {
                            // 未扫码
                            if (binding.loginStatus.text.toString() != "请使用B站APP扫描二维码登录") {
                                binding.loginStatus.text = "请使用B站APP扫描二维码登录"
                            }
                        }
                        86101 -> {
                            // 已扫码，未确认
                            binding.loginStatus.text = "已扫码，请在手机上确认登录"
                        }
                    }
                }
            }
        }
    }

    private fun onLoginSuccess(data: com.bili.tv.bili_tv_app.models.LoginStatusData) {
        lifecycleScope.launch {
            try {
                // ========== 核心：先拼装 Cookie 字符串 ==========
                val cookiesList = data.cookieInfo?.cookies
                if (cookiesList.isNullOrEmpty()) {
                    Log.e("LoginFragment", "登录成功但未返回Cookie信息")
                    Toast.makeText(requireContext(), "登录异常：未获取到Cookie", Toast.LENGTH_LONG).show()
                    binding.loginStatus.text = "登录异常，请重试"
                    return@launch
                }

                val cookiesString = cookiesList.joinToString("; ") { "${it.name}=${it.value}" }
                Log.d("LoginFragment", "Cookie获取成功: ${cookiesString.take(50)}...")

                // ========== 用 Cookie 获取用户信息（最可靠的方式） ==========
                val userInfo = withContext(Dispatchers.IO) {
                    AuthApi.getInstance().getUserInfoByCookie(cookiesString)
                }

                val user: User? = if (userInfo != null && userInfo.code == 0 && userInfo.data != null) {
                    val info = userInfo.data
                    Log.d("LoginFragment", "通过Cookie获取用户信息成功: ${info.uname}, mid: ${info.mid}")
                    User(
                        mid = info.mid,
                        uname = info.uname,
                        face = info.face,
                        sign = info.sign,
                        level = info.level,
                        vipType = info.vipType,
                        vipStatus = info.vipStatus
                    )
                } else {
                    // 降级方案：从 tokenInfo.mid 获取（修复后模型已有此字段）
                    val mid = data.tokenInfo?.mid ?: 0L
                    if (mid > 0) {
                        Log.d("LoginFragment", "降级：通过mid=$mid 获取用户信息")
                        val cardResp = withContext(Dispatchers.IO) {
                            AuthApi.getInstance().getLoginInfo(mid)
                        }
                        cardResp?.data?.card?.let { card ->
                            User(
                                mid = card.mid,
                                uname = card.name,
                                face = card.face,
                                sign = card.sign,
                                level = card.level,
                                vipType = card.vipType,
                                vipStatus = card.vipStatus
                            )
                        }
                    } else {
                        // 最终降级：从 Cookie 中提取 DedeUserID
                        val dedeUserId = cookiesList.find { it.name == "DedeUserID" }?.value?.toLongOrNull() ?: 0L
                        if (dedeUserId > 0) {
                            Log.d("LoginFragment", "最终降级：从Cookie提取DedeUserID=$dedeUserId")
                            val cardResp = withContext(Dispatchers.IO) {
                                AuthApi.getInstance().getLoginInfo(dedeUserId)
                            }
                            cardResp?.data?.card?.let { card ->
                                User(
                                    mid = card.mid,
                                    uname = card.name,
                                    face = card.face,
                                    sign = card.sign,
                                    level = card.level,
                                    vipType = card.vipType,
                                    vipStatus = card.vipStatus
                                )
                            }
                        } else {
                            null
                        }
                    }

                // ========== 持久化登录状态 ==========
                if (user != null) {
                    val token = data.tokenInfo?.accessToken ?: ""
                    val refreshToken = data.tokenInfo?.refreshToken ?: data.refreshToken ?: ""
                    val expiresIn = data.tokenInfo?.expiresIn ?: 0L

                    AuthService.saveLoginInfo(
                        accessToken = token,
                        refreshToken = refreshToken,
                        expiresIn = expiresIn,
                        cookies = cookiesString,
                        user = user
                    )

                    Log.d("LoginFragment", "登录状态保存成功: isLoggedIn=${AuthService.isLoggedIn}")
                    Toast.makeText(requireContext(), "登录成功: ${user.uname}", Toast.LENGTH_SHORT).show()
                    binding.loginStatus.text = "登录成功！"

                    // 确保保存完成后再返回
                    delay(300)
                    parentFragmentManager.popBackStack()
                } else {
                    Log.e("LoginFragment", "无法获取用户信息")
                    Toast.makeText(requireContext(), "登录异常：无法获取用户信息", Toast.LENGTH_LONG).show()
                    binding.loginStatus.text = "获取用户信息失败，请重试"
                }
            } catch (e: Exception) {
                Log.e("LoginFragment", "登录处理异常", e)
                Toast.makeText(requireContext(), "登录处理异常: ${e.message}", Toast.LENGTH_LONG).show()
                binding.loginStatus.text = "登录异常，请重试"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isPolling = false
        _binding = null
    }
}
```

### 5. `services/SettingsService.kt` — 增加自动播放上次视频设置

```kt
package com.bili.tv.bili_tv_app.services

import android.content.Context
import android.content.SharedPreferences

object SettingsService {

    private const val PREFS_NAME = "bili_settings"

    // 播放设置
    var defaultQuality: Int
        get() = prefs.getInt("default_quality", 80)
        set(value) = prefs.edit().putInt("default_quality", value).apply()

    var autoPlay: Boolean
        get() = prefs.getBoolean("auto_play", true)
        set(value) = prefs.edit().putBoolean("auto_play", value).apply()

    // ↓↓↓ 新增：自动播放上次视频 ↓↓↓
    var autoPlayLastVideo: Boolean
        get() = prefs.getBoolean("auto_play_last_video", false)
        set(value) = prefs.edit().putBoolean("auto_play_last_video", value).apply()

    // 上次播放的视频信息
    var lastPlayedBvid: String
        get() = prefs.getString("last_played_bvid", "") ?: ""
        set(value) = prefs.edit().putString("last_played_bvid", value).apply()

    var lastPlayedTitle: String
        get() = prefs.getString("last_played_title", "") ?: ""
        set(value) = prefs.edit().putString("last_played_title", value).apply()

    var lastPlayedCover: String
        get() = prefs.getString("last_played_cover", "") ?: ""
        set(value) = prefs.edit().putString("last_played_cover", value).apply()

    var lastPlayedCid: Long
        get() = prefs.getLong("last_played_cid", 0L)
        set(value) = prefs.edit().putLong("last_played_cid", value).apply()

    var lastPlayedProgress: Long
        get() = prefs.getLong("last_played_progress", 0L)
        set(value) = prefs.edit().putLong("last_played_progress", value).apply()

    var lastPlayedIsLive: Boolean
        get() = prefs.getBoolean("last_played_is_live", false)
        set(value) = prefs.edit().putBoolean("last_played_is_live", value).apply()

    var lastPlayedRoomId: Long
        get() = prefs.getLong("last_played_room_id", 0L)
        set(value) = prefs.edit().putLong("last_played_room_id", value).apply()

    /**
     * 保存上次播放的视频信息
     */
    fun saveLastPlayedVideo(
        bvid: String,
        title: String,
        cover: String,
        cid: Long,
        progress: Long,
        isLive: Boolean = false,
        roomId: Long = 0L
    ) {
        lastPlayedBvid = bvid
        lastPlayedTitle = title
        lastPlayedCover = cover
        lastPlayedCid = cid
        lastPlayedProgress = progress
        lastPlayedIsLive = isLive
        lastPlayedRoomId = roomId
    }

    /**
     * 清除上次播放记录
     */
    fun clearLastPlayedVideo() {
        prefs.edit()
            .remove("last_played_bvid")
            .remove("last_played_title")
            .remove("last_played_cover")
            .remove("last_played_cid")
            .remove("last_played_progress")
            .remove("last_played_is_live")
            .remove("last_played_room_id")
            .apply()
    }

    /**
     * 是否有有效的上次播放记录
     */
    fun hasLastPlayedVideo(): Boolean {
        return lastPlayedBvid.isNotEmpty()
    }

    // 弹幕设置
    var danmakuEnabled: Boolean
        get() = prefs.getBoolean("danmaku_enabled", true)
        set(value) = prefs.edit().putBoolean("danmaku_enabled", value).apply()

    var danmakuFontSize: Float
        get() = prefs.getFloat("danmaku_font_size", 25f)
        set(value) = prefs.edit().putFloat("danmaku_font_size", value).apply()

    var danmakuOpacity: Float
        get() = prefs.getFloat("danmaku_opacity", 0.8f)
        set(value) = prefs.edit().putFloat("danmaku_opacity", value).apply()

    var danmakuDensity: Float
        get() = prefs.getFloat("danmaku_density", 1.0f)
        set(value) = prefs.edit().putFloat("danmaku_density", value).apply()

    // 插件设置
    var danmakuEnhanceEnabled: Boolean
        get() = prefs.getBoolean("danmaku_enhance_enabled", true)
        set(value) = prefs.edit().putBoolean("danmaku_enhance_enabled", value).apply()

    var adFilterEnabled: Boolean
        get() = prefs.getBoolean("ad_filter_enabled", true)
        set(value) = prefs.edit().putBoolean("ad_filter_enabled", value).apply()

    var sponsorBlockEnabled: Boolean
        get() = prefs.getBoolean("sponsor_block_enabled", false)
        set(value) = prefs.edit().putBoolean("sponsor_block_enabled", value).apply()

    // 其他
    var splashAnimationEnabled: Boolean
        get() = prefs.getBoolean("splash_animation_enabled", true)
        set(value) = prefs.edit().putBoolean("splash_animation_enabled", value).apply()

    // 内部
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
```

### 6. `res/layout/fragment_settings.xml` — 增加自动播放上次视频开关

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#1A1A2E"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="40dp">

        <!-- 顶部栏 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:layout_marginBottom="30dp">

            <ImageButton
                android:id="@+id/backButton"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="返回"
                android:src="@android:drawable/ic_menu_revert" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:text="设置"
                android:textColor="#FFFFFF"
                android:textSize="28sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- 播放设置分区 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="播放设置"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp"
            android:layout_marginBottom="24dp">

            <!-- 自动连播 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="自动连播"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/autoPlaySwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- ↓↓↓ 新增：自动播放上次视频 ↓↓↓ -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="启动时自动播放上次视频"
                        android:textColor="#E0E0E0"
                        android:textSize="18sp" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:text="开启后启动应用将自动进入上次播放的视频"
                        android:textColor="#888888"
                        android:textSize="13sp" />
                </LinearLayout>

                <Switch
                    android:id="@+id/autoPlayLastVideoSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- 画质选择 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="默认画质"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Spinner
                    android:id="@+id/qualitySpinner"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:entries="@array/quality_options" />
            </LinearLayout>
        </LinearLayout>

        <!-- 弹幕设置分区 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="弹幕设置"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp"
            android:layout_marginBottom="24dp">

            <!-- 弹幕开关 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="显示弹幕"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/danmakuSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- 弹幕增强 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="弹幕增强过滤"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/adFilterSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- 赞助跳过 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="赞助内容跳过"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/sponsorBlockSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>
        </LinearLayout>

        <!-- 其他设置 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="其他"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp"
            android:layout_marginBottom="24dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="启动动画"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/splashAnimationSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>
        </LinearLayout>

        <!-- 关于 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="关于"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp">

            <TextView
                android:id="@+id/aboutText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#AAAAAA"
                android:textSize="16sp"
                android:lineSpacingExtra="4dp" />
        </LinearLayout>

    </LinearLayout>
</ScrollView>
```

### 7. `res/values/arrays.xml` — 确保有画质选项数组

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="quality_options">
        <item>360P</item>
        <item>480P</item>
        <item>720P</item>
        <item>1080P</item>
        <item>1080P+</item>
    </string-array>
</resources>
```

### 8. `screens/home/settings/SettingsFragment.kt` — 绑定新开关

```kt
package com.bili.tv.bili_tv_app.screens.home.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bili.tv.bili_tv_app.databinding.FragmentSettingsBinding
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        // 返回按钮
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 加载当前设置状态
        binding.splashAnimationSwitch.isChecked = SettingsService.splashAnimationEnabled
        binding.danmakuSwitch.isChecked = SettingsService.danmakuEnabled
        binding.adFilterSwitch.isChecked = SettingsService.adFilterEnabled
        binding.sponsorBlockSwitch.isChecked = SettingsService.sponsorBlockEnabled
        binding.autoPlaySwitch.isChecked = SettingsService.autoPlay
        // ↓↓↓ 新增 ↓↓↓
        binding.autoPlayLastVideoSwitch.isChecked = SettingsService.autoPlayLastVideo

        // 保存开关状态
        binding.splashAnimationSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.splashAnimationEnabled = isChecked
        }

        binding.danmakuSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.danmakuEnabled = isChecked
        }

        binding.adFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.adFilterEnabled = isChecked
        }

        binding.sponsorBlockSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.sponsorBlockEnabled = isChecked
        }

        binding.autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.autoPlay = isChecked
        }

        // ↓↓↓ 新增：自动播放上次视频开关 ↓↓↓
        binding.autoPlayLastVideoSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.autoPlayLastVideo = isChecked
            if (!isChecked) {
                // 关闭时清除记录
                SettingsService.clearLastPlayedVideo()
                Toast.makeText(requireContext(), "已清除上次播放记录", Toast.LENGTH_SHORT).show()
            }
        }

        // 画质选择
        val qualities = arrayOf("360P", "480P", "720P", "1080P", "1080P+")
        binding.qualitySpinner.setSelection(getCurrentQualityIndex())

        binding.qualitySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val quality = when (position) {
                    0 -> 16   // 360P
                    1 -> 32   // 480P
                    2 -> 64   // 720P
                    3 -> 80   // 1080P
                    4 -> 112  // 1080P+
                    else -> 80
                }
                SettingsService.defaultQuality = quality
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // 关于
        binding.aboutText.text = "BiliTV v1.2.3\n哔哩哔哩电视版客户端\n\n基于Flutter源码转换"
    }

    private fun getCurrentQualityIndex(): Int {
        return when (SettingsService.defaultQuality) {
            16 -> 0
            32 -> 1
            64 -> 2
            80 -> 3
            112 -> 4
            else -> 3
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 9. `models/Episode.kt` — 新增分集模型

```kt
package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class Episode(
    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("part")
    val part: String = "",

    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("dimension")
    val dimension: Video.Dimension? = null,

    @SerializedName("first_frame")
    val firstFrame: String? = null
) {
    fun getFormattedDuration(): String {
        val totalSeconds = (duration / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
```

### 10. `models/VideoDetail.kt` — 新增视频详情模型（含分集信息）

```kt
package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class VideoDetailResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: VideoDetail? = null
)

data class VideoDetail(
    @SerializedName("bvid")
    val bvid: String = "",

    @SerializedName("aid")
    val aid: Long = 0,

    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("pic")
    val pic: String = "",

    @SerializedName("desc")
    val desc: String = "",

    @SerializedName("pubdate")
    val pubdate: Long = 0,

    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("owner")
    val owner: Video.Owner? = null,

    @SerializedName("stat")
    val stat: Video.Stat? = null,

    @SerializedName("dimension")
    val dimension: Video.Dimension? = null,

    @SerializedName("pages")
    val pages: List<Episode> = emptyList(),

    @SerializedName("tid")
    val tid: Int = 0,

    @SerializedName("tname")
    val tname: String = "",

    @SerializedName("videos")
    val videos: Int = 0,

    @SerializedName("season_id")
    val seasonId: Long = 0,

    @SerializedName("season_type")
    val seasonType: Int = 0,

    @SerializedName("ugc_season")
    val ugcSeason: UgcSeason? = null
) {
    /**
     * 是否是多P视频
     */
    fun isMultiPart(): Boolean = pages.size > 1

    /**
     * 获取当前分集在列表中的索引
     */
    fun getPageIndex(cid: Long): Int {
        return pages.indexOfFirst { it.cid == cid }.coerceAtLeast(0)
    }
}

data class UgcSeason(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("sections")
    val sections: List<UgcSeasonSection> = emptyList()
)

data class UgcSeasonSection(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("episodes")
    val episodes: List<UgcSeasonEpisode> = emptyList()
)

data class UgcSeasonEpisode(
    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("bvid")
    val bvid: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("duration")
    val duration: Long = 0
)
```

### 11. `models/LiveRoom.kt` — 新增直播间模型

```kt
package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class LiveRoom(
    @SerializedName("room_id")
    val roomId: Long = 0,

    @SerializedName("uid")
    val uid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("cover")
    val cover: String = "",

    @SerializedName("uname")
    val uname: String = "",

    @SerializedName("face")
    val face: String = "",

    @SerializedName("live_status")
    val liveStatus: Int = 0,  // 0:未开播, 1:直播中, 2:轮播中

    @SerializedName("area_id")
    val areaId: Int = 0,

    @SerializedName("area_name")
    val areaName: String = "",

    @SerializedName("online")
    val online: Int = 0,

    @SerializedName("play_url")
    val playUrl: String? = null
) {
    fun isLive(): Boolean = liveStatus == 1

    fun getFormattedOnline(): String {
        return when {
            online >= 10000 -> String.format("%.1f万", online / 10000.0)
            else -> online.toString()
        }
    }
}

data class LiveRoomListResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: LiveRoomListData? = null
)

data class LiveRoomListData(
    @SerializedName("list")
    val list: List<LiveRoomItem>? = null,

    @SerializedName("count")
    val count: Int = 0
)

data class LiveRoomItem(
    @SerializedName("roomid")
    val roomid: Long = 0,

    @SerializedName("uid")
    val uid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("cover")
    val cover: String = "",

    @SerializedName("uname")
    val uname: String = "",

    @SerializedName("face")
    val face: String = "",

    @SerializedName("live_status")
    val liveStatus: Int = 0,

    @SerializedName("area_id")
    val areaId: Int = 0,

    @SerializedName("area_name")
    val areaName: String = "",

    @SerializedName("online")
    val online: Int = 0
) {
    fun toLiveRoom(): LiveRoom {
        return LiveRoom(
            roomId = roomid,
            uid = uid,
            title = title,
            cover = cover,
            uname = uname,
            face = face,
            liveStatus = liveStatus,
            areaId = areaId,
            areaName = areaName,
            online = online
        )
    }
}

data class LiveStreamResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: LiveStreamData? = null
)

data class LiveStreamData(
    @SerializedName("playurl_info")
    val playurlInfo: LivePlayUrlInfo? = null
)

data class LivePlayUrlInfo(
    @SerializedName("playurl")
    val playurl: LivePlayUrl? = null
)

data class LivePlayUrl(
    @SerializedName("stream")
    val stream: List<LiveStream>? = null
)

data class LiveStream(
    @SerializedName("protocol_name")
    val protocolName: String = "",

    @SerializedName("format")
    val format: List<LiveFormat>? = null
)

data class LiveFormat(
    @SerializedName("format_name")
    val formatName: String = "",

    @SerializedName("codec")
    val codec: List<LiveCodec>? = null
)

data class LiveCodec(
    @SerializedName("codec_name")
    val codecName: String = "",

    @SerializedName("current_qn")
    val currentQn: Int = 0,

    @SerializedName("base_url")
    val baseUrl: String = "",

    @SerializedName("url_info")
    val urlInfo: List<LiveUrlInfo>? = null
)

data class LiveUrlInfo(
    @SerializedName("host")
    val host: String = "",

    @SerializedName("extra")
    val extra: String = "",

    @SerializedName("stream")
    val stream: String? = null
) {
    fun buildFullUrl(baseUrl: String): String {
        return if (stream != null) {
            "$host$baseUrl?$extra$stream"
        } else {
            "$host$baseUrl?$extra"
        }
    }
}
```

### 12. `services/api/BilibiliApiService.kt` — 增加直播和分集API

```kt
package com.bili.tv.bili_tv_app.services.api

import com.bili.tv.bili_tv_app.models.*
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class BilibiliApiService private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://api.bilibili.com"
        private const val LIVE_URL = "https://api.live.bilibili.com"

        @Volatile
        private var instance: BilibiliApiService? = null

        fun getInstance(): BilibiliApiService {
            return instance ?: synchronized(this) {
                instance ?: BilibiliApiService().also { instance = it }
            }
        }
    }

    /**
     * 构建带认证信息的请求
     */
    private fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://www.bilibili.com")

        val cookies = AuthService.cookies
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies)
        }

        return builder
    }

    /**
     * 获取推荐视频列表
     */
    fun getRecommendVideos(page: Int): List<Video> {
        val url = "$BASE_URL/x/web-interface/popular?ps=20&pn=${page + 1}"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val list = data["list"] as? List<*> ?: return emptyList()

            list.mapNotNull { item ->
                try {
                    gson.fromJson(gson.toJson(item), Video::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取视频详情（含分集信息）
     */
    fun getVideoInfo(bvid: String): VideoDetail? {
        val url = "$BASE_URL/x/web-interface/view?bvid=$bvid"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val resp = gson.fromJson(body, VideoDetailResponse::class.java)
            if (resp.code == 0) resp.data else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取视频播放地址
     */
    fun getPlayUrl(aid: Long, cid: Long, quality: Int): String? {
        val url = "$BASE_URL/x/player/playurl?avid=$aid&cid=$cid&qn=$quality&fnval=16&fourk=1"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return null
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return null

            val data = json["data"] as? Map<*, *> ?: return null

            // 优先使用 DASH 格式
            val dash = data["dash"] as? Map<*, *>
            if (dash != null) {
                val videoList = dash["video"] as? List<*> ?: return null
                // 找到匹配或最高的画质
                val targetVideo = videoList.mapNotNull { v ->
                    try {
                        gson.fromJson(gson.toJson(v), VideoTrack::class.java)
                    } catch (e: Exception) { null }
                }.maxByOrNull { it.bandwidth }

                if (targetVideo != null && targetVideo.baseUrl.isNotEmpty()) {
                    return targetVideo.baseUrl
                }
            }

            // 降级使用 DURL 格式
            val durl = data["durl"] as? List<*>
            if (durl != null && durl.isNotEmpty()) {
                val first = durl[0] as? Map<*, *>
                return first?.get("url") as? String
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 搜索视频
     */
    fun searchVideos(keyword: String): List<Video> {
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val url = "$BASE_URL/x/web-interface/search/type?search_type=video&keyword=$encodedKeyword&page=1"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val result = data["result"] as? List<*> ?: return emptyList()

            result.mapNotNull { item ->
                try {
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    Video(
                        bvid = map["bvid"] as? String ?: "",
                        aid = ((map["aid"] as? Double)?.toLong() ?: 0L),
                        title = (map["title"] as? String)?.replace(Regex("<[^>]+>"), "") ?: "",
                        pic = map["pic"] as? String ?: "",
                        author = map["author"] as? String ?: "",
                        duration = map["duration"] as? String ?: "",
                        view = ((map["play"] as? Double)?.toInt() ?: 0),
                        danmaku = ((map["video_review"] as? Double)?.toInt() ?: 0),
                        owner = Video.Owner(
                            mid = ((map["mid"] as? Double)?.toLong() ?: 0L),
                            name = map["author"] as? String ?: ""
                        )
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ==================== 直播相关 API ====================

    /**
     * 获取关注的主播直播间列表（左右键数据源）
     */
    fun getFollowLiveRooms(page: Int = 1): List<LiveRoom> {
        if (!AuthService.isLoggedIn) return emptyList()

        val url = "$LIVE_URL/xlive/web-ucenter/v1/xfetter/room_list?page=$page&page_size=20"
        val request = buildRequest(url)
            .header("Referer", "https://live.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val list = data["list"] as? List<*> ?: return emptyList()

            list.mapNotNull { item ->
                try {
                    val room = gson.fromJson(gson.toJson(item), LiveRoomItem::class.java)
                    room.toLiveRoom()
                } catch (e: Exception) { null }
            }.filter { it.isLive() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取推荐直播间列表（上下键数据源）
     */
    fun getRecommendLiveRooms(page: Int = 1): List<LiveRoom> {
        val url = "$LIVE_URL/xlive/web-interface/v1/second/getList?platform=web&sort_type=online&page=$page&page_size=20"
        val request = buildRequest(url)
            .header("Referer", "https://live.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val list = data["list"] as? List<*> ?: return emptyList()

            list.mapNotNull { item ->
                try {
                    val room = gson.fromJson(gson.toJson(item), LiveRoomItem::class.java)
                    room.toLiveRoom()
                } catch (e: Exception) { null }
            }.filter { it.isLive() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取直播间播放地址
     */
    fun getLiveStreamUrl(roomId: Long): String? {
        val url = "$LIVE_URL/xlive/web-room/v1/index/getRoomPlayInfo?room_id=$roomId&protocol=0,1&format=0,1,2&codec=0,1&qn=10000&platform=web"
        val request = buildRequest(url)
            .header("Referer", "https://live.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val resp = gson.fromJson(body, LiveStreamResponse::class.java)

            if (resp.code != 0 || resp.data?.playurlInfo?.playurl == null) return null

            val streams = resp.data.playurlInfo.playurl.stream
            if (streams.isNullOrEmpty()) return null

            // 优先找 http-stream (FLV)，其次找 http-hls (HLS)
            for (protocol in streams) {
                if (protocol.formatName == "http_stream" || protocol.formatName == "http_hls") {
                    val codecs = protocol.format?.firstOrNull()?.codec
                    if (!codecs.isNullOrEmpty()) {
                        val codec = codecs.maxByOrNull { it.currentQn }
                        if (codec != null && codec.baseUrl.isNotEmpty()) {
                            val urlInfo = codec.urlInfo?.firstOrNull()
                            if (urlInfo != null) {
                                return urlInfo.buildFullUrl(codec.baseUrl)
                            }
                            return codec.baseUrl
                        }
                    }
                }
            }

            // 最终降级：返回第一个可用的流
            for (stream in streams) {
                for (format in stream.format ?: emptyList()) {
                    for (codec in format.codec ?: emptyList()) {
                        if (codec.baseUrl.isNotEmpty()) {
                            val urlInfo = codec.urlInfo?.firstOrNull()
                            if (urlInfo != null) {
                                return urlInfo.buildFullUrl(codec.baseUrl)
                            }
                            return codec.baseUrl
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
```

### 13. `screens/home/HomeFragment.kt` — 启动时自动播放上次视频

```kt
package com.bili.tv.bili_tv_app.screens.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.databinding.FragmentHomeBinding
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.screens.home.settings.SettingsFragment
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService
import com.bili.tv.bili_tv_app.widgets.VideoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private val videoList = mutableListOf<Video>()

    // 标记是否已经处理过自动播放（防止从播放器返回时再次触发）
    private var autoPlayHandled = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated called")
        setupUI()
        loadContent()
    }

    private fun setupUI() {
        // 视频网格
        videoAdapter = VideoAdapter(videoList) { video ->
            navigateToPlayer(video)
        }

        binding.videosRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = videoAdapter
        }

        // 搜索按钮
        binding.searchButton.setOnClickListener {
            navigateToSearch()
        }

        // 设置按钮
        binding.settingsButton.setOnClickListener {
            navigateToSettings()
        }

        // 用户按钮
        binding.userButton.setOnClickListener {
            if (AuthService.isLoggedIn) {
                showUserInfo()
            } else {
                navigateToLogin()
            }
        }

        // 下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            loadContent()
        }
    }

    private fun loadContent() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true

            val videos = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getRecommendVideos(0)
            }

            videoList.clear()
            videoList.addAll(videos)
            videoAdapter.notifyDataSetChanged()

            binding.swipeRefresh.isRefreshing = false

            if (videoList.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.videosRecyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.videosRecyclerView.visibility = View.VISIBLE
            }

            // ↓↓↓ 自动播放上次视频逻辑 ↓↓↓
            checkAndAutoPlayLastVideo()
        }
    }

    /**
     * 检查并自动播放上次观看的视频
     */
    private fun checkAndAutoPlayLastVideo() {
        if (autoPlayHandled) return
        if (!SettingsService.autoPlayLastVideo) return
        if (!SettingsService.hasLastPlayedVideo()) return
        // 如果是直播，暂不自动播放（直播可能已结束）
        if (SettingsService.lastPlayedIsLive) return

        autoPlayHandled = true

        lifecycleScope.launch {
            delay(500) // 稍微延迟，让首页先渲染出来

            val lastBvid = SettingsService.lastPlayedBvid
            val lastTitle = SettingsService.lastPlayedTitle
            val lastCover = SettingsService.lastPlayedCover

            Log.d("HomeFragment", "自动播放上次视频: $lastBvid - $lastTitle")

            // 显示提示
            Toast.makeText(
                requireContext(),
                "继续播放: $lastTitle",
                Toast.LENGTH_SHORT
            ).show()

            navigateToPlayerWithList(
                Video(
                    bvid = lastBvid,
                    title = lastTitle,
                    pic = lastCover,
                    cid = SettingsService.lastPlayedCid
                ),
                videoList.toList(),
                videoList.indexOfFirst { it.bvid == lastBvid }.coerceAtLeast(0)
            )
        }
    }

    private fun navigateToPlayer(video: Video) {
        navigateToPlayerWithList(video, videoList.toList(), videoList.indexOf(video).coerceAtLeast(0))
    }

    private fun navigateToPlayerWithList(video: Video, list: List<Video>, index: Int) {
        val fragment = com.bili.tv.bili_tv_app.screens.player.PlayerFragment.newInstance(
            bvid = video.bvid,
            title = video.title,
            coverUrl = video.pic,
            isLive = false,
            categoryVideoList = list,
            categoryVideoIndex = index
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToSearch() {
        val fragment = com.bili.tv.bili_tv_app.screens.home.search.SearchFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToSettings() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToLogin() {
        val fragment = com.bili.tv.bili_tv_app.screens.home.login.LoginFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showUserInfo() {
        AuthService.currentUser?.let { user ->
            Toast.makeText(
                requireContext(),
                "欢迎, ${user.uname} (Lv.${user.level})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 14. `res/layout/fragment_player.xml` — 增加切换提示覆盖层

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/playerRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- 播放器 -->
    <androidx.media3.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 弹幕层 -->
    <com.bili.tv.bili_tv_app.widgets.DanmakuView
        android:id="@+id/danmakuView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clickable="false"
        android:focusable="false" />

    <!-- 加载进度 -->
    <ProgressBar
        android:id="@+id/loadingProgress"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_gravity="center"
        android:visibility="gone" />

    <!-- 顶部信息栏 -->
    <LinearLayout
        android:id="@+id/topBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/gradient_top"
        android:orientation="vertical"
        android:padding="16dp"
        android:visibility="visible">

        <!-- 返回按钮 + 标题 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/backButton"
                android:layout_width="44dp"
                android:layout_height="44dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="返回"
                android:src="@android:drawable/ic_menu_revert" />

            <TextView
                android:id="@+id/videoTitle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:layout_weight="1"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="#FFFFFF"
                android:textSize="20sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- 分集/模式指示器 -->
        <LinearLayout
            android:id="@+id/episodeIndicator"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="56dp"
            android:layout_marginTop="4dp"
            android:orientation="horizontal"
            android:visibility="gone">

            <TextView
                android:id="@+id/modeLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="#33FB7299"
                android:paddingHorizontal="8dp"
                android:paddingVertical="2dp"
                android:text="点播"
                android:textColor="#FFFFFF"
                android:textSize="12sp" />

            <TextView
                android:id="@+id/episodeLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:textColor="#AAAAAA"
                android:textSize="14sp" />
        </LinearLayout>
    </LinearLayout>

    <!-- 切换提示浮层 -->
    <LinearLayout
        android:id="@+id/switchOverlay"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="#CC000000"
        android:gravity="center"
        android:orientation="vertical"
        android:paddingHorizontal="32dp"
        android:paddingVertical="20dp"
        android:visibility="gone">

        <ImageView
            android:id="@+id/switchIcon"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:contentDescription="切换方向"
            android:src="@android:drawable/ic_media_previous" />

        <TextView
            android:id="@+id/switchTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:ellipsize="end"
            android:maxLines="2"
            android:maxWidth="280dp"
            android:textColor="#FFFFFF"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/switchSubtitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="#AAAAAA"
            android:textSize="13sp" />
    </LinearLayout>

    <!-- 底部提示 -->
    <TextView
        android:id="@+id/hintText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="24dp"
        android:text="← → 切换分集  ↑ ↓ 切换视频"
        android:textColor="#66FFFFFF"
        android:textSize="13sp"
        android:visibility="gone" />

</FrameLayout>
```

### 15. `screens/player/PlayerFragment.kt` — 完整重写，支持方向键切换

```kt
package com.bili.tv.bili_tv_app.screens.player

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.util.UnstableApi
import com.bili.tv.bili_tv_app.databinding.FragmentPlayerBinding
import com.bili.tv.bili_tv_app.models.Episode
import com.bili.tv.bili_tv_app.models.LiveRoom
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.models.VideoDetail
import com.bili.tv.bili_tv_app.services.SettingsService
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.widgets.DanmakuView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var danmakuView: DanmakuView? = null

    // 当前播放信息
    private var bvid: String = ""
    private var title: String = ""
    private var coverUrl: String = ""
    private var currentAid: Long = 0
    private var currentCid: Long = 0

    // 模式：直播 or 点播
    private var isLiveMode: Boolean = false
    private var liveRoomId: Long = 0

    // 点播模式 - 分集数据
    private var videoDetail: VideoDetail? = null
    private var episodeList: List<Episode> = emptyList()
    private var currentEpisodeIndex: Int = 0

    // 点播模式 - 分类列表（上下键）
    private var categoryVideoList: List<Video> = emptyList()
    private var categoryVideoIndex: Int = 0

    // 直播模式 - 数据源
    private var followLiveList: List<LiveRoom> = emptyList()
    private var followLiveIndex: Int = 0
    private var recommendLiveList: List<LiveRoom> = emptyList()
    private var recommendLiveIndex: Int = 0

    // 切换提示
    private val handler = Handler(Looper.getMainLooper())
    private var hideSwitchOverlayRunnable: Runnable? = null
    private var hideHintRunnable: Runnable? = null

    // 上次按键时间（防抖）
    private var lastKeyTime: Long = 0
    private val keyDebounceMs = 600L

    // 加载协程
    private var loadJob: Job? = null

    companion object {
        private const val ARG_BVID = "bvid"
        private const val ARG_TITLE = "title"
        private const val ARG_COVER = "cover"
        private const val ARG_IS_LIVE = "is_live"
        private const val ARG_LIVE_ROOM_ID = "live_room_id"
        private const val ARG_CATEGORY_VIDEOS = "category_videos"
        private const val ARG_CATEGORY_INDEX = "category_index"

        fun newInstance(
            bvid: String,
            title: String,
            coverUrl: String,
            isLive: Boolean = false,
            liveRoomId: Long = 0L,
            categoryVideoList: List<Video> = emptyList(),
            categoryVideoIndex: Int = 0
        ): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BVID, bvid)
                    putString(ARG_TITLE, title)
                    putString(ARG_COVER, coverUrl)
                    putBoolean(ARG_IS_LIVE, isLive)
                    putLong(ARG_LIVE_ROOM_ID, liveRoomId)
                    putStringArrayList(ARG_CATEGORY_VIDEOS, ArrayList(categoryVideoList.map {
                        "${it.bvid}|${it.title}|${it.pic}|${it.cid}"
                    }))
                    putInt(ARG_CATEGORY_INDEX, categoryVideoIndex)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            bvid = it.getString(ARG_BVID, "")
            title = it.getString(ARG_TITLE, "")
            coverUrl = it.getString(ARG_COVER, "")
            isLiveMode = it.getBoolean(ARG_IS_LIVE, false)
            liveRoomId = it.getLong(ARG_LIVE_ROOM_ID, 0L)

            // 解析分类视频列表
            val videoStrings = it.getStringArrayList(ARG_CATEGORY_VIDEOS)
            if (videoStrings != null) {
                categoryVideoList = videoStrings.mapNotNull { str ->
                    val parts = str.split("|")
                    if (parts.size >= 3) {
                        Video(
                            bvid = parts[0],
                            title = parts[1],
                            pic = parts[2],
                            cid = if (parts.size > 3) parts[3].toLongOrNull() ?: 0L else 0L
                        )
                    } else null
                }
            }
            categoryVideoIndex = it.getInt(ARG_CATEGORY_INDEX, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPlayer()
        setupKeyHandler()
        setupUI()

        if (isLiveMode) {
            loadLiveRoom()
        } else {
            loadVideo()
        }
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            savePlaybackProgress()
            parentFragmentManager.popBackStack()
        }

        binding.videoTitle.text = title

        // 根据模式更新UI
        updateModeUI()
    }

    private fun updateModeUI() {
        if (isLiveMode) {
            binding.modeLabel.text = "直播"
            binding.episodeIndicator.visibility = View.VISIBLE
            binding.episodeLabel.text = "房间号: $liveRoomId"
            binding.hintText.text = "← → 切换关注直播间  ↑ ↓ 切换推荐直播间"
            binding.hintText.visibility = View.VISIBLE
            hideHintRunnable?.let { handler.removeCallbacks(it) }
            hideHintRunnable = Runnable { binding.hintText.visibility = View.GONE }
            handler.postDelayed(hideHintRunnable!!, 5000)
        } else {
            binding.modeLabel.text = "点播"
            binding.episodeIndicator.visibility = View.GONE
            binding.hintText.text = "← → 切换分集  ↑ ↓ 切换视频"
            binding.hintText.visibility = View.VISIBLE
            hideHintRunnable?.let { handler.removeCallbacks(it) }
            hideHintRunnable = Runnable { binding.hintText.visibility = View.GONE }
            handler.postDelayed(hideHintRunnable!!, 5000)
        }
    }

    private fun setupPlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://www.bilibili.com",
                "Origin" to "https://www.bilibili.com"
            ))

        val dataSourceFactory = DefaultDataSource.Factory(requireContext(), httpDataSourceFactory)

        player = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory))
            .build().also {
                binding.playerView.player = it

                it.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                binding.loadingProgress.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                binding.loadingProgress.visibility = View.GONE
                                it.volume = 1.0f
                                it.playWhenReady = true
                                // 恢复进度
                                if (!isLiveMode) {
                                    val savedProgress = SettingsService.lastPlayedProgress
                                    if (savedProgress > 0 && savedProgress < (it.duration * 0.95)) {
                                        it.seekTo(savedProgress)
                                    }
                                }
                            }
                            Player.STATE_ENDED -> {
                                binding.loadingProgress.visibility = View.GONE
                                if (!isLiveMode) {
                                    playNextEpisodeOrVideo()
                                }
                            }
                            Player.STATE_IDLE -> {
                                binding.loadingProgress.visibility = View.GONE
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("PlayerFragment", "播放错误: ${error.message}", error)
                        binding.loadingProgress.visibility = View.GONE
                        Toast.makeText(requireContext(), "播放错误: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }

        if (SettingsService.danmakuEnabled) {
            setupDanmaku()
        }
    }

    private fun setupDanmaku() {
        danmakuView = binding.danmakuView
        danmakuView?.apply {
            setTextSize(SettingsService.danmakuFontSize)
            setAlpha(SettingsService.danmakuOpacity)
            setSpeed(SettingsService.danmakuDensity)
        }
    }

    // ==================== 方向键处理 ====================

    private fun setupKeyHandler() {
        // 在 PlayerView 上设置按键监听，拦截默认的快进快退行为
        binding.playerView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleDpadKey(keyCode)
            } else false
        }

        // 同时在根布局上设置，作为备用
        binding.playerRoot.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleDpadKey(keyCode)
            } else false
        }

        binding.playerRoot.isFocusableInTouchMode = true
        binding.playerRoot.requestFocus()
    }

    private fun handleDpadKey(keyCode: Int): Boolean {
        // 防抖：短时间内不重复触发
        val now = System.currentTimeMillis()
        if (now - lastKeyTime < keyDebounceMs) return false
        lastKeyTime = now

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isLiveMode) switchFollowLiveRoom(-1) else switchEpisode(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isLiveMode) switchFollowLiveRoom(1) else switchEpisode(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isLiveMode) switchRecommendLiveRoom(-1) else switchCategoryVideo(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isLiveMode) switchRecommendLiveRoom(1) else switchCategoryVideo(1)
                return true
            }
        }
        return false
    }

    // ==================== 点播模式：切换逻辑 ====================

    /**
     * 切换分集（左右键）
     * @param direction -1=上一集, +1=下一集
     */
    private fun switchEpisode(direction: Int) {
        if (episodeList.size <= 1) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
                title = "没有更多分集",
                subtitle = ""
            )
            return
        }

        val newIndex = currentEpisodeIndex + direction
        if (newIndex < 0 || newIndex >= episodeList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
                title = "已是${if (direction < 0) "第一" else "最后一"}集",
                subtitle = ""
            )
            return
        }

        currentEpisodeIndex = newIndex
        val episode = episodeList[newIndex]
        currentCid = episode.cid

        Log.d("PlayerFragment", "切换分集: P${episode.page} - ${episode.part}")

        updateEpisodeUI()
        showSwitchOverlay(
            iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
            title = "P${episode.page} ${episode.part}",
            subtitle = "${newIndex + 1}/${episodeList.size}"
        )

        // 加载新分集
        loadEpisodePlayUrl(episode.cid)
    }

    /**
     * 切换分类视频（上下键）
     * @param direction -1=上一个视频, +1=下一个视频
     */
    private fun switchCategoryVideo(direction: Int) {
        if (categoryVideoList.isEmpty()) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
                title = "没有更多视频",
                subtitle = ""
            )
            return
        }

        val newIndex = categoryVideoIndex + direction
        if (newIndex < 0 || newIndex >= categoryVideoList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
                title = "已是${if (direction < 0) "第一" else "最后"}个视频",
                subtitle = ""
            )
            return
        }

        categoryVideoIndex = newIndex
        val video = categoryVideoList[newIndex]

        Log.d("PlayerFragment", "切换分类视频: ${video.bvid} - ${video.title}")

        showSwitchOverlay(
            iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
            title = video.title,
            subtitle = "${newIndex + 1}/${categoryVideoList.size}"
        )

        // 切换到新视频（重置分集状态）
        bvid = video.bvid
        title = video.title
        coverUrl = video.pic
        currentCid = video.cid
        currentEpisodeIndex = 0
        episodeList = emptyList()
        videoDetail = null

        binding.videoTitle.text = title
        loadVideo()
    }

    /**
     * 播放下一个分集或下一个视频
     */
    private fun playNextEpisodeOrVideo() {
        // 优先尝试下一集
        if (episodeList.size > 1 && currentEpisodeIndex < episodeList.size - 1) {
            switchEpisode(1)
            return
        }
        // 其次尝试下一个视频
        if (categoryVideoList.isNotEmpty() && categoryVideoIndex < categoryVideoList.size - 1) {
            switchCategoryVideo(1)
        }
    }

    // ==================== 直播模式：切换逻辑 ====================

    /**
     * 切换关注列表直播间（左右键）
     */
    private fun switchFollowLiveRoom(direction: Int) {
        if (followLiveList.isEmpty()) {
            // 如果还没有加载过，先加载
            loadFollowLiveRooms()
            return
        }

        val newIndex = followLiveIndex + direction
        if (newIndex < 0 || newIndex >= followLiveList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
                title = "已是关注列表${if (direction < 0) "第一个" else "最后一个"}直播间",
                subtitle = "(关注列表)"
            )
            return
        }

        followLiveIndex = newIndex
        val room = followLiveList[newIndex]
        switchToLiveRoom(room, "关注列表")
    }

    /**
     * 切换推荐直播间（上下键）
     */
    private fun switchRecommendLiveRoom(direction: Int) {
        if (recommendLiveList.isEmpty()) {
            // 如果还没有加载过，先加载
            loadRecommendLiveRooms()
            return
        }

        val newIndex = recommendLiveIndex + direction
        if (newIndex < 0 || newIndex >= recommendLiveList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
                title = "已是推荐列表${if (direction < 0) "第一个" else "最后一个"}直播间",
                subtitle = "(推荐列表)"
            )
            return
        }

        recommendLiveIndex = newIndex
        val room = recommendLiveList[newIndex]
        switchToLiveRoom(room, "推荐列表")
    }

    /**
     * 切换到指定直播间
     */
    private fun switchToLiveRoom(room: LiveRoom, source: String) {
        liveRoomId = room.roomId
        title = room.title
        bvid = "" // 直播没有bvid

        binding.videoTitle.text = title
        binding.episodeLabel.text = "${room.uname} - 房间号: ${room.roomId}"

        showSwitchOverlay(
            iconRes = android.R.drawable.ic_media_play,
            title = room.title,
            subtitle = "$source · ${room.uname} · ${room.getFormattedOnline()}人观看"
        )

        Log.d("PlayerFragment", "切换直播间: ${room.roomId} - ${room.title}")
        loadLiveStream(room.roomId)
    }

    private fun loadFollowLiveRooms() {
        lifecycleScope.launch {
            showSwitchOverlay(
                iconRes = android.R.drawable.ic_menu_rotate,
                title = "正在加载关注列表...",
                subtitle = ""
            )
            val rooms = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getFollowLiveRooms()
            }
            if (rooms.isNotEmpty()) {
                followLiveList = rooms
                followLiveIndex = 0
                hideSwitchOverlay()
                switchFollowLiveRoom(1)
            } else {
                showSwitchOverlay(
                    iconRes = android.R.drawable.ic_dialog_alert,
                    title = "关注列表为空或未登录",
                    subtitle = "请先在B站APP关注一些主播"
                )
            }
        }
    }

    private fun loadRecommendLiveRooms() {
        lifecycleScope.launch {
            showSwitchOverlay(
                iconRes = android.R.drawable.ic_menu_rotate,
                title = "正在加载推荐列表...",
                subtitle = ""
            )
            val rooms = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getRecommendLiveRooms()
            }
            if (rooms.isNotEmpty()) {
                recommendLiveList = rooms
                recommendLiveIndex = 0
                hideSwitchOverlay()
                switchRecommendLiveRoom(1)
            } else {
                showSwitchOverlay(
                    iconRes = android.R.drawable.ic_dialog_alert,
                    title = "推荐列表加载失败",
                    subtitle = "请稍后重试"
                )
            }
        }
    }

    // ==================== 加载逻辑 ====================

    private fun loadVideo() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                Log.d("PlayerFragment", "加载视频: $bvid")

                val info = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getVideoInfo(bvid)
                }

                if (info == null) {
                    Toast.makeText(requireContext(), "获取视频信息失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                videoDetail = info
                currentAid = info.aid

                // 如果没有指定cid，使用视频默认cid
                if (currentCid == 0L) {
                    currentCid = info.cid
                }

                // 设置分集列表
                episodeList = info.pages
                currentEpisodeIndex = info.getPageIndex(currentCid)

                binding.videoTitle.text = info.title
                title = info.title

                updateEpisodeUI()

                // 获取播放地址
                val videoUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getPlayUrl(
                        info.aid,
                        currentCid,
                        SettingsService.defaultQuality
                    )
                }

                if (videoUrl == null) {
                    Toast.makeText(requireContext(), "获取播放地址失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                playUrl(videoUrl)

                // 保存播放记录
                savePlaybackRecord()

            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载视频异常", e)
                Toast.makeText(requireContext(), "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadEpisodePlayUrl(cid: Long) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                val videoUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getPlayUrl(
                        currentAid,
                        cid,
                        SettingsService.defaultQuality
                    )
                }

                if (videoUrl == null) {
                    Toast.makeText(requireContext(), "获取分集播放地址失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                playUrl(videoUrl)
                savePlaybackRecord()

            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载分集异常", e)
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadLiveRoom() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                Log.d("PlayerFragment", "加载直播间: $liveRoomId")

                // 预加载直播列表
                launch { loadLiveListsInBackground() }

                loadLiveStream(liveRoomId)
            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载直播异常", e)
                Toast.makeText(requireContext(), "加载直播失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadLiveStream(roomId: Long) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                val streamUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getLiveStreamUrl(roomId)
                }

                if (streamUrl == null) {
                    Toast.makeText(requireContext(), "直播间未开播或获取流失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                Log.d("PlayerFragment", "直播流地址: ${streamUrl.take(80)}...")
                playUrl(streamUrl)

                // 保存播放记录
                SettingsService.saveLastPlayedVideo(
                    bvid = "",
                    title = title,
                    cover = "",
                    cid = 0,
                    progress = 0,
                    isLive = true,
                    roomId = roomId
                )

            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载直播流异常", e)
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    /**
     * 后台预加载直播列表
     */
    private suspend fun loadLiveListsInBackground() {
        withContext(Dispatchers.IO) {
            try {
                val follow = BilibiliApiService.getInstance().getFollowLiveRooms()
                if (follow.isNotEmpty()) {
                    followLiveList = follow
                    followLiveIndex = follow.indexOfFirst { it.roomId == liveRoomId }.coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Log.w("PlayerFragment", "预加载关注列表失败", e)
            }

            try {
                val recommend = BilibiliApiService.getInstance().getRecommendLiveRooms()
                if (recommend.isNotEmpty()) {
                    recommendLiveList = recommend
                    recommendLiveIndex = recommend.indexOfFirst { it.roomId == liveRoomId }.coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Log.w("PlayerFragment", "预加载推荐列表失败", e)
            }
        }
    }

    private fun playUrl(url: String) {
        player?.let {
            it.stop()
            val mediaItem = MediaItem.fromUri(url)
            it.setMediaItem(mediaItem)
            it.prepare()
            it.playWhenReady = true
        }
    }

    // ==================== UI 更新 ====================

    private fun updateEpisodeUI() {
        if (episodeList.size > 1) {
            val episode = episodeList.getOrNull(currentEpisodeIndex)
            binding.episodeIndicator.visibility = View.VISIBLE
            binding.episodeLabel.text = if (episode != null) {
                "P${episode.page} ${episode.part}  (${currentEpisodeIndex + 1}/${episodeList.size})"
            } else {
                "${currentEpisodeIndex + 1}/${episodeList.size}"
            }
        } else {
            binding.episodeIndicator.visibility = if (isLiveMode) View.VISIBLE else View.GONE
        }
    }

    private fun showSwitchOverlay(iconRes: Int, titleText: String, subtitle: String) {
        binding.switchOverlay.visibility = View.VISIBLE
        binding.switchIcon.setImageResource(iconRes)
        binding.switchTitle.text = titleText
        binding.switchSubtitle.text = subtitle

        hideSwitchOverlayRunnable?.let { handler.removeCallbacks(it) }
        hideSwitchOverlayRunnable = Runnable {
            binding.switchOverlay.visibility = View.GONE
        }
        handler.postDelayed(hideSwitchOverlayRunnable!!, 2000)
    }

    private fun hideSwitchOverlay() {
        binding.switchOverlay.visibility = View.GONE
        hideSwitchOverlayRunnable?.let { handler.removeCallbacks(it) }
    }

    // ==================== 播放记录 ====================

    private fun savePlaybackRecord() {
        if (isLiveMode) return

        SettingsService.saveLastPlayedVideo(
            bvid = bvid,
            title = title,
            cover = coverUrl,
            cid = currentCid,
            progress = 0 // 进入时进度为0，退出时在 savePlaybackProgress 保存
        )
    }

    private fun savePlaybackProgress() {
        if (isLiveMode) return
        val currentPosition = player?.currentPosition ?: 0L
        val duration = player?.duration ?: 0L

        if (currentPosition > 0 && duration > 0) {
            SettingsService.lastPlayedProgress = currentPosition
        }
    }

    // ==================== 生命周期 ====================

    override fun onPause() {
        super.onPause()
        savePlaybackProgress()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        savePlaybackProgress()
        loadJob?.cancel()
        hideSwitchOverlayRunnable?.let { handler.removeCallbacks(it) }
        hideHintRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        danmakuView = null
        _binding = null
    }
}
```

---

## 修改总结

| 问题 | 根因 | 修复方式 |
|------|------|----------|
| **二维码登录失败** | `TokenInfo` 模型中 `mid` 放在了嵌套的 `TokenInfoDetail` 里，但B站API直接在 `token_info` 下返回 `mid`，导致 `tokenInfo.tokenInfo?.mid` 永远为 `null`，用户信息获取失败，登录状态未持久化 | 1. 在 `TokenInfo` 第一层增加 `mid` 字段；2. 重写 `LoginFragment.onLoginSuccess`，**优先通过 Cookie 调用 `/x/web-interface/nav` 获取用户信息**（最可靠），降级方案才用 `tokenInfo.mid`，最终降级从 Cookie 提取 `DedeUserID`；3. 重写 `AuthService` 基于 Cookie 持久化登录状态 |
| **自动播放上次视频** | 无此功能 | 1. `SettingsService` 增加 `autoPlayLastVideo` 开关及 6 个持久化字段；2. `fragment_settings.xml` 增加带说明文字的 Switch；3. `SettingsFragment` 绑定开关（关闭时清除记录）；4. `HomeFragment.loadContent` 完成后检查设置，满足条件自动跳转播放器 |
| **方向键切换逻辑** | 无此功能 | 1. 在 `PlayerView` 上设置 `setOnKeyListener` 拦截 D-pad 四键（返回 `true` 阻止 ExoPlayer 默认快进快退）；2. **点播模式**：左右键切换 `pages` 分集列表，上下键切换 `categoryVideoList` 分类视频列表；3. **直播模式**：左右键切换关注主播列表（`/xlive/web-ucenter/v1/xfetter/room_list`），上下键切换推荐直播间列表（`/xlive/web-interface/v1/second/getList`）；4. 新增 `Episode`/`LiveRoom`/`VideoDetail` 模型；5. `BilibiliApiService` 增加三个直播 API；6. 600ms 防抖 + 居中浮层切换提示 |## 问题分析与修复方案

### Bug 1：二维码登录失败根因
`LoginStatusData.tokenInfo` 模型中 `mid` 字段定义在嵌套的 `TokenInfoDetail` 里，但B站API实际返回的 `token_info.mid` 是**直接在第一层**的，导致 `tokenInfo.tokenInfo?.mid` 永远是 `null`，用户信息获取失败，登录状态未持久化。

### Bug 2：自动播放上次视频
需要在 `SettingsService` 增加持久化字段，`SettingsFragment` 增加开关，`HomeFragment` 启动时判断并跳转。

### Bug 3：方向键切换逻辑
需要在 `PlayerFragment` 拦截 D-pad 按键，根据直播/点播模式分发到不同的数据源。

---

## 修改文件清单

### 1. `models/Login.kt` — 修复 TokenInfo 模型，增加 mid 字段

```kt
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
    val mid: Long = 0,          // ← 修复：B站API直接在token_info下返回mid

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
```

### 2. `services/AuthService.kt` — 完整重写，基于 Cookie 持久化登录

```kt
package com.bili.tv.bili_tv_app.services

import android.content.Context
import android.content.SharedPreferences
import com.bili.tv.bili_tv_app.models.User
import com.google.gson.Gson

object AuthService {

    private const val PREFS_NAME = "bili_auth"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_IN = "expires_in"
    private const val KEY_USER_JSON = "user_json"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    var isLoggedIn: Boolean = false
        private set
    var currentUser: User? = null
        private set
    var cookies: String = ""
        private set
    var accessToken: String = ""
        private set

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        restoreLoginState()
    }

    private fun restoreLoginState() {
        isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            cookies = prefs.getString(KEY_COOKIES, "") ?: ""
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
            val userJson = prefs.getString(KEY_USER_JSON, null)
            if (userJson != null) {
                try {
                    currentUser = gson.fromJson(userJson, User::class.java)
                } catch (e: Exception) {
                    // 数据损坏，清除登录状态
                    clearLogin()
                }
            }
            // 校验：如果没有 cookies 或用户信息，视为未登录
            if (cookies.isEmpty() || currentUser == null) {
                clearLogin()
            }
        }
    }

    /**
     * 保存登录信息 — 以 Cookie 为主，token 为辅
     */
    fun saveLoginInfo(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
        cookies: String,
        user: User
    ) {
        this.accessToken = accessToken
        this.cookies = cookies
        this.currentUser = user
        this.isLoggedIn = true

        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_COOKIES, cookies)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_IN, expiresIn)
            .putString(KEY_USER_JSON, gson.toJson(user))
            .apply()
    }

    /**
     * 从 Cookie 字符串中提取指定字段值
     */
    fun getCookieValue(name: String): String {
        val pattern = "$name="
        val start = cookies.indexOf(pattern)
        if (start == -1) return ""
        val valueStart = start + pattern.length
        val end = cookies.indexOf(';', valueStart)
        return if (end == -1) cookies.substring(valueStart) else cookies.substring(valueStart, end)
    }

    /**
     * 获取 DedeUserID（B站用户ID，存在于Cookie中）
     */
    fun getDedeUserId(): Long {
        val uid = getCookieValue("DedeUserID").trim()
        return uid.toLongOrNull() ?: 0L
    }

    fun clearLogin() {
        isLoggedIn = false
        currentUser = null
        cookies = ""
        accessToken = ""
        prefs.edit().clear().apply()
    }
}
```

### 3. `services/api/AuthApi.kt` — 完整实现，支持 Cookie 方式获取用户信息

```kt
package com.bili.tv.bili_tv_app.services.api

import com.bili.tv.bili_tv_app.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AuthApi private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://passport.bilibili.com"
        private const val API_URL = "https://api.bilibili.com"

        @Volatile
        private var instance: AuthApi? = null

        fun getInstance(): AuthApi {
            return instance ?: synchronized(this) {
                instance ?: AuthApi().also { instance = it }
            }
        }
    }

    /**
     * 获取二维码登录链接
     */
    fun getQRCode(): LoginQRCodeResponse {
        val url = "$BASE_URL/x/passport-login/web/qrcode/generate"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return LoginQRCodeResponse(code = -1, message = "网络错误")

        return try {
            val resp = gson.fromJson(body, LoginQRCodeResponse::class.java)
            if (resp.code == 0 && resp.data != null) {
                resp
            } else {
                LoginQRCodeResponse(code = resp.code, message = resp.message)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LoginQRCodeResponse(code = -1, message = "解析错误: ${e.message}")
        }
    }

    /**
     * 轮询二维码扫描状态
     * 返回 null 表示请求失败（应继续轮询）
     */
    fun checkQRCodeStatus(qrcodeKey: String): LoginStatusResponse? {
        val url = "$BASE_URL/x/passport-login/web/qrcode/poll?qrcode_key=${URLEncoder.encode(qrcodeKey, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, LoginStatusResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 通过 Cookie 获取当前登录用户信息（推荐方式）
     */
    fun getUserInfoByCookie(cookies: String): UserInfoResponse? {
        val url = "$API_URL/x/web-interface/nav"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://www.bilibili.com")
            .header("Cookie", cookies)
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, UserInfoResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 通过 mid 获取用户信息卡片
     */
    fun getLoginInfo(mid: Long): UserInfoCardResponse? {
        if (mid <= 0) return null
        val url = "$API_URL/x/web-interface/card?mid=$mid"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://www.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            gson.fromJson(body, UserInfoCardResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// ============ 响应数据模型 ============

data class UserInfoResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("data")
    val data: UserInfoData? = null
)

data class UserInfoData(
    @SerializedName("mid")
    val mid: Long = 0,
    @SerializedName("uname")
    val uname: String = "",
    @SerializedName("face")
    val face: String = "",
    @SerializedName("sign")
    val sign: String = "",
    @SerializedName("level")
    val level: Int = 0,
    @SerializedName("vip_type")
    val vipType: Int = 0,
    @SerializedName("vip_status")
    val vipStatus: Int = 0,
    @SerializedName("isLogin")
    val isLogin: Boolean = false
)

data class UserInfoCardResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("message")
    val message: String = "",
    @SerializedName("data")
    val data: UserCardData? = null
)

data class UserCardData(
    @SerializedName("card")
    val card: UserCard? = null
)

data class UserCard(
    @SerializedName("mid")
    val mid: Long = 0,
    @SerializedName("name")
    val name: String = "",
    @SerializedName("face")
    val face: String = "",
    @SerializedName("sign")
    val sign: String = "",
    @SerializedName("level")
    val level: Int = 0,
    @SerializedName("vip_type")
    val vipType: Int = 0,
    @SerializedName("vip_status")
    val vipStatus: Int = 0
)

data class DanmakuSegment(
    val p: String = "",
    val m: String = ""
)
```

### 4. `screens/home/login/LoginFragment.kt` — 修复登录流程

```kt
package com.bili.tv.bili_tv_app.screens.home.login

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bili.tv.bili_tv_app.databinding.FragmentLoginBinding
import com.bili.tv.bili_tv_app.models.User
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.api.AuthApi
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private var qrcodeKey: String = ""
    private var isPolling = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        generateQRCode()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun generateQRCode() {
        lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE
            binding.loginStatus.text = "正在获取二维码..."

            val qrCode = withContext(Dispatchers.IO) {
                AuthApi.getInstance().getQRCode()
            }

            binding.loadingProgress.visibility = View.GONE

            if (qrCode.code == 0 && qrCode.data != null) {
                qrcodeKey = qrCode.data.qrcodeKey

                try {
                    val barcodeEncoder = BarcodeEncoder()
                    val bitmap = barcodeEncoder.encodeBitmap(
                        qrCode.data.url,
                        com.google.zxing.BarcodeFormat.QR_CODE,
                        400,
                        400
                    )
                    binding.qrCodeImage.setImageBitmap(bitmap)
                    binding.loginStatus.text = "请使用B站APP扫描二维码登录"
                } catch (e: Exception) {
                    e.printStackTrace()
                    binding.loginStatus.text = "二维码生成失败"
                    Toast.makeText(requireContext(), "二维码生成失败", Toast.LENGTH_SHORT).show()
                }

                startPolling()
            } else {
                binding.loginStatus.text = "获取二维码失败: ${qrCode.message}"
                Toast.makeText(requireContext(), "获取二维码失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true

        lifecycleScope.launch {
            while (isPolling && qrcodeKey.isNotEmpty() && isAdded) {
                delay(2000)

                if (!isAdded) break

                val status = withContext(Dispatchers.IO) {
                    AuthApi.getInstance().checkQRCodeStatus(qrcodeKey)
                }

                if (status == null) continue  // 网络错误，继续轮询

                status.data?.let { data ->
                    when (data.code) {
                        0 -> {
                            // 登录成功
                            isPolling = false
                            binding.loginStatus.text = "登录成功，正在获取用户信息..."
                            onLoginSuccess(data)
                        }
                        -1, 86038 -> {
                            // 二维码过期
                            isPolling = false
                            binding.loginStatus.text = "二维码已过期"
                            Toast.makeText(requireContext(), "二维码已过期，请重新获取", Toast.LENGTH_SHORT).show()
                            delay(1000)
                            generateQRCode()
                        }
                        86090 -> {
                            // 未扫码
                            if (binding.loginStatus.text.toString() != "请使用B站APP扫描二维码登录") {
                                binding.loginStatus.text = "请使用B站APP扫描二维码登录"
                            }
                        }
                        86101 -> {
                            // 已扫码，未确认
                            binding.loginStatus.text = "已扫码，请在手机上确认登录"
                        }
                    }
                }
            }
        }
    }

    private fun onLoginSuccess(data: com.bili.tv.bili_tv_app.models.LoginStatusData) {
        lifecycleScope.launch {
            try {
                // ========== 核心：先拼装 Cookie 字符串 ==========
                val cookiesList = data.cookieInfo?.cookies
                if (cookiesList.isNullOrEmpty()) {
                    Log.e("LoginFragment", "登录成功但未返回Cookie信息")
                    Toast.makeText(requireContext(), "登录异常：未获取到Cookie", Toast.LENGTH_LONG).show()
                    binding.loginStatus.text = "登录异常，请重试"
                    return@launch
                }

                val cookiesString = cookiesList.joinToString("; ") { "${it.name}=${it.value}" }
                Log.d("LoginFragment", "Cookie获取成功: ${cookiesString.take(50)}...")

                // ========== 用 Cookie 获取用户信息（最可靠的方式） ==========
                val userInfo = withContext(Dispatchers.IO) {
                    AuthApi.getInstance().getUserInfoByCookie(cookiesString)
                }

                val user: User? = if (userInfo != null && userInfo.code == 0 && userInfo.data != null) {
                    val info = userInfo.data
                    Log.d("LoginFragment", "通过Cookie获取用户信息成功: ${info.uname}, mid: ${info.mid}")
                    User(
                        mid = info.mid,
                        uname = info.uname,
                        face = info.face,
                        sign = info.sign,
                        level = info.level,
                        vipType = info.vipType,
                        vipStatus = info.vipStatus
                    )
                } else {
                    // 降级方案：从 tokenInfo.mid 获取（修复后模型已有此字段）
                    val mid = data.tokenInfo?.mid ?: 0L
                    if (mid > 0) {
                        Log.d("LoginFragment", "降级：通过mid=$mid 获取用户信息")
                        val cardResp = withContext(Dispatchers.IO) {
                            AuthApi.getInstance().getLoginInfo(mid)
                        }
                        cardResp?.data?.card?.let { card ->
                            User(
                                mid = card.mid,
                                uname = card.name,
                                face = card.face,
                                sign = card.sign,
                                level = card.level,
                                vipType = card.vipType,
                                vipStatus = card.vipStatus
                            )
                        }
                    } else {
                        // 最终降级：从 Cookie 中提取 DedeUserID
                        val dedeUserId = cookiesList.find { it.name == "DedeUserID" }?.value?.toLongOrNull() ?: 0L
                        if (dedeUserId > 0) {
                            Log.d("LoginFragment", "最终降级：从Cookie提取DedeUserID=$dedeUserId")
                            val cardResp = withContext(Dispatchers.IO) {
                                AuthApi.getInstance().getLoginInfo(dedeUserId)
                            }
                            cardResp?.data?.card?.let { card ->
                                User(
                                    mid = card.mid,
                                    uname = card.name,
                                    face = card.face,
                                    sign = card.sign,
                                    level = card.level,
                                    vipType = card.vipType,
                                    vipStatus = card.vipStatus
                                )
                            }
                        } else {
                            null
                        }
                    }

                // ========== 持久化登录状态 ==========
                if (user != null) {
                    val token = data.tokenInfo?.accessToken ?: ""
                    val refreshToken = data.tokenInfo?.refreshToken ?: data.refreshToken ?: ""
                    val expiresIn = data.tokenInfo?.expiresIn ?: 0L

                    AuthService.saveLoginInfo(
                        accessToken = token,
                        refreshToken = refreshToken,
                        expiresIn = expiresIn,
                        cookies = cookiesString,
                        user = user
                    )

                    Log.d("LoginFragment", "登录状态保存成功: isLoggedIn=${AuthService.isLoggedIn}")
                    Toast.makeText(requireContext(), "登录成功: ${user.uname}", Toast.LENGTH_SHORT).show()
                    binding.loginStatus.text = "登录成功！"

                    // 确保保存完成后再返回
                    delay(300)
                    parentFragmentManager.popBackStack()
                } else {
                    Log.e("LoginFragment", "无法获取用户信息")
                    Toast.makeText(requireContext(), "登录异常：无法获取用户信息", Toast.LENGTH_LONG).show()
                    binding.loginStatus.text = "获取用户信息失败，请重试"
                }
            } catch (e: Exception) {
                Log.e("LoginFragment", "登录处理异常", e)
                Toast.makeText(requireContext(), "登录处理异常: ${e.message}", Toast.LENGTH_LONG).show()
                binding.loginStatus.text = "登录异常，请重试"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isPolling = false
        _binding = null
    }
}
```

### 5. `services/SettingsService.kt` — 增加自动播放上次视频设置

```kt
package com.bili.tv.bili_tv_app.services

import android.content.Context
import android.content.SharedPreferences

object SettingsService {

    private const val PREFS_NAME = "bili_settings"

    // 播放设置
    var defaultQuality: Int
        get() = prefs.getInt("default_quality", 80)
        set(value) = prefs.edit().putInt("default_quality", value).apply()

    var autoPlay: Boolean
        get() = prefs.getBoolean("auto_play", true)
        set(value) = prefs.edit().putBoolean("auto_play", value).apply()

    // ↓↓↓ 新增：自动播放上次视频 ↓↓↓
    var autoPlayLastVideo: Boolean
        get() = prefs.getBoolean("auto_play_last_video", false)
        set(value) = prefs.edit().putBoolean("auto_play_last_video", value).apply()

    // 上次播放的视频信息
    var lastPlayedBvid: String
        get() = prefs.getString("last_played_bvid", "") ?: ""
        set(value) = prefs.edit().putString("last_played_bvid", value).apply()

    var lastPlayedTitle: String
        get() = prefs.getString("last_played_title", "") ?: ""
        set(value) = prefs.edit().putString("last_played_title", value).apply()

    var lastPlayedCover: String
        get() = prefs.getString("last_played_cover", "") ?: ""
        set(value) = prefs.edit().putString("last_played_cover", value).apply()

    var lastPlayedCid: Long
        get() = prefs.getLong("last_played_cid", 0L)
        set(value) = prefs.edit().putLong("last_played_cid", value).apply()

    var lastPlayedProgress: Long
        get() = prefs.getLong("last_played_progress", 0L)
        set(value) = prefs.edit().putLong("last_played_progress", value).apply()

    var lastPlayedIsLive: Boolean
        get() = prefs.getBoolean("last_played_is_live", false)
        set(value) = prefs.edit().putBoolean("last_played_is_live", value).apply()

    var lastPlayedRoomId: Long
        get() = prefs.getLong("last_played_room_id", 0L)
        set(value) = prefs.edit().putLong("last_played_room_id", value).apply()

    /**
     * 保存上次播放的视频信息
     */
    fun saveLastPlayedVideo(
        bvid: String,
        title: String,
        cover: String,
        cid: Long,
        progress: Long,
        isLive: Boolean = false,
        roomId: Long = 0L
    ) {
        lastPlayedBvid = bvid
        lastPlayedTitle = title
        lastPlayedCover = cover
        lastPlayedCid = cid
        lastPlayedProgress = progress
        lastPlayedIsLive = isLive
        lastPlayedRoomId = roomId
    }

    /**
     * 清除上次播放记录
     */
    fun clearLastPlayedVideo() {
        prefs.edit()
            .remove("last_played_bvid")
            .remove("last_played_title")
            .remove("last_played_cover")
            .remove("last_played_cid")
            .remove("last_played_progress")
            .remove("last_played_is_live")
            .remove("last_played_room_id")
            .apply()
    }

    /**
     * 是否有有效的上次播放记录
     */
    fun hasLastPlayedVideo(): Boolean {
        return lastPlayedBvid.isNotEmpty()
    }

    // 弹幕设置
    var danmakuEnabled: Boolean
        get() = prefs.getBoolean("danmaku_enabled", true)
        set(value) = prefs.edit().putBoolean("danmaku_enabled", value).apply()

    var danmakuFontSize: Float
        get() = prefs.getFloat("danmaku_font_size", 25f)
        set(value) = prefs.edit().putFloat("danmaku_font_size", value).apply()

    var danmakuOpacity: Float
        get() = prefs.getFloat("danmaku_opacity", 0.8f)
        set(value) = prefs.edit().putFloat("danmaku_opacity", value).apply()

    var danmakuDensity: Float
        get() = prefs.getFloat("danmaku_density", 1.0f)
        set(value) = prefs.edit().putFloat("danmaku_density", value).apply()

    // 插件设置
    var danmakuEnhanceEnabled: Boolean
        get() = prefs.getBoolean("danmaku_enhance_enabled", true)
        set(value) = prefs.edit().putBoolean("danmaku_enhance_enabled", value).apply()

    var adFilterEnabled: Boolean
        get() = prefs.getBoolean("ad_filter_enabled", true)
        set(value) = prefs.edit().putBoolean("ad_filter_enabled", value).apply()

    var sponsorBlockEnabled: Boolean
        get() = prefs.getBoolean("sponsor_block_enabled", false)
        set(value) = prefs.edit().putBoolean("sponsor_block_enabled", value).apply()

    // 其他
    var splashAnimationEnabled: Boolean
        get() = prefs.getBoolean("splash_animation_enabled", true)
        set(value) = prefs.edit().putBoolean("splash_animation_enabled", value).apply()

    // 内部
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
```

### 6. `res/layout/fragment_settings.xml` — 增加自动播放上次视频开关

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#1A1A2E"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="40dp">

        <!-- 顶部栏 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:layout_marginBottom="30dp">

            <ImageButton
                android:id="@+id/backButton"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="返回"
                android:src="@android:drawable/ic_menu_revert" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:text="设置"
                android:textColor="#FFFFFF"
                android:textSize="28sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- 播放设置分区 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="播放设置"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp"
            android:layout_marginBottom="24dp">

            <!-- 自动连播 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="自动连播"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/autoPlaySwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- ↓↓↓ 新增：自动播放上次视频 ↓↓↓ -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="启动时自动播放上次视频"
                        android:textColor="#E0E0E0"
                        android:textSize="18sp" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:text="开启后启动应用将自动进入上次播放的视频"
                        android:textColor="#888888"
                        android:textSize="13sp" />
                </LinearLayout>

                <Switch
                    android:id="@+id/autoPlayLastVideoSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- 画质选择 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="默认画质"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Spinner
                    android:id="@+id/qualitySpinner"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:entries="@array/quality_options" />
            </LinearLayout>
        </LinearLayout>

        <!-- 弹幕设置分区 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="弹幕设置"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp"
            android:layout_marginBottom="24dp">

            <!-- 弹幕开关 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="显示弹幕"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/danmakuSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- 弹幕增强 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="弹幕增强过滤"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/adFilterSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>

            <!-- 赞助跳过 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="赞助内容跳过"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/sponsorBlockSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>
        </LinearLayout>

        <!-- 其他设置 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="其他"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp"
            android:layout_marginBottom="24dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingVertical="12dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="启动动画"
                    android:textColor="#E0E0E0"
                    android:textSize="18sp" />

                <Switch
                    android:id="@+id/splashAnimationSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:switchMinWidth="60dp" />
            </LinearLayout>
        </LinearLayout>

        <!-- 关于 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="关于"
            android:textColor="#FB7299"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="#16213E"
            android:orientation="vertical"
            android:padding="20dp">

            <TextView
                android:id="@+id/aboutText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="#AAAAAA"
                android:textSize="16sp"
                android:lineSpacingExtra="4dp" />
        </LinearLayout>

    </LinearLayout>
</ScrollView>
```

### 7. `res/values/arrays.xml` — 确保有画质选项数组

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="quality_options">
        <item>360P</item>
        <item>480P</item>
        <item>720P</item>
        <item>1080P</item>
        <item>1080P+</item>
    </string-array>
</resources>
```

### 8. `screens/home/settings/SettingsFragment.kt` — 绑定新开关

```kt
package com.bili.tv.bili_tv_app.screens.home.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bili.tv.bili_tv_app.databinding.FragmentSettingsBinding
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        // 返回按钮
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 加载当前设置状态
        binding.splashAnimationSwitch.isChecked = SettingsService.splashAnimationEnabled
        binding.danmakuSwitch.isChecked = SettingsService.danmakuEnabled
        binding.adFilterSwitch.isChecked = SettingsService.adFilterEnabled
        binding.sponsorBlockSwitch.isChecked = SettingsService.sponsorBlockEnabled
        binding.autoPlaySwitch.isChecked = SettingsService.autoPlay
        // ↓↓↓ 新增 ↓↓↓
        binding.autoPlayLastVideoSwitch.isChecked = SettingsService.autoPlayLastVideo

        // 保存开关状态
        binding.splashAnimationSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.splashAnimationEnabled = isChecked
        }

        binding.danmakuSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.danmakuEnabled = isChecked
        }

        binding.adFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.adFilterEnabled = isChecked
        }

        binding.sponsorBlockSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.sponsorBlockEnabled = isChecked
        }

        binding.autoPlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.autoPlay = isChecked
        }

        // ↓↓↓ 新增：自动播放上次视频开关 ↓↓↓
        binding.autoPlayLastVideoSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsService.autoPlayLastVideo = isChecked
            if (!isChecked) {
                // 关闭时清除记录
                SettingsService.clearLastPlayedVideo()
                Toast.makeText(requireContext(), "已清除上次播放记录", Toast.LENGTH_SHORT).show()
            }
        }

        // 画质选择
        val qualities = arrayOf("360P", "480P", "720P", "1080P", "1080P+")
        binding.qualitySpinner.setSelection(getCurrentQualityIndex())

        binding.qualitySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val quality = when (position) {
                    0 -> 16   // 360P
                    1 -> 32   // 480P
                    2 -> 64   // 720P
                    3 -> 80   // 1080P
                    4 -> 112  // 1080P+
                    else -> 80
                }
                SettingsService.defaultQuality = quality
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // 关于
        binding.aboutText.text = "BiliTV v1.2.3\n哔哩哔哩电视版客户端\n\n基于Flutter源码转换"
    }

    private fun getCurrentQualityIndex(): Int {
        return when (SettingsService.defaultQuality) {
            16 -> 0
            32 -> 1
            64 -> 2
            80 -> 3
            112 -> 4
            else -> 3
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 9. `models/Episode.kt` — 新增分集模型

```kt
package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class Episode(
    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("part")
    val part: String = "",

    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("dimension")
    val dimension: Video.Dimension? = null,

    @SerializedName("first_frame")
    val firstFrame: String? = null
) {
    fun getFormattedDuration(): String {
        val totalSeconds = (duration / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
```

### 10. `models/VideoDetail.kt` — 新增视频详情模型（含分集信息）

```kt
package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class VideoDetailResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: VideoDetail? = null
)

data class VideoDetail(
    @SerializedName("bvid")
    val bvid: String = "",

    @SerializedName("aid")
    val aid: Long = 0,

    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("pic")
    val pic: String = "",

    @SerializedName("desc")
    val desc: String = "",

    @SerializedName("pubdate")
    val pubdate: Long = 0,

    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("owner")
    val owner: Video.Owner? = null,

    @SerializedName("stat")
    val stat: Video.Stat? = null,

    @SerializedName("dimension")
    val dimension: Video.Dimension? = null,

    @SerializedName("pages")
    val pages: List<Episode> = emptyList(),

    @SerializedName("tid")
    val tid: Int = 0,

    @SerializedName("tname")
    val tname: String = "",

    @SerializedName("videos")
    val videos: Int = 0,

    @SerializedName("season_id")
    val seasonId: Long = 0,

    @SerializedName("season_type")
    val seasonType: Int = 0,

    @SerializedName("ugc_season")
    val ugcSeason: UgcSeason? = null
) {
    /**
     * 是否是多P视频
     */
    fun isMultiPart(): Boolean = pages.size > 1

    /**
     * 获取当前分集在列表中的索引
     */
    fun getPageIndex(cid: Long): Int {
        return pages.indexOfFirst { it.cid == cid }.coerceAtLeast(0)
    }
}

data class UgcSeason(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("sections")
    val sections: List<UgcSeasonSection> = emptyList()
)

data class UgcSeasonSection(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("episodes")
    val episodes: List<UgcSeasonEpisode> = emptyList()
)

data class UgcSeasonEpisode(
    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("bvid")
    val bvid: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("duration")
    val duration: Long = 0
)
```

### 11. `models/LiveRoom.kt` — 新增直播间模型

```kt
package com.bili.tv.bili_tv_app.models

import com.google.gson.annotations.SerializedName

data class LiveRoom(
    @SerializedName("room_id")
    val roomId: Long = 0,

    @SerializedName("uid")
    val uid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("cover")
    val cover: String = "",

    @SerializedName("uname")
    val uname: String = "",

    @SerializedName("face")
    val face: String = "",

    @SerializedName("live_status")
    val liveStatus: Int = 0,  // 0:未开播, 1:直播中, 2:轮播中

    @SerializedName("area_id")
    val areaId: Int = 0,

    @SerializedName("area_name")
    val areaName: String = "",

    @SerializedName("online")
    val online: Int = 0,

    @SerializedName("play_url")
    val playUrl: String? = null
) {
    fun isLive(): Boolean = liveStatus == 1

    fun getFormattedOnline(): String {
        return when {
            online >= 10000 -> String.format("%.1f万", online / 10000.0)
            else -> online.toString()
        }
    }
}

data class LiveRoomListResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: LiveRoomListData? = null
)

data class LiveRoomListData(
    @SerializedName("list")
    val list: List<LiveRoomItem>? = null,

    @SerializedName("count")
    val count: Int = 0
)

data class LiveRoomItem(
    @SerializedName("roomid")
    val roomid: Long = 0,

    @SerializedName("uid")
    val uid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("cover")
    val cover: String = "",

    @SerializedName("uname")
    val uname: String = "",

    @SerializedName("face")
    val face: String = "",

    @SerializedName("live_status")
    val liveStatus: Int = 0,

    @SerializedName("area_id")
    val areaId: Int = 0,

    @SerializedName("area_name")
    val areaName: String = "",

    @SerializedName("online")
    val online: Int = 0
) {
    fun toLiveRoom(): LiveRoom {
        return LiveRoom(
            roomId = roomid,
            uid = uid,
            title = title,
            cover = cover,
            uname = uname,
            face = face,
            liveStatus = liveStatus,
            areaId = areaId,
            areaName = areaName,
            online = online
        )
    }
}

data class LiveStreamResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: LiveStreamData? = null
)

data class LiveStreamData(
    @SerializedName("playurl_info")
    val playurlInfo: LivePlayUrlInfo? = null
)

data class LivePlayUrlInfo(
    @SerializedName("playurl")
    val playurl: LivePlayUrl? = null
)

data class LivePlayUrl(
    @SerializedName("stream")
    val stream: List<LiveStream>? = null
)

data class LiveStream(
    @SerializedName("protocol_name")
    val protocolName: String = "",

    @SerializedName("format")
    val format: List<LiveFormat>? = null
)

data class LiveFormat(
    @SerializedName("format_name")
    val formatName: String = "",

    @SerializedName("codec")
    val codec: List<LiveCodec>? = null
)

data class LiveCodec(
    @SerializedName("codec_name")
    val codecName: String = "",

    @SerializedName("current_qn")
    val currentQn: Int = 0,

    @SerializedName("base_url")
    val baseUrl: String = "",

    @SerializedName("url_info")
    val urlInfo: List<LiveUrlInfo>? = null
)

data class LiveUrlInfo(
    @SerializedName("host")
    val host: String = "",

    @SerializedName("extra")
    val extra: String = "",

    @SerializedName("stream")
    val stream: String? = null
) {
    fun buildFullUrl(baseUrl: String): String {
        return if (stream != null) {
            "$host$baseUrl?$extra$stream"
        } else {
            "$host$baseUrl?$extra"
        }
    }
}
```

### 12. `services/api/BilibiliApiService.kt` — 增加直播和分集API

```kt
package com.bili.tv.bili_tv_app.services.api

import com.bili.tv.bili_tv_app.models.*
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class BilibiliApiService private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://api.bilibili.com"
        private const val LIVE_URL = "https://api.live.bilibili.com"

        @Volatile
        private var instance: BilibiliApiService? = null

        fun getInstance(): BilibiliApiService {
            return instance ?: synchronized(this) {
                instance ?: BilibiliApiService().also { instance = it }
            }
        }
    }

    /**
     * 构建带认证信息的请求
     */
    private fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://www.bilibili.com")

        val cookies = AuthService.cookies
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies)
        }

        return builder
    }

    /**
     * 获取推荐视频列表
     */
    fun getRecommendVideos(page: Int): List<Video> {
        val url = "$BASE_URL/x/web-interface/popular?ps=20&pn=${page + 1}"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val list = data["list"] as? List<*> ?: return emptyList()

            list.mapNotNull { item ->
                try {
                    gson.fromJson(gson.toJson(item), Video::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取视频详情（含分集信息）
     */
    fun getVideoInfo(bvid: String): VideoDetail? {
        val url = "$BASE_URL/x/web-interface/view?bvid=$bvid"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val resp = gson.fromJson(body, VideoDetailResponse::class.java)
            if (resp.code == 0) resp.data else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取视频播放地址
     */
    fun getPlayUrl(aid: Long, cid: Long, quality: Int): String? {
        val url = "$BASE_URL/x/player/playurl?avid=$aid&cid=$cid&qn=$quality&fnval=16&fourk=1"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return null
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return null

            val data = json["data"] as? Map<*, *> ?: return null

            // 优先使用 DASH 格式
            val dash = data["dash"] as? Map<*, *>
            if (dash != null) {
                val videoList = dash["video"] as? List<*> ?: return null
                // 找到匹配或最高的画质
                val targetVideo = videoList.mapNotNull { v ->
                    try {
                        gson.fromJson(gson.toJson(v), VideoTrack::class.java)
                    } catch (e: Exception) { null }
                }.maxByOrNull { it.bandwidth }

                if (targetVideo != null && targetVideo.baseUrl.isNotEmpty()) {
                    return targetVideo.baseUrl
                }
            }

            // 降级使用 DURL 格式
            val durl = data["durl"] as? List<*>
            if (durl != null && durl.isNotEmpty()) {
                val first = durl[0] as? Map<*, *>
                return first?.get("url") as? String
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 搜索视频
     */
    fun searchVideos(keyword: String): List<Video> {
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val url = "$BASE_URL/x/web-interface/search/type?search_type=video&keyword=$encodedKeyword&page=1"
        val request = buildRequest(url).get().build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val result = data["result"] as? List<*> ?: return emptyList()

            result.mapNotNull { item ->
                try {
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    Video(
                        bvid = map["bvid"] as? String ?: "",
                        aid = ((map["aid"] as? Double)?.toLong() ?: 0L),
                        title = (map["title"] as? String)?.replace(Regex("<[^>]+>"), "") ?: "",
                        pic = map["pic"] as? String ?: "",
                        author = map["author"] as? String ?: "",
                        duration = map["duration"] as? String ?: "",
                        view = ((map["play"] as? Double)?.toInt() ?: 0),
                        danmaku = ((map["video_review"] as? Double)?.toInt() ?: 0),
                        owner = Video.Owner(
                            mid = ((map["mid"] as? Double)?.toLong() ?: 0L),
                            name = map["author"] as? String ?: ""
                        )
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ==================== 直播相关 API ====================

    /**
     * 获取关注的主播直播间列表（左右键数据源）
     */
    fun getFollowLiveRooms(page: Int = 1): List<LiveRoom> {
        if (!AuthService.isLoggedIn) return emptyList()

        val url = "$LIVE_URL/xlive/web-ucenter/v1/xfetter/room_list?page=$page&page_size=20"
        val request = buildRequest(url)
            .header("Referer", "https://live.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val list = data["list"] as? List<*> ?: return emptyList()

            list.mapNotNull { item ->
                try {
                    val room = gson.fromJson(gson.toJson(item), LiveRoomItem::class.java)
                    room.toLiveRoom()
                } catch (e: Exception) { null }
            }.filter { it.isLive() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取推荐直播间列表（上下键数据源）
     */
    fun getRecommendLiveRooms(page: Int = 1): List<LiveRoom> {
        val url = "$LIVE_URL/xlive/web-interface/v1/second/getList?platform=web&sort_type=online&page=$page&page_size=20"
        val request = buildRequest(url)
            .header("Referer", "https://live.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(body, Map::class.java) as? Map<*, *> ?: return emptyList()
            val code = (json["code"] as? Double)?.toInt() ?: -1
            if (code != 0) return emptyList()

            val data = json["data"] as? Map<*, *> ?: return emptyList()
            val list = data["list"] as? List<*> ?: return emptyList()

            list.mapNotNull { item ->
                try {
                    val room = gson.fromJson(gson.toJson(item), LiveRoomItem::class.java)
                    room.toLiveRoom()
                } catch (e: Exception) { null }
            }.filter { it.isLive() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取直播间播放地址
     */
    fun getLiveStreamUrl(roomId: Long): String? {
        val url = "$LIVE_URL/xlive/web-room/v1/index/getRoomPlayInfo?room_id=$roomId&protocol=0,1&format=0,1,2&codec=0,1&qn=10000&platform=web"
        val request = buildRequest(url)
            .header("Referer", "https://live.bilibili.com")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val resp = gson.fromJson(body, LiveStreamResponse::class.java)

            if (resp.code != 0 || resp.data?.playurlInfo?.playurl == null) return null

            val streams = resp.data.playurlInfo.playurl.stream
            if (streams.isNullOrEmpty()) return null

            // 优先找 http-stream (FLV)，其次找 http-hls (HLS)
            for (protocol in streams) {
                if (protocol.formatName == "http_stream" || protocol.formatName == "http_hls") {
                    val codecs = protocol.format?.firstOrNull()?.codec
                    if (!codecs.isNullOrEmpty()) {
                        val codec = codecs.maxByOrNull { it.currentQn }
                        if (codec != null && codec.baseUrl.isNotEmpty()) {
                            val urlInfo = codec.urlInfo?.firstOrNull()
                            if (urlInfo != null) {
                                return urlInfo.buildFullUrl(codec.baseUrl)
                            }
                            return codec.baseUrl
                        }
                    }
                }
            }

            // 最终降级：返回第一个可用的流
            for (stream in streams) {
                for (format in stream.format ?: emptyList()) {
                    for (codec in format.codec ?: emptyList()) {
                        if (codec.baseUrl.isNotEmpty()) {
                            val urlInfo = codec.urlInfo?.firstOrNull()
                            if (urlInfo != null) {
                                return urlInfo.buildFullUrl(codec.baseUrl)
                            }
                            return codec.baseUrl
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
```

### 13. `screens/home/HomeFragment.kt` — 启动时自动播放上次视频

```kt
package com.bili.tv.bili_tv_app.screens.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.databinding.FragmentHomeBinding
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.screens.home.settings.SettingsFragment
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.SettingsService
import com.bili.tv.bili_tv_app.widgets.VideoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private val videoList = mutableListOf<Video>()

    // 标记是否已经处理过自动播放（防止从播放器返回时再次触发）
    private var autoPlayHandled = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated called")
        setupUI()
        loadContent()
    }

    private fun setupUI() {
        // 视频网格
        videoAdapter = VideoAdapter(videoList) { video ->
            navigateToPlayer(video)
        }

        binding.videosRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = videoAdapter
        }

        // 搜索按钮
        binding.searchButton.setOnClickListener {
            navigateToSearch()
        }

        // 设置按钮
        binding.settingsButton.setOnClickListener {
            navigateToSettings()
        }

        // 用户按钮
        binding.userButton.setOnClickListener {
            if (AuthService.isLoggedIn) {
                showUserInfo()
            } else {
                navigateToLogin()
            }
        }

        // 下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            loadContent()
        }
    }

    private fun loadContent() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true

            val videos = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getRecommendVideos(0)
            }

            videoList.clear()
            videoList.addAll(videos)
            videoAdapter.notifyDataSetChanged()

            binding.swipeRefresh.isRefreshing = false

            if (videoList.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.videosRecyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.videosRecyclerView.visibility = View.VISIBLE
            }

            // ↓↓↓ 自动播放上次视频逻辑 ↓↓↓
            checkAndAutoPlayLastVideo()
        }
    }

    /**
     * 检查并自动播放上次观看的视频
     */
    private fun checkAndAutoPlayLastVideo() {
        if (autoPlayHandled) return
        if (!SettingsService.autoPlayLastVideo) return
        if (!SettingsService.hasLastPlayedVideo()) return
        // 如果是直播，暂不自动播放（直播可能已结束）
        if (SettingsService.lastPlayedIsLive) return

        autoPlayHandled = true

        lifecycleScope.launch {
            delay(500) // 稍微延迟，让首页先渲染出来

            val lastBvid = SettingsService.lastPlayedBvid
            val lastTitle = SettingsService.lastPlayedTitle
            val lastCover = SettingsService.lastPlayedCover

            Log.d("HomeFragment", "自动播放上次视频: $lastBvid - $lastTitle")

            // 显示提示
            Toast.makeText(
                requireContext(),
                "继续播放: $lastTitle",
                Toast.LENGTH_SHORT
            ).show()

            navigateToPlayerWithList(
                Video(
                    bvid = lastBvid,
                    title = lastTitle,
                    pic = lastCover,
                    cid = SettingsService.lastPlayedCid
                ),
                videoList.toList(),
                videoList.indexOfFirst { it.bvid == lastBvid }.coerceAtLeast(0)
            )
        }
    }

    private fun navigateToPlayer(video: Video) {
        navigateToPlayerWithList(video, videoList.toList(), videoList.indexOf(video).coerceAtLeast(0))
    }

    private fun navigateToPlayerWithList(video: Video, list: List<Video>, index: Int) {
        val fragment = com.bili.tv.bili_tv_app.screens.player.PlayerFragment.newInstance(
            bvid = video.bvid,
            title = video.title,
            coverUrl = video.pic,
            isLive = false,
            categoryVideoList = list,
            categoryVideoIndex = index
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToSearch() {
        val fragment = com.bili.tv.bili_tv_app.screens.home.search.SearchFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToSettings() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToLogin() {
        val fragment = com.bili.tv.bili_tv_app.screens.home.login.LoginFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showUserInfo() {
        AuthService.currentUser?.let { user ->
            Toast.makeText(
                requireContext(),
                "欢迎, ${user.uname} (Lv.${user.level})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 14. `res/layout/fragment_player.xml` — 增加切换提示覆盖层

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/playerRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- 播放器 -->
    <androidx.media3.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 弹幕层 -->
    <com.bili.tv.bili_tv_app.widgets.DanmakuView
        android:id="@+id/danmakuView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clickable="false"
        android:focusable="false" />

    <!-- 加载进度 -->
    <ProgressBar
        android:id="@+id/loadingProgress"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_gravity="center"
        android:visibility="gone" />

    <!-- 顶部信息栏 -->
    <LinearLayout
        android:id="@+id/topBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/gradient_top"
        android:orientation="vertical"
        android:padding="16dp"
        android:visibility="visible">

        <!-- 返回按钮 + 标题 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/backButton"
                android:layout_width="44dp"
                android:layout_height="44dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="返回"
                android:src="@android:drawable/ic_menu_revert" />

            <TextView
                android:id="@+id/videoTitle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:layout_weight="1"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="#FFFFFF"
                android:textSize="20sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- 分集/模式指示器 -->
        <LinearLayout
            android:id="@+id/episodeIndicator"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="56dp"
            android:layout_marginTop="4dp"
            android:orientation="horizontal"
            android:visibility="gone">

            <TextView
                android:id="@+id/modeLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="#33FB7299"
                android:paddingHorizontal="8dp"
                android:paddingVertical="2dp"
                android:text="点播"
                android:textColor="#FFFFFF"
                android:textSize="12sp" />

            <TextView
                android:id="@+id/episodeLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:textColor="#AAAAAA"
                android:textSize="14sp" />
        </LinearLayout>
    </LinearLayout>

    <!-- 切换提示浮层 -->
    <LinearLayout
        android:id="@+id/switchOverlay"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="#CC000000"
        android:gravity="center"
        android:orientation="vertical"
        android:paddingHorizontal="32dp"
        android:paddingVertical="20dp"
        android:visibility="gone">

        <ImageView
            android:id="@+id/switchIcon"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:contentDescription="切换方向"
            android:src="@android:drawable/ic_media_previous" />

        <TextView
            android:id="@+id/switchTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:ellipsize="end"
            android:maxLines="2"
            android:maxWidth="280dp"
            android:textColor="#FFFFFF"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/switchSubtitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="#AAAAAA"
            android:textSize="13sp" />
    </LinearLayout>

    <!-- 底部提示 -->
    <TextView
        android:id="@+id/hintText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_marginBottom="24dp"
        android:text="← → 切换分集  ↑ ↓ 切换视频"
        android:textColor="#66FFFFFF"
        android:textSize="13sp"
        android:visibility="gone" />

</FrameLayout>
```

### 15. `screens/player/PlayerFragment.kt` — 完整重写，支持方向键切换

```kt
package com.bili.tv.bili_tv_app.screens.player

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.util.UnstableApi
import com.bili.tv.bili_tv_app.databinding.FragmentPlayerBinding
import com.bili.tv.bili_tv_app.models.Episode
import com.bili.tv.bili_tv_app.models.LiveRoom
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.models.VideoDetail
import com.bili.tv.bili_tv_app.services.SettingsService
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.widgets.DanmakuView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var danmakuView: DanmakuView? = null

    // 当前播放信息
    private var bvid: String = ""
    private var title: String = ""
    private var coverUrl: String = ""
    private var currentAid: Long = 0
    private var currentCid: Long = 0

    // 模式：直播 or 点播
    private var isLiveMode: Boolean = false
    private var liveRoomId: Long = 0

    // 点播模式 - 分集数据
    private var videoDetail: VideoDetail? = null
    private var episodeList: List<Episode> = emptyList()
    private var currentEpisodeIndex: Int = 0

    // 点播模式 - 分类列表（上下键）
    private var categoryVideoList: List<Video> = emptyList()
    private var categoryVideoIndex: Int = 0

    // 直播模式 - 数据源
    private var followLiveList: List<LiveRoom> = emptyList()
    private var followLiveIndex: Int = 0
    private var recommendLiveList: List<LiveRoom> = emptyList()
    private var recommendLiveIndex: Int = 0

    // 切换提示
    private val handler = Handler(Looper.getMainLooper())
    private var hideSwitchOverlayRunnable: Runnable? = null
    private var hideHintRunnable: Runnable? = null

    // 上次按键时间（防抖）
    private var lastKeyTime: Long = 0
    private val keyDebounceMs = 600L

    // 加载协程
    private var loadJob: Job? = null

    companion object {
        private const val ARG_BVID = "bvid"
        private const val ARG_TITLE = "title"
        private const val ARG_COVER = "cover"
        private const val ARG_IS_LIVE = "is_live"
        private const val ARG_LIVE_ROOM_ID = "live_room_id"
        private const val ARG_CATEGORY_VIDEOS = "category_videos"
        private const val ARG_CATEGORY_INDEX = "category_index"

        fun newInstance(
            bvid: String,
            title: String,
            coverUrl: String,
            isLive: Boolean = false,
            liveRoomId: Long = 0L,
            categoryVideoList: List<Video> = emptyList(),
            categoryVideoIndex: Int = 0
        ): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BVID, bvid)
                    putString(ARG_TITLE, title)
                    putString(ARG_COVER, coverUrl)
                    putBoolean(ARG_IS_LIVE, isLive)
                    putLong(ARG_LIVE_ROOM_ID, liveRoomId)
                    putStringArrayList(ARG_CATEGORY_VIDEOS, ArrayList(categoryVideoList.map {
                        "${it.bvid}|${it.title}|${it.pic}|${it.cid}"
                    }))
                    putInt(ARG_CATEGORY_INDEX, categoryVideoIndex)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            bvid = it.getString(ARG_BVID, "")
            title = it.getString(ARG_TITLE, "")
            coverUrl = it.getString(ARG_COVER, "")
            isLiveMode = it.getBoolean(ARG_IS_LIVE, false)
            liveRoomId = it.getLong(ARG_LIVE_ROOM_ID, 0L)

            // 解析分类视频列表
            val videoStrings = it.getStringArrayList(ARG_CATEGORY_VIDEOS)
            if (videoStrings != null) {
                categoryVideoList = videoStrings.mapNotNull { str ->
                    val parts = str.split("|")
                    if (parts.size >= 3) {
                        Video(
                            bvid = parts[0],
                            title = parts[1],
                            pic = parts[2],
                            cid = if (parts.size > 3) parts[3].toLongOrNull() ?: 0L else 0L
                        )
                    } else null
                }
            }
            categoryVideoIndex = it.getInt(ARG_CATEGORY_INDEX, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPlayer()
        setupKeyHandler()
        setupUI()

        if (isLiveMode) {
            loadLiveRoom()
        } else {
            loadVideo()
        }
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            savePlaybackProgress()
            parentFragmentManager.popBackStack()
        }

        binding.videoTitle.text = title

        // 根据模式更新UI
        updateModeUI()
    }

    private fun updateModeUI() {
        if (isLiveMode) {
            binding.modeLabel.text = "直播"
            binding.episodeIndicator.visibility = View.VISIBLE
            binding.episodeLabel.text = "房间号: $liveRoomId"
            binding.hintText.text = "← → 切换关注直播间  ↑ ↓ 切换推荐直播间"
            binding.hintText.visibility = View.VISIBLE
            hideHintRunnable?.let { handler.removeCallbacks(it) }
            hideHintRunnable = Runnable { binding.hintText.visibility = View.GONE }
            handler.postDelayed(hideHintRunnable!!, 5000)
        } else {
            binding.modeLabel.text = "点播"
            binding.episodeIndicator.visibility = View.GONE
            binding.hintText.text = "← → 切换分集  ↑ ↓ 切换视频"
            binding.hintText.visibility = View.VISIBLE
            hideHintRunnable?.let { handler.removeCallbacks(it) }
            hideHintRunnable = Runnable { binding.hintText.visibility = View.GONE }
            handler.postDelayed(hideHintRunnable!!, 5000)
        }
    }

    private fun setupPlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://www.bilibili.com",
                "Origin" to "https://www.bilibili.com"
            ))

        val dataSourceFactory = DefaultDataSource.Factory(requireContext(), httpDataSourceFactory)

        player = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory))
            .build().also {
                binding.playerView.player = it

                it.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                binding.loadingProgress.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                binding.loadingProgress.visibility = View.GONE
                                it.volume = 1.0f
                                it.playWhenReady = true
                                // 恢复进度
                                if (!isLiveMode) {
                                    val savedProgress = SettingsService.lastPlayedProgress
                                    if (savedProgress > 0 && savedProgress < (it.duration * 0.95)) {
                                        it.seekTo(savedProgress)
                                    }
                                }
                            }
                            Player.STATE_ENDED -> {
                                binding.loadingProgress.visibility = View.GONE
                                if (!isLiveMode) {
                                    playNextEpisodeOrVideo()
                                }
                            }
                            Player.STATE_IDLE -> {
                                binding.loadingProgress.visibility = View.GONE
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("PlayerFragment", "播放错误: ${error.message}", error)
                        binding.loadingProgress.visibility = View.GONE
                        Toast.makeText(requireContext(), "播放错误: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }

        if (SettingsService.danmakuEnabled) {
            setupDanmaku()
        }
    }

    private fun setupDanmaku() {
        danmakuView = binding.danmakuView
        danmakuView?.apply {
            setTextSize(SettingsService.danmakuFontSize)
            setAlpha(SettingsService.danmakuOpacity)
            setSpeed(SettingsService.danmakuDensity)
        }
    }

    // ==================== 方向键处理 ====================

    private fun setupKeyHandler() {
        // 在 PlayerView 上设置按键监听，拦截默认的快进快退行为
        binding.playerView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleDpadKey(keyCode)
            } else false
        }

        // 同时在根布局上设置，作为备用
        binding.playerRoot.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleDpadKey(keyCode)
            } else false
        }

        binding.playerRoot.isFocusableInTouchMode = true
        binding.playerRoot.requestFocus()
    }

    private fun handleDpadKey(keyCode: Int): Boolean {
        // 防抖：短时间内不重复触发
        val now = System.currentTimeMillis()
        if (now - lastKeyTime < keyDebounceMs) return false
        lastKeyTime = now

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isLiveMode) switchFollowLiveRoom(-1) else switchEpisode(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isLiveMode) switchFollowLiveRoom(1) else switchEpisode(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isLiveMode) switchRecommendLiveRoom(-1) else switchCategoryVideo(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isLiveMode) switchRecommendLiveRoom(1) else switchCategoryVideo(1)
                return true
            }
        }
        return false
    }

    // ==================== 点播模式：切换逻辑 ====================

    /**
     * 切换分集（左右键）
     * @param direction -1=上一集, +1=下一集
     */
    private fun switchEpisode(direction: Int) {
        if (episodeList.size <= 1) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
                title = "没有更多分集",
                subtitle = ""
            )
            return
        }

        val newIndex = currentEpisodeIndex + direction
        if (newIndex < 0 || newIndex >= episodeList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
                title = "已是${if (direction < 0) "第一" else "最后一"}集",
                subtitle = ""
            )
            return
        }

        currentEpisodeIndex = newIndex
        val episode = episodeList[newIndex]
        currentCid = episode.cid

        Log.d("PlayerFragment", "切换分集: P${episode.page} - ${episode.part}")

        updateEpisodeUI()
        showSwitchOverlay(
            iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
            title = "P${episode.page} ${episode.part}",
            subtitle = "${newIndex + 1}/${episodeList.size}"
        )

        // 加载新分集
        loadEpisodePlayUrl(episode.cid)
    }

    /**
     * 切换分类视频（上下键）
     * @param direction -1=上一个视频, +1=下一个视频
     */
    private fun switchCategoryVideo(direction: Int) {
        if (categoryVideoList.isEmpty()) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
                title = "没有更多视频",
                subtitle = ""
            )
            return
        }

        val newIndex = categoryVideoIndex + direction
        if (newIndex < 0 || newIndex >= categoryVideoList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
                title = "已是${if (direction < 0) "第一" else "最后"}个视频",
                subtitle = ""
            )
            return
        }

        categoryVideoIndex = newIndex
        val video = categoryVideoList[newIndex]

        Log.d("PlayerFragment", "切换分类视频: ${video.bvid} - ${video.title}")

        showSwitchOverlay(
            iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
            title = video.title,
            subtitle = "${newIndex + 1}/${categoryVideoList.size}"
        )

        // 切换到新视频（重置分集状态）
        bvid = video.bvid
        title = video.title
        coverUrl = video.pic
        currentCid = video.cid
        currentEpisodeIndex = 0
        episodeList = emptyList()
        videoDetail = null

        binding.videoTitle.text = title
        loadVideo()
    }

    /**
     * 播放下一个分集或下一个视频
     */
    private fun playNextEpisodeOrVideo() {
        // 优先尝试下一集
        if (episodeList.size > 1 && currentEpisodeIndex < episodeList.size - 1) {
            switchEpisode(1)
            return
        }
        // 其次尝试下一个视频
        if (categoryVideoList.isNotEmpty() && categoryVideoIndex < categoryVideoList.size - 1) {
            switchCategoryVideo(1)
        }
    }

    // ==================== 直播模式：切换逻辑 ====================

    /**
     * 切换关注列表直播间（左右键）
     */
    private fun switchFollowLiveRoom(direction: Int) {
        if (followLiveList.isEmpty()) {
            // 如果还没有加载过，先加载
            loadFollowLiveRooms()
            return
        }

        val newIndex = followLiveIndex + direction
        if (newIndex < 0 || newIndex >= followLiveList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_media_previous else android.R.drawable.ic_media_next,
                title = "已是关注列表${if (direction < 0) "第一个" else "最后一个"}直播间",
                subtitle = "(关注列表)"
            )
            return
        }

        followLiveIndex = newIndex
        val room = followLiveList[newIndex]
        switchToLiveRoom(room, "关注列表")
    }

    /**
     * 切换推荐直播间（上下键）
     */
    private fun switchRecommendLiveRoom(direction: Int) {
        if (recommendLiveList.isEmpty()) {
            // 如果还没有加载过，先加载
            loadRecommendLiveRooms()
            return
        }

        val newIndex = recommendLiveIndex + direction
        if (newIndex < 0 || newIndex >= recommendLiveList.size) {
            showSwitchOverlay(
                iconRes = if (direction < 0) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_menu_sort_by_size,
                title = "已是推荐列表${if (direction < 0) "第一个" else "最后一个"}直播间",
                subtitle = "(推荐列表)"
            )
            return
        }

        recommendLiveIndex = newIndex
        val room = recommendLiveList[newIndex]
        switchToLiveRoom(room, "推荐列表")
    }

    /**
     * 切换到指定直播间
     */
    private fun switchToLiveRoom(room: LiveRoom, source: String) {
        liveRoomId = room.roomId
        title = room.title
        bvid = "" // 直播没有bvid

        binding.videoTitle.text = title
        binding.episodeLabel.text = "${room.uname} - 房间号: ${room.roomId}"

        showSwitchOverlay(
            iconRes = android.R.drawable.ic_media_play,
            title = room.title,
            subtitle = "$source · ${room.uname} · ${room.getFormattedOnline()}人观看"
        )

        Log.d("PlayerFragment", "切换直播间: ${room.roomId} - ${room.title}")
        loadLiveStream(room.roomId)
    }

    private fun loadFollowLiveRooms() {
        lifecycleScope.launch {
            showSwitchOverlay(
                iconRes = android.R.drawable.ic_menu_rotate,
                title = "正在加载关注列表...",
                subtitle = ""
            )
            val rooms = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getFollowLiveRooms()
            }
            if (rooms.isNotEmpty()) {
                followLiveList = rooms
                followLiveIndex = 0
                hideSwitchOverlay()
                switchFollowLiveRoom(1)
            } else {
                showSwitchOverlay(
                    iconRes = android.R.drawable.ic_dialog_alert,
                    title = "关注列表为空或未登录",
                    subtitle = "请先在B站APP关注一些主播"
                )
            }
        }
    }

    private fun loadRecommendLiveRooms() {
        lifecycleScope.launch {
            showSwitchOverlay(
                iconRes = android.R.drawable.ic_menu_rotate,
                title = "正在加载推荐列表...",
                subtitle = ""
            )
            val rooms = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getRecommendLiveRooms()
            }
            if (rooms.isNotEmpty()) {
                recommendLiveList = rooms
                recommendLiveIndex = 0
                hideSwitchOverlay()
                switchRecommendLiveRoom(1)
            } else {
                showSwitchOverlay(
                    iconRes = android.R.drawable.ic_dialog_alert,
                    title = "推荐列表加载失败",
                    subtitle = "请稍后重试"
                )
            }
        }
    }

    // ==================== 加载逻辑 ====================

    private fun loadVideo() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                Log.d("PlayerFragment", "加载视频: $bvid")

                val info = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getVideoInfo(bvid)
                }

                if (info == null) {
                    Toast.makeText(requireContext(), "获取视频信息失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                videoDetail = info
                currentAid = info.aid

                // 如果没有指定cid，使用视频默认cid
                if (currentCid == 0L) {
                    currentCid = info.cid
                }

                // 设置分集列表
                episodeList = info.pages
                currentEpisodeIndex = info.getPageIndex(currentCid)

                binding.videoTitle.text = info.title
                title = info.title

                updateEpisodeUI()

                // 获取播放地址
                val videoUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getPlayUrl(
                        info.aid,
                        currentCid,
                        SettingsService.defaultQuality
                    )
                }

                if (videoUrl == null) {
                    Toast.makeText(requireContext(), "获取播放地址失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                playUrl(videoUrl)

                // 保存播放记录
                savePlaybackRecord()

            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载视频异常", e)
                Toast.makeText(requireContext(), "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadEpisodePlayUrl(cid: Long) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                val videoUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getPlayUrl(
                        currentAid,
                        cid,
                        SettingsService.defaultQuality
                    )
                }

                if (videoUrl == null) {
                    Toast.makeText(requireContext(), "获取分集播放地址失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                playUrl(videoUrl)
                savePlaybackRecord()

            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载分集异常", e)
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadLiveRoom() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                Log.d("PlayerFragment", "加载直播间: $liveRoomId")

                // 预加载直播列表
                launch { loadLiveListsInBackground() }

                loadLiveStream(liveRoomId)
            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载直播异常", e)
                Toast.makeText(requireContext(), "加载直播失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadLiveStream(roomId: Long) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                val streamUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getLiveStreamUrl(roomId)
                }

                if (streamUrl == null) {
                    Toast.makeText(requireContext(), "直播间未开播或获取流失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                Log.d("PlayerFragment", "直播流地址: ${streamUrl.take(80)}...")
                playUrl(streamUrl)

                // 保存播放记录
                SettingsService.saveLastPlayedVideo(
                    bvid = "",
                    title = title,
                    cover = "",
                    cid = 0,
                    progress = 0,
                    isLive = true,
                    roomId = roomId
                )

            } catch (e: Exception) {
                Log.e("PlayerFragment", "加载直播流异常", e)
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    /**
     * 后台预加载直播列表
     */
    private suspend fun loadLiveListsInBackground() {
        withContext(Dispatchers.IO) {
            try {
                val follow = BilibiliApiService.getInstance().getFollowLiveRooms()
                if (follow.isNotEmpty()) {
                    followLiveList = follow
                    followLiveIndex = follow.indexOfFirst { it.roomId == liveRoomId }.coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Log.w("PlayerFragment", "预加载关注列表失败", e)
            }

            try {
                val recommend = BilibiliApiService.getInstance().getRecommendLiveRooms()
                if (recommend.isNotEmpty()) {
                    recommendLiveList = recommend
                    recommendLiveIndex = recommend.indexOfFirst { it.roomId == liveRoomId }.coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Log.w("PlayerFragment", "预加载推荐列表失败", e)
            }
        }
    }

    private fun playUrl(url: String) {
        player?.let {
            it.stop()
            val mediaItem = MediaItem.fromUri(url)
            it.setMediaItem(mediaItem)
            it.prepare()
            it.playWhenReady = true
        }
    }

    // ==================== UI 更新 ====================

    private fun updateEpisodeUI() {
        if (episodeList.size > 1) {
            val episode = episodeList.getOrNull(currentEpisodeIndex)
            binding.episodeIndicator.visibility = View.VISIBLE
            binding.episodeLabel.text = if (episode != null) {
                "P${episode.page} ${episode.part}  (${currentEpisodeIndex + 1}/${episodeList.size})"
            } else {
                "${currentEpisodeIndex + 1}/${episodeList.size}"
            }
        } else {
            binding.episodeIndicator.visibility = if (isLiveMode) View.VISIBLE else View.GONE
        }
    }

    private fun showSwitchOverlay(iconRes: Int, titleText: String, subtitle: String) {
        binding.switchOverlay.visibility = View.VISIBLE
        binding.switchIcon.setImageResource(iconRes)
        binding.switchTitle.text = titleText
        binding.switchSubtitle.text = subtitle

        hideSwitchOverlayRunnable?.let { handler.removeCallbacks(it) }
        hideSwitchOverlayRunnable = Runnable {
            binding.switchOverlay.visibility = View.GONE
        }
        handler.postDelayed(hideSwitchOverlayRunnable!!, 2000)
    }

    private fun hideSwitchOverlay() {
        binding.switchOverlay.visibility = View.GONE
        hideSwitchOverlayRunnable?.let { handler.removeCallbacks(it) }
    }

    // ==================== 播放记录 ====================

    private fun savePlaybackRecord() {
        if (isLiveMode) return

        SettingsService.saveLastPlayedVideo(
            bvid = bvid,
            title = title,
            cover = coverUrl,
            cid = currentCid,
            progress = 0 // 进入时进度为0，退出时在 savePlaybackProgress 保存
        )
    }

    private fun savePlaybackProgress() {
        if (isLiveMode) return
        val currentPosition = player?.currentPosition ?: 0L
        val duration = player?.duration ?: 0L

        if (currentPosition > 0 && duration > 0) {
            SettingsService.lastPlayedProgress = currentPosition
        }
    }

    // ==================== 生命周期 ====================

    override fun onPause() {
        super.onPause()
        savePlaybackProgress()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        savePlaybackProgress()
        loadJob?.cancel()
        hideSwitchOverlayRunnable?.let { handler.removeCallbacks(it) }
        hideHintRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        danmakuView = null
        _binding = null
    }
}
```

---

## 修改总结

| 问题 | 根因 | 修复方式 |
|------|------|----------|
| **二维码登录失败** | `TokenInfo` 模型中 `mid` 放在了嵌套的 `TokenInfoDetail` 里，但B站API直接在 `token_info` 下返回 `mid`，导致 `tokenInfo.tokenInfo?.mid` 永远为 `null`，用户信息获取失败，登录状态未持久化 | 1. 在 `TokenInfo` 第一层增加 `mid` 字段；2. 重写 `LoginFragment.onLoginSuccess`，**优先通过 Cookie 调用 `/x/web-interface/nav` 获取用户信息**（最可靠），降级方案才用 `tokenInfo.mid`，最终降级从 Cookie 提取 `DedeUserID`；3. 重写 `AuthService` 基于 Cookie 持久化登录状态 |
| **自动播放上次视频** | 无此功能 | 1. `SettingsService` 增加 `autoPlayLastVideo` 开关及 6 个持久化字段；2. `fragment_settings.xml` 增加带说明文字的 Switch；3. `SettingsFragment` 绑定开关（关闭时清除记录）；4. `HomeFragment.loadContent` 完成后检查设置，满足条件自动跳转播放器 |
| **方向键切换逻辑** | 无此功能 | 1. 在 `PlayerView` 上设置 `setOnKeyListener` 拦截 D-pad 四键（返回 `true` 阻止 ExoPlayer 默认快进快退）；2. **点播模式**：左右键切换 `pages` 分集列表，上下键切换 `categoryVideoList` 分类视频列表；3. **直播模式**：左右键切换关注主播列表（`/xlive/web-ucenter/v1/xfetter/room_list`），上下键切换推荐直播间列表（`/xlive/web-interface/v1/second/getList`）；4. 新增 `Episode`/`LiveRoom`/`VideoDetail` 模型；5. `BilibiliApiService` 增加三个直播 API；6. 600ms 防抖 + 居中浮层切换提示 |

    // ==================== 生命周期 ====================

    override fun onPause() {
        super.onPause()
        savePlaybackProgress()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        savePlaybackProgress()
        loadJob?.cancel()
        hideSwitchOverlayRunnable?.let { handler.removeCallbacks(it) }
        hideHintRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        danmakuView = null
        _binding = null
    }
}
```

---

## 修改总结

| 问题 | 根因 | 修复方式 |
|------|------|----------|
| **二维码登录失败** | `TokenInfo` 模型中 `mid` 放在了嵌套的 `TokenInfoDetail` 里，但B站API直接在 `token_info` 下返回 `mid`，导致 `tokenInfo.tokenInfo?.mid` 永远为 `null`，用户信息获取失败，登录状态未持久化 | 1. 在 `TokenInfo` 第一层增加 `mid` 字段；2. 重写 `LoginFragment.onLoginSuccess`，**优先通过 Cookie 调用 `/x/web-interface/nav` 获取用户信息**（最可靠），降级方案才用 `tokenInfo.mid`，最终降级从 Cookie 提取 `DedeUserID`；3. 重写 `AuthService` 基于 Cookie 持久化登录状态 |
| **自动播放上次视频** | 无此功能 | 1. `SettingsService` 增加 `autoPlayLastVideo` 开关及 6 个持久化字段；2. `fragment_settings.xml` 增加带说明文字的 Switch；3. `SettingsFragment` 绑定开关（关闭时清除记录）；4. `HomeFragment.loadContent` 完成后检查设置，满足条件自动跳转播放器 |
| **方向键切换逻辑** | 无此功能 | 1. 在 `PlayerView` 上设置 `setOnKeyListener` 拦截 D-pad 四键（返回 `true` 阻止 ExoPlayer 默认快进快退）；2. **点播模式**：左右键切换 `pages` 分集列表，上下键切换 `categoryVideoList` 分类视频列表；3. **直播模式**：左右键切换关注主播列表（`/xlive/web-ucenter/v1/xfetter/room_list`），上下键切换推荐直播间列表（`/xlive/web-interface/v1/second/getList`）；4. 新增 `Episode`/`LiveRoom`/`VideoDetail` 模型；5. `BilibiliApiService` 增加三个直播 API；6. 600ms 防抖 + 居中浮层切换提示 |