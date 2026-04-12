package com.xycz.bilibili_live.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xycz.bilibili_live.data.remote.api.BilibiliApi
import com.xycz.bilibili_live.data.remote.dto.RoomDto
import com.xycz.bilibili_live.domain.model.LiveRoomItem
import com.xycz.bilibili_live.util.WbiSigner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页ViewModel
 */
class HomeViewModel(private val api: BilibiliApi) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecommendRooms()
    }

    /**
     * 加载推荐直播
     */
    fun loadRecommendRooms(page: Int = 1) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val wRid = WbiSigner.generateWwebid()
                val signedParams = WbiSigner.getSignedParams(
                    mapOf(
                        "platform" to "web",
                        "sort" to "online",
                        "page_size" to "30",
                        "page" to page.toString(),
                        "w_rid" to wRid,
                        "wts" to WbiSigner.getWts().toString()
                    )
                )

                val response = api.getRecommendRooms(
                    page = page,
                    wRid = signedParams["w_rid"] ?: wRid,
                    wts = signedParams["wts"]?.toLongOrNull() ?: WbiSigner.getWts()
                )

                if (response.isSuccessful) {
                    val rooms = response.body()?.data?.list?.map { it.toLiveRoomItem() } ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        rooms = if (page == 1) rooms else _uiState.value.rooms + rooms,
                        currentPage = page,
                        hasMore = response.body()?.data?.hasMore == 1
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "加载失败: ${response.message()}"
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
     * 搜索
     */
    fun search(keyword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                searchQuery = keyword,
                isLoading = true,
                error = null
            )

            try {
                val response = api.searchRooms(keyword = keyword, page = 1)

                if (response.isSuccessful) {
                    val rooms = response.body()?.data?.items?.map {
                        LiveRoomItem(
                            roomId = it.roomid?.toString() ?: "",
                            title = it.title ?: "",
                            cover = it.cover ?: "",
                            userName = it.owner?.name ?: "",
                            online = it.online ?: 0
                        )
                    } ?: emptyList()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        rooms = rooms
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "搜索失败"
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
     * 更新搜索词
     */
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /**
     * 加载更多
     */
    fun loadMore() {
        if (_uiState.value.hasMore && !_uiState.value.isLoading) {
            loadRecommendRooms(_uiState.value.currentPage + 1)
        }
    }

    /**
     * 下拉刷新
     */
    fun refresh() {
        loadRecommendRooms(1)
    }
}

/**
 * 首页UI状态
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val rooms: List<LiveRoomItem> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = true
)

/**
 * DTO转Domain
 */
fun RoomDto.toLiveRoomItem() = LiveRoomItem(
    roomId = roomidStr ?: roomid?.toString() ?: "",
    title = title ?: "",
    cover = cover ?: user_cover ?: "",
    userName = uname ?: "",
    online = online ?: 0
)
