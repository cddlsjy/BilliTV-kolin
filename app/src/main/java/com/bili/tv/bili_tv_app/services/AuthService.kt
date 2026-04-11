package com.bili.tv.bili_tv_app.services

import android.content.Context
import android.content.SharedPreferences
import com.bili.tv.bili_tv_app.models.LoginQRCode
import com.bili.tv.bili_tv_app.models.LoginStatusResponse
import com.bili.tv.bili_tv_app.models.User
import com.bili.tv.bili_tv_app.services.api.AuthApi
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

fun SharedPreferences.edit(block: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    editor.block()
    editor.apply()
}

object AuthService {

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    // Keys
    private object Keys {
        const val USER_INFO = "user_info"
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val TOKEN_EXPIRY = "token_expiry"
        const val COOKIES = "cookies"
        const val LOGGED_IN = "logged_in"
    }

    // Current user
    var currentUser: User? = null
        private set

    var cookies: String = ""
        private set

    var accessToken: String = ""
        private set

    val isLoggedIn: Boolean
        get() = sharedPreferences.getBoolean(Keys.LOGGED_IN, false) && currentUser != null

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        restoreLoginState()
    }

    fun restoreLoginState() {
        try {
            val userInfoJson = sharedPreferences.getString(Keys.USER_INFO, "")
            if (!userInfoJson.isNullOrEmpty()) {
                currentUser = gson.fromJson(userInfoJson, User::class.java)
            }
            cookies = sharedPreferences.getString(Keys.COOKIES, "") ?: ""
            accessToken = sharedPreferences.getString(Keys.ACCESS_TOKEN, "") ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveLoginInfo(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
        cookies: String,
        user: User
    ) {
        val expiryTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn)

        sharedPreferences.edit {
            putString(Keys.ACCESS_TOKEN, accessToken)
            putString(Keys.REFRESH_TOKEN, refreshToken)
            putLong(Keys.TOKEN_EXPIRY, expiryTime)
            putString(Keys.COOKIES, cookies)
            putString(Keys.USER_INFO, gson.toJson(user))
            putBoolean(Keys.LOGGED_IN, true)
        }

        currentUser = user
        this.cookies = cookies
        this.accessToken = accessToken
    }

    fun getCookieValue(cookieName: String): String {
        return cookies.split(";")
            .find { it.trim().startsWith("$cookieName=") }
            ?.substringAfter("=")
            ?.trim() ?: ""
    }

    fun getDedeUserId(): String {
        return getCookieValue("DedeUserID")
    }

    fun clearLogin() {
        sharedPreferences.edit {
            remove(Keys.ACCESS_TOKEN)
            remove(Keys.REFRESH_TOKEN)
            remove(Keys.TOKEN_EXPIRY)
            remove(Keys.COOKIES)
            remove(Keys.USER_INFO)
            remove(Keys.LOGGED_IN)
        }

        currentUser = null
        cookies = ""
        accessToken = ""
    }

    suspend fun getQRCode(): LoginQRCode? {
        return try {
            val response = AuthApi.getInstance().getQRCode()
            if (response.code == 0 && response.data != null) {
                LoginQRCode(url = response.data.url, qrcodeKey = response.data.qrcodeKey)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun checkQRCodeStatus(qrcodeKey: String): LoginStatusResponse? {
        return try {
            val response = AuthApi.getInstance().checkQRCodeStatus(qrcodeKey)
            response
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getRefreshToken(): String {
        return sharedPreferences.getString(Keys.REFRESH_TOKEN, "") ?: ""
    }

    suspend fun refreshTokenIfNeeded(): Boolean {
        val expiry = sharedPreferences.getLong(Keys.TOKEN_EXPIRY, 0L)

        // Refresh if token expires within 1 hour
        if (System.currentTimeMillis() > expiry - TimeUnit.HOURS.toMillis(1)) {
            return refreshToken()
        }
        return true
    }

    private suspend fun refreshToken(): Boolean {
        return try {
            val refreshToken = getRefreshToken()
            if (refreshToken.isEmpty()) return false

            // In real implementation, call refresh token API
            // For now, return false to indicate refresh failed
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun updateUserInfo(user: User) {
        sharedPreferences.edit {
            putString(Keys.USER_INFO, gson.toJson(user))
        }
        currentUser = user
    }
}
