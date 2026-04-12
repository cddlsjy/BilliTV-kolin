package com.xycz.bilibili_live.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.xycz.bilibili_live.util.SecureSettingsManager
import com.xycz.bilibili_live.util.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.value = SettingsUiState(
            danmakuEnabled = settingsManager.danmakuEnabled,
            danmakuOpacity = settingsManager.danmakuOpacity,
            danmakuSize = settingsManager.danmakuSize,
            qualityLevel = settingsManager.qualityLevel,
            backgroundPlay = settingsManager.backgroundPlay,
            shieldWords = settingsManager.shieldWords,
            isLoggedIn = SecureSettingsManager.isLoggedIn(),
            userName = if (SecureSettingsManager.isLoggedIn()) "已登录" else "未登录",
            autoResumeLast = settingsManager.autoResumeLast
        )
    }

    /**
     * 切换弹幕开关
     */
    fun toggleDanmaku(enabled: Boolean) {
        settingsManager.danmakuEnabled = enabled
        _uiState.value = _uiState.value.copy(danmakuEnabled = enabled)
    }

    /**
     * 设置弹幕透明度
     */
    fun setDanmakuOpacity(opacity: Float) {
        settingsManager.danmakuOpacity = opacity
        _uiState.value = _uiState.value.copy(danmakuOpacity = opacity)
    }

    /**
     * 设置弹幕大小
     */
    fun setDanmakuSize(size: Float) {
        settingsManager.danmakuSize = size
        _uiState.value = _uiState.value.copy(danmakuSize = size)
    }

    /**
     * 设置默认画质
     */
    fun setQualityLevel(level: Int) {
        settingsManager.qualityLevel = level
        _uiState.value = _uiState.value.copy(qualityLevel = level)
    }

    /**
     * 设置后台播放
     */
    fun setBackgroundPlay(enabled: Boolean) {
        settingsManager.backgroundPlay = enabled
        _uiState.value = _uiState.value.copy(backgroundPlay = enabled)
    }

    /**
     * 添加屏蔽词
     */
    fun addShieldWord(word: String) {
        if (word.isNotBlank()) {
            settingsManager.addShieldWord(word)
            _uiState.value = _uiState.value.copy(shieldWords = settingsManager.shieldWords)
        }
    }

    /**
     * 移除屏蔽词
     */
    fun removeShieldWord(word: String) {
        settingsManager.removeShieldWord(word)
        _uiState.value = _uiState.value.copy(shieldWords = settingsManager.shieldWords)
    }

    /**
     * 设置自动播放上次观看
     */
    fun setAutoResumeLast(enabled: Boolean) {
        settingsManager.autoResumeLast = enabled
        _uiState.value = _uiState.value.copy(autoResumeLast = enabled)
    }

    /**
     * 登出
     */
    fun logout() {
        SecureSettingsManager.clearAll()
        _uiState.value = _uiState.value.copy(
            isLoggedIn = false,
            userName = "未登录"
        )
    }
}

/**
 * 设置UI状态
 */
data class SettingsUiState(
    val danmakuEnabled: Boolean = true,
    val danmakuOpacity: Float = 1f,
    val danmakuSize: Float = 16f,
    val qualityLevel: Int = 2,
    val backgroundPlay: Boolean = false,
    val shieldWords: Set<String> = emptySet(),
    val isLoggedIn: Boolean = false,
    val userName: String = "未登录",
    val autoResumeLast: Boolean = false
)
