package com.bili.tv.bili_tv_app.core.plugin

import com.bili.tv.bili_tv_app.services.api.DanmakuSegment
import com.bili.tv.bili_tv_app.plugins.AdFilterPlugin
import com.bili.tv.bili_tv_app.plugins.DanmakuEnhancePlugin
import com.bili.tv.bili_tv_app.plugins.SponsorBlockPlugin

class PluginManager {

    private val plugins = mutableListOf<Plugin>()

    companion object {
        @Volatile
        private var instance: PluginManager? = null

        fun getInstance(): PluginManager {
            return instance ?: synchronized(this) {
                instance ?: PluginManager().also { instance = it }
            }
        }
    }

    suspend fun init() {
        // Initialize plugins
    }

    fun register(plugin: Plugin) {
        plugins.add(plugin)
    }

    fun unregister(plugin: Plugin) {
        plugins.remove(plugin)
    }

    fun getPlugins(): List<Plugin> = plugins.toList()

    // Process danmaku - filter and enhance
    fun processDanmaku(danmakuList: List<DanmakuSegment>): List<DanmakuSegment> {
        var result = danmakuList

        plugins.filterIsInstance<DanmakuEnhancePlugin>().forEach { plugin ->
            if (plugin.isEnabled()) {
                result = plugin.processDanmaku(result)
            }
        }

        plugins.filterIsInstance<AdFilterPlugin>().forEach { plugin ->
            if (plugin.isEnabled()) {
                result = plugin.filterDanmaku(result)
            }
        }

        return result
    }

    // Get sponsor block segments for video
    fun getSponsorSegments(videoId: String): List<SponsorSegment> {
        val segments = mutableListOf<SponsorSegment>()
        plugins.filterIsInstance<SponsorBlockPlugin>().forEach { plugin ->
            if (plugin.isEnabled()) {
                segments.addAll(plugin.getSegments(videoId))
            }
        }
        return segments
    }

    // Should skip segment (for sponsor block)
    fun shouldSkipSegment(videoId: String, currentTime: Long): Boolean {
        plugins.filterIsInstance<SponsorBlockPlugin>().forEach { plugin ->
            if (plugin.isEnabled() && plugin.shouldSkip(videoId, currentTime)) {
                return true
            }
        }
        return false
    }
}

data class SponsorSegment(
    val videoId: String,
    val startTime: Long, // milliseconds
    val endTime: Long,
    val category: String = "sponsor"
)
