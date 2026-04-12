package com.xycz.bilibili_live.util

import com.xycz.bilibili_live.domain.model.VodEpisode
import com.xycz.bilibili_live.domain.model.VodRecommend

/**
 * 播放列表管理器
 * 统一管理直播和点播的播放模式切换
 */
object PlaylistManager {

    // 当前播放模式
    enum class PlayMode {
        LIVE,   // 直播模式
        VOD     // 点播模式
    }

    private var currentMode: PlayMode = PlayMode.LIVE

    // 点播相关数据
    private var currentBvid: String = ""
    private var episodes: List<VodEpisode> = emptyList()
    private var currentEpisodeIndex: Int = 0
    private var recommends: List<VodRecommend> = emptyList()
    private var currentRecommendIndex: Int = 0

    // 直播相关数据
    private var followedRoomIds: List<String> = emptyList()
    private var recommendRoomIds: List<String> = emptyList()
    private var currentLiveIndex: Int = 0

    /**
     * 设置当前播放模式
     */
    fun setPlayMode(mode: PlayMode) {
        currentMode = mode
    }

    /**
     * 获取当前播放模式
     */
    fun getPlayMode(): PlayMode = currentMode

    /**
     * ==================== 点播模式操作 ====================
     */

    /**
     * 设置点播数据
     */
    fun setVodData(
        bvid: String,
        episodes: List<VodEpisode>,
        recommends: List<VodRecommend>
    ) {
        currentBvid = bvid
        this.episodes = episodes
        this.recommends = recommends
        currentEpisodeIndex = 0
        currentRecommendIndex = recommends.indexOfFirst { it.bvid == bvid }.takeIf { it >= 0 } ?: 0
    }

    /**
     * 获取当前bvid
     */
    fun getCurrentBvid(): String = currentBvid

    /**
     * 切换到上一个分P
     */
    fun previousEpisode(): Boolean {
        if (currentEpisodeIndex > 0) {
            currentEpisodeIndex--
            return true
        }
        return false
    }

    /**
     * 切换到下一个分P
     */
    fun nextEpisode(): Boolean {
        if (currentEpisodeIndex < episodes.size - 1) {
            currentEpisodeIndex++
            return true
        }
        return false
    }

    /**
     * 获取当前分P
     */
    fun getCurrentEpisode(): VodEpisode? {
        return episodes.getOrNull(currentEpisodeIndex)
    }

    /**
     * 获取当前分P索引
     */
    fun getCurrentEpisodeIndex(): Int = currentEpisodeIndex

    /**
     * 获取分P列表大小
     */
    fun getEpisodesCount(): Int = episodes.size

    /**
     * 切换到上一个推荐视频
     */
    fun previousRecommend(): Boolean {
        if (currentRecommendIndex > 0) {
            currentRecommendIndex--
            val newBvid = recommends.getOrNull(currentRecommendIndex)?.bvid
            if (newBvid != null) {
                currentBvid = newBvid
                return true
            }
        }
        return false
    }

    /**
     * 切换到下一个推荐视频
     */
    fun nextRecommend(): Boolean {
        if (currentRecommendIndex < recommends.size - 1) {
            currentRecommendIndex++
            val newBvid = recommends.getOrNull(currentRecommendIndex)?.bvid
            if (newBvid != null) {
                currentBvid = newBvid
                return true
            }
        }
        return false
    }

    /**
     * 获取当前推荐索引
     */
    fun getCurrentRecommendIndex(): Int = currentRecommendIndex

    /**
     * 获取推荐列表大小
     */
    fun getRecommendsCount(): Int = recommends.size

    /**
     * ==================== 直播模式操作 ====================
     */

    /**
     * 设置直播数据
     */
    fun setLiveData(
        followedRoomIds: List<String>,
        recommendRoomIds: List<String>
    ) {
        this.followedRoomIds = followedRoomIds
        this.recommendRoomIds = recommendRoomIds
        currentLiveIndex = 0
    }

    /**
     * 切换到上一个直播间（关注列表）
     */
    fun previousFollowedRoom(): Boolean {
        if (currentLiveIndex > 0) {
            currentLiveIndex--
            return true
        }
        return false
    }

    /**
     * 切换到下一个直播间（推荐列表）
     */
    fun nextRecommendRoom(): Boolean {
        if (currentLiveIndex < recommendRoomIds.size - 1) {
            currentLiveIndex++
            return true
        }
        return false
    }

    /**
     * 获取当前直播间ID
     */
    fun getCurrentRoomId(): String? {
        return recommendRoomIds.getOrNull(currentLiveIndex)
            ?: followedRoomIds.getOrNull(currentLiveIndex)
    }

    /**
     * 获取关注列表
     */
    fun getFollowedRoomIds(): List<String> = followedRoomIds

    /**
     * 获取推荐列表
     */
    fun getRecommendRoomIds(): List<String> = recommendRoomIds

    /**
     * ==================== 通用操作 ====================
     */

    /**
     * 重置所有状态
     */
    fun reset() {
        currentMode = PlayMode.LIVE
        currentBvid = ""
        episodes = emptyList()
        currentEpisodeIndex = 0
        recommends = emptyList()
        currentRecommendIndex = 0
        followedRoomIds = emptyList()
        recommendRoomIds = emptyList()
        currentLiveIndex = 0
    }
}
