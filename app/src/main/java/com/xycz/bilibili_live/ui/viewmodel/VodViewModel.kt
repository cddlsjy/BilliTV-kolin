package com.xycz.bilibili_live.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xycz.bilibili_live.data.remote.api.BilibiliApi
import com.xycz.bilibili_live.domain.model.VodEpisode
import com.xycz.bilibili_live.domain.model.VodRecommend
import com.xycz.bilibili_live.domain.model.VodVideo
import com.xycz.bilibili_live.util.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 点播ViewModel
 */
class VodViewModel(
    application: Application,
    private val bvid: String
) : AndroidViewModel(application) {

    private val api: BilibiliApi = NetworkModule.bilibiliApi

    private val _uiState = MutableStateFlow(VodUiState())
    val uiState: StateFlow<VodUiState> = _uiState.asStateFlow()

    private var currentPageIndex = 0
    private var pagesList = listOf<VodEpisode>()
    private var recommendList = listOf<VodRecommend>()

    init {
        loadVideoDetail()
    }

    /**
     * 加载视频详情
     */
    fun loadVideoDetail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = api.getVideoDetail(bvid)

                if (response.isSuccessful) {
                    val data = response.body()?.data

                    if (data != null) {
                        val video = VodVideo(
                            bvid = data.bvid ?: "",
                            title = data.title ?: "",
                            cover = data.cover ?: "",
                            description = data.desc ?: "",
                            duration = data.duration ?: 0,
                            ownerMid = data.owner?.mid ?: 0,
                            ownerName = data.owner?.name ?: "",
                            ownerFace = data.owner?.face ?: "",
                            pages = data.pages?.map {
                                VodEpisode(
                                    cid = it.cid ?: 0,
                                    page = it.page ?: 0,
                                    title = it.part ?: "",
                                    duration = it.duration ?: 0,
                                    description = it.desc ?: ""
                                )
                            } ?: emptyList()
                        )

                        pagesList = video.pages
                        _uiState.value = _uiState.value.copy(
                            video = video,
                            currentEpisode = video.pages.firstOrNull(),
                            isLoading = false
                        )

                        // 加载推荐视频
                        loadRecommendVideos()
                        // 加载播放地址
                        if (video.pages.isNotEmpty()) {
                            loadPlayUrl(video.pages.first().cid)
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "视频不存在"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "加载失败: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 加载推荐视频列表
     */
    private fun loadRecommendVideos() {
        viewModelScope.launch {
            try {
                val response = api.getRecommendVideos(bvid)

                if (response.isSuccessful) {
                    val videos = response.body()?.data?.map {
                        VodRecommend(
                            bvid = it.bvid ?: "",
                            title = it.title ?: "",
                            cover = it.pic ?: "",
                            ownerMid = it.owner?.mid ?: 0,
                            ownerName = it.owner?.name ?: "",
                            ownerFace = it.owner?.face ?: "",
                            duration = it.duration ?: 0,
                            shortLink = it.short_link ?: ""
                        )
                    } ?: emptyList()

                    recommendList = videos
                    _uiState.value = _uiState.value.copy(recommendVideos = videos)
                }
            } catch (e: Exception) {
                // 忽略推荐加载错误
            }
        }
    }

    /**
     * 加载播放地址
     */
    private fun loadPlayUrl(cid: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPlayUrl = true)

            try {
                val response = api.getVideoPlayUrl(bvid, cid)

                if (response.isSuccessful) {
                    val playUrl = response.body()?.data?.durl?.firstOrNull()?.url
                    _uiState.value = _uiState.value.copy(
                        playUrl = playUrl,
                        isLoadingPlayUrl = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPlayUrl = false,
                        error = "获取播放地址失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPlayUrl = false,
                    error = "网络错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 切换分P
     */
    fun switchEpisode(delta: Int) {
        if (pagesList.isEmpty()) return

        val newIndex = (currentPageIndex + delta).coerceIn(0, pagesList.size - 1)

        if (newIndex != currentPageIndex) {
            currentPageIndex = newIndex
            val newEpisode = pagesList[newIndex]
            _uiState.value = _uiState.value.copy(currentEpisode = newEpisode)
            loadPlayUrl(newEpisode.cid)
        }
    }

    /**
     * 切换推荐视频
     */
    fun switchRecommend(delta: Int) {
        if (recommendList.isEmpty()) return

        val currentIndex = recommendList.indexOfFirst { it.bvid == _uiState.value.video?.bvid }
        val startIndex = if (currentIndex >= 0) currentIndex else 0
        val newIndex = (startIndex + delta).coerceIn(0, recommendList.size - 1)

        if (newIndex != startIndex) {
            val newVideo = recommendList[newIndex]
            loadVideoByBvid(newVideo.bvid)
        }
    }

    /**
     * 根据bvid加载视频
     */
    private fun loadVideoByBvid(newBvid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val response = api.getVideoDetail(newBvid)

                if (response.isSuccessful) {
                    val data = response.body()?.data

                    if (data != null) {
                        val video = VodVideo(
                            bvid = data.bvid ?: "",
                            title = data.title ?: "",
                            cover = data.cover ?: "",
                            description = data.desc ?: "",
                            duration = data.duration ?: 0,
                            ownerMid = data.owner?.mid ?: 0,
                            ownerName = data.owner?.name ?: "",
                            ownerFace = data.owner?.face ?: "",
                            pages = data.pages?.map {
                                VodEpisode(
                                    cid = it.cid ?: 0,
                                    page = it.page ?: 0,
                                    title = it.part ?: "",
                                    duration = it.duration ?: 0,
                                    description = it.desc ?: ""
                                )
                            } ?: emptyList()
                        )

                        pagesList = video.pages
                        currentPageIndex = 0

                        _uiState.value = _uiState.value.copy(
                            video = video,
                            currentEpisode = video.pages.firstOrNull(),
                            isLoading = false
                        )

                        // 加载新的推荐视频
                        loadRecommendVideos()
                        // 加载播放地址
                        if (video.pages.isNotEmpty()) {
                            loadPlayUrl(video.pages.first().cid)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 保存播放进度
     */
    fun saveProgress(progress: Long) {
        _uiState.value = _uiState.value.copy(progress = progress)
    }
}

/**
 * 点播UI状态
 */
data class VodUiState(
    val video: VodVideo? = null,
    val currentEpisode: VodEpisode? = null,
    val playUrl: String? = null,
    val recommendVideos: List<VodRecommend> = emptyList(),
    val progress: Long = 0,
    val isLoading: Boolean = false,
    val isLoadingPlayUrl: Boolean = false,
    val error: String? = null
)
