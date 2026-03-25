package com.bili.tv.bili_tv_app.plugins

import com.bili.tv.bili_tv_app.core.plugin.Plugin
import com.bili.tv.bili_tv_app.core.plugin.PluginManager
import com.bili.tv.bili_tv_app.core.plugin.SponsorSegment
import com.bili.tv.bili_tv_app.services.SettingsService

class SponsorBlockPlugin : Plugin {

    private var enabled = true

    companion object {
        const val ID = "sponsor_block"
        const val NAME = "赞助跳过"
        const val DESCRIPTION = "自动跳过视频中的赞助内容"
    }

    override fun getId(): String = ID

    override fun getName(): String = NAME

    override fun getDescription(): String = DESCRIPTION

    override fun isEnabled(): Boolean = enabled && SettingsService.sponsorBlockEnabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun getSegments(videoId: String): List<SponsorSegment> {
        // In a real implementation, this would fetch segments from sponsorblock API
        // For now, return empty list
        return emptyList()
    }

    fun shouldSkip(videoId: String, currentTime: Long): Boolean {
        val segments = getSegments(videoId)
        return segments.any { segment ->
            currentTime >= segment.startTime && currentTime <= segment.endTime
        }
    }
}
