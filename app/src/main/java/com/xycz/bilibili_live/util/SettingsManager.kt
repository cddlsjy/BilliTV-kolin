package com.xycz.bilibili_live.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 设置管理器
 * 存储普通应用设置
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bilibili_live_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DANMAKU_ENABLED = "danmaku_enabled"
        private const val KEY_DANMAKU_OPACITY = "danmaku_opacity"
        private const val KEY_DANMAKU_SIZE = "danmaku_size"
        private const val KEY_QUALITY_LEVEL = "quality_level"
        private const val KEY_BACKGROUND_PLAY = "background_play"
        private const val KEY_SHIELD_WORDS = "shield_words"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_AUTO_RESUME_LAST = "auto_resume_last"
    }

    // ==================== 弹幕设置 ====================

    var danmakuEnabled: Boolean
        get() = prefs.getBoolean(KEY_DANMAKU_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DANMAKU_ENABLED, value).apply()

    var danmakuOpacity: Float
        get() = prefs.getFloat(KEY_DANMAKU_OPACITY, 1f)
        set(value) = prefs.edit().putFloat(KEY_DANMAKU_OPACITY, value).apply()

    var danmakuSize: Float
        get() = prefs.getFloat(KEY_DANMAKU_SIZE, 16f)
        set(value) = prefs.edit().putFloat(KEY_DANMAKU_SIZE, value).apply()

    // ==================== 播放设置 ====================

    var qualityLevel: Int
        get() = prefs.getInt(KEY_QUALITY_LEVEL, 2) // 2=原画
        set(value) = prefs.edit().putInt(KEY_QUALITY_LEVEL, value).apply()

    var backgroundPlay: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_PLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_PLAY, value).apply()

    // ==================== 屏蔽词 ====================

    var shieldWords: Set<String>
        get() = prefs.getStringSet(KEY_SHIELD_WORDS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_SHIELD_WORDS, value).apply()

    fun addShieldWord(word: String) {
        val current = shieldWords.toMutableSet()
        current.add(word)
        shieldWords = current
    }

    fun removeShieldWord(word: String) {
        val current = shieldWords.toMutableSet()
        current.remove(word)
        shieldWords = current
    }

    fun isShielded(message: String): Boolean {
        return shieldWords.any { message.contains(it, ignoreCase = true) }
    }

    // ==================== 主题设置 ====================

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    // ==================== 自动播放设置 ====================

    var autoResumeLast: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESUME_LAST, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RESUME_LAST, value).apply()

    // ==================== Cookie管理 ====================

    fun getCookie(): String = SecureSettingsManager.getCookie()
    fun setCookie(cookie: String) = SecureSettingsManager.saveCookie(cookie)
    fun clearCookie() = SecureSettingsManager.clearCookie()
    fun isLoggedIn(): Boolean = SecureSettingsManager.isLoggedIn()
}
