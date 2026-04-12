package com.xycz.bilibili_live.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 安全设置管理器
 * 使用EncryptedSharedPreferences存储敏感数据
 */
object SecureSettingsManager {
    private const val PREFS_NAME = "bilibili_live_secure_prefs"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_IMG_KEY = "wbi_img_key"
    private const val KEY_SUB_KEY = "wbi_sub_key"
    private const val KEY_BUVID3 = "buvid3"
    private const val KEY_BUVID4 = "buvid4"
    private const val KEY_UID = "uid"

    private var encryptedPrefs: SharedPreferences? = null

    /**
     * 初始化
     */
    fun init(context: Context) {
        if (encryptedPrefs == null) {
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    // ==================== Cookie ====================

    /**
     * 保存Cookie
     */
    fun saveCookie(cookie: String) {
        encryptedPrefs?.edit()?.putString(KEY_COOKIE, cookie)?.apply()
    }

    /**
     * 获取Cookie
     */
    fun getCookie(): String = encryptedPrefs?.getString(KEY_COOKIE, "") ?: ""

    /**
     * 清除Cookie
     */
    fun clearCookie() {
        encryptedPrefs?.edit()?.remove(KEY_COOKIE)?.apply()
    }

    // ==================== WBI Keys ====================

    /**
     * 保存WBI密钥
     */
    fun saveWbiKeys(imgKey: String, subKey: String) {
        encryptedPrefs?.edit()
            ?.putString(KEY_IMG_KEY, imgKey)
            ?.putString(KEY_SUB_KEY, subKey)
            ?.apply()
    }

    /**
     * 获取WBI密钥
     */
    fun getWbiKeys(): Pair<String, String>? {
        val img = encryptedPrefs?.getString(KEY_IMG_KEY, null) ?: return null
        val sub = encryptedPrefs?.getString(KEY_SUB_KEY, null) ?: return null
        return Pair(img, sub)
    }

    // ==================== Buvid ====================

    /**
     * 保存Buvid
     */
    fun saveBuvid(buvid3: String, buvid4: String) {
        encryptedPrefs?.edit()
            ?.putString(KEY_BUVID3, buvid3)
            ?.putString(KEY_BUVID4, buvid4)
            ?.apply()
    }

    /**
     * 获取Buvid3
     */
    fun getBuvid3(): String = encryptedPrefs?.getString(KEY_BUVID3, "") ?: ""

    /**
     * 获取Buvid4
     */
    fun getBuvid4(): String = encryptedPrefs?.getString(KEY_BUVID4, "") ?: ""

    // ==================== UID ====================

    /**
     * 保存UID
     */
    fun saveUid(uid: Long) {
        encryptedPrefs?.edit()?.putLong(KEY_UID, uid)?.apply()
    }

    /**
     * 获取UID
     */
    fun getUid(): Long = encryptedPrefs?.getLong(KEY_UID, 0L) ?: 0L

    // ==================== CSRF Token ====================

    /**
     * 从Cookie中提取CSRF Token
     */
    fun getCsrf(): String {
        val cookie = getCookie()
        val match = Regex("bili_jct=([^;]+)").find(cookie)
        return match?.groupValues?.get(1) ?: ""
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean = getCookie().isNotEmpty() && getUid() > 0

    // ==================== 清空所有数据 ====================

    /**
     * 清空所有安全数据
     */
    fun clearAll() {
        encryptedPrefs?.edit()?.clear()?.apply()
    }
}
