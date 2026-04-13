package com.bili.tv.bili_tv_app.plugins

import com.bili.tv.bili_tv_app.core.plugin.Plugin
import com.bili.tv.bili_tv_app.services.api.DanmakuSegment
import com.bili.tv.bili_tv_app.services.SettingsService

class DanmakuEnhancePlugin : Plugin {

    private var enabled = true

    companion object {
        const val ID = "danmaku_enhance"
        const val NAME = "弹幕增强"
        const val DESCRIPTION = "增强弹幕显示效果，过滤低质量弹幕"
    }

    override fun getId(): String = ID

    override fun getName(): String = NAME

    override fun getDescription(): String = DESCRIPTION

    override fun isEnabled(): Boolean = enabled && SettingsService.danmakuEnhanceEnabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun processDanmaku(danmakuList: List<DanmakuSegment>): List<DanmakuSegment> {
        if (!isEnabled()) return danmakuList

        return danmakuList.map { danmaku ->
            // Process danmaku - could add color, speed, position adjustments
            danmaku.copy()
        }
    }

    // Check if danmaku should be filtered (low quality)
    fun isLowQuality(danmaku: DanmakuSegment): Boolean {
        val text = danmaku.m

        // Filter very short messages
        if (text.length < 2) return true

        // Filter messages that are too long
        if (text.length > 20) return true

        // Filter duplicate characters (spam)
        val uniqueChars = text.toSet()
        if (text.length > 5 && uniqueChars.size < 3) return true

        return false
    }
}
