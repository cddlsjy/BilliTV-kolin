package com.bili.tv.bili_tv_app.plugins

import com.bili.tv.bili_tv_app.core.plugin.Plugin
import com.bili.tv.bili_tv_app.services.api.DanmakuSegment
import com.bili.tv.bili_tv_app.services.SettingsService

class AdFilterPlugin : Plugin {

    private var enabled = true

    companion object {
        const val ID = "ad_filter"
        const val NAME = "弹幕广告过滤"
        const val DESCRIPTION = "过滤弹幕中的广告内容"
    }

    override fun getId(): String = ID

    override fun getName(): String = NAME

    override fun getDescription(): String = DESCRIPTION

    override fun isEnabled(): Boolean = enabled && SettingsService.adFilterEnabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun filterDanmaku(danmakuList: List<DanmakuSegment>): List<DanmakuSegment> {
        if (!isEnabled()) return danmakuList

        // Common ad keywords in Chinese
        val adKeywords = listOf(
            "广告", "推广", "代言", "赞助", "博雅",
            "招代理", "加微信", "扫码", "加QQ",
            "淘宝", "天猫", "拼多多", "京东",
            "抖音", "快手", "火山", "微视",
            "游戏", "礼包", "礼包码", "兑换码"
        )

        return danmakuList.filter { danmaku ->
            val text = danmaku.m
            !adKeywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
        }
    }
}
