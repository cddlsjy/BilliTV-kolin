package com.bili.tv.bili_tv_app.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bili.tv.bili_tv_app.models.LoginQRCode
import com.bili.tv.bili_tv_app.models.LoginStatusResponse
import com.bili.tv.bili_tv_app.models.User
import com.bili.tv.bili_tv_app.services.api.AuthApi
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

object AuthService {

    private lateinit var dataStore: DataStore<Preferences>
    private val gson = Gson()

    // Keys
    private object Keys {
        val USER_INFO = stringPreferencesKey("user_info")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val TOKEN_EXPIRY = longPreferencesKey("token_expiry")
        val COOKIES = stringPreferencesKey("cookies")
    }

    // Current user
    var currentUser: User? = null
        private set

    val isLoggedIn: Boolean
        get() = currentUser != null && getAccessToken().isNotEmpty()

    suspend fun init(context: Context? = null) {
        if (!::dataStore.isInitialized && context != null) {
            dataStore = context.authDataStore
        }
        loadUserFromStorage()
    }

    private fun loadUserFromStorage() {
        runBlocking {
            try {
                val userInfoJson = dataStore.data.first()[Keys.USER_INFO]
                if (!userInfoJson.isNullOrEmpty()) {
                    currentUser = gson.fromJson(userInfoJson, User::class.java)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

    suspend fun saveLoginInfo(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
        cookies: String,
        user: User
    ) {
        val expiryTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn)

        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.TOKEN_EXPIRY] = expiryTime
            prefs[Keys.COOKIES] = cookies
            prefs[Keys.USER_INFO] = gson.toJson(user)
        }

        currentUser = user
    }

    suspend fun logout() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.TOKEN_EXPIRY)
            prefs.remove(Keys.COOKIES)
            prefs.remove(Keys.USER_INFO)
        }

        currentUser = null
    }

    fun getAccessToken(): String = runBlocking {
        try {
            dataStore.data.first()[Keys.ACCESS_TOKEN] ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getRefreshToken(): String = runBlocking {
        try {
            dataStore.data.first()[Keys.REFRESH_TOKEN] ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getCookies(): String = runBlocking {
        try {
            dataStore.data.first()[Keys.COOKIES] ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun refreshTokenIfNeeded(): Boolean {
        val expiry = runBlocking {
            try {
                dataStore.data.first()[Keys.TOKEN_EXPIRY] ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

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

    suspend fun updateUserInfo(user: User) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_INFO] = gson.toJson(user)
        }
        currentUser = user
    }
}
