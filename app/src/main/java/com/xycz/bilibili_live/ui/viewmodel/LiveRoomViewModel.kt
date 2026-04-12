package com.xycz.bilibili_live.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.xycz.bilibili_live.data.local.AppDatabase
import com.xycz.bilibili_live.data.local.entity.FollowUser
import com.xycz.bilibili_live.data.local.entity.History
import com.xycz.bilibili_live.data.remote.api.BilibiliApi
import com.xycz.bilibili_live.domain.model.DanmakuMessage
import com.xycz.bilibili_live.domain.model.LiveRoomDetail
import com.xycz.bilibili_live.service.danmaku.DanmakuSender
import com.xycz.bilibili_live.service.danmaku.DanmakuService
import com.xycz.bilibili_live.service.player.PlayerManager
import com.xycz.bilibili_live.util.SecureSettingsManager
import com.xycz.bilibili_live.util.SettingsManager
import com.xycz.bilibili_live.util.WbiSigner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 直播间ViewModel
 */
class LiveRoomViewModel(
    application: Application,
    private val roomId: String
) : AndroidViewModel(application) {

    private val api = com.xycz.bilibili_live.util.NetworkModule.bilibiliApi
    private val database = AppDatabase.getInstance(application)
    private val settingsManager = SettingsManager(application)
    private val playerManager = PlayerManager(application)

    private val _uiState = MutableStateFlow(LiveRoomUiState())
    val uiState: StateFlow<LiveRoomUiState> = _uiState.asStateFlow()

    private var danmakuService: DanmakuService? = null
    private var danmakuSender: DanmakuSender? = null
    private var exoPlayer: ExoPlayer? = null

    init {
        loadRoomInfo()
        initPlayer()
    }

    /**
     * 初始化播放器
     */
    private fun initPlayer() {
        exoPlayer = playerManager.initialize()
        _uiState.value = _uiState.value.copy(
            exoPlayer = exoPlayer,
            danmakuEnabled = settingsManager.danmakuEnabled,
            danmakuOpacity = settingsManager.danmakuOpacity,
            danmakuSize = settingsManager.danmakuSize
        )
    }

    /**
     * 加载房间信息
     */
    fun loadRoomInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val signedParams = WbiSigner.getSignedParams(
                    mapOf(
                        "room_id" to roomId,
                        "w_rid" to WbiSigner.generateWwebid(),
                        "wts" to WbiSigner.getWts().toString()
                    )
                )

                // 获取房间信息
                val roomInfoResponse = api.getRoomInfo(
                    roomId = roomId,
                    wRid = signedParams["w_rid"] ?: "",
                    wts = signedParams["wts"]?.toLongOrNull() ?: WbiSigner.getWts()
                )

                // 获取弹幕信息
                val danmakuResponse = api.getDanmakuInfo(
                    roomId = roomId,
                    wRid = signedParams["w_rid"] ?: "",
                    wts = signedParams["wts"]?.toLongOrNull() ?: WbiSigner.getWts()
                )

                // 获取播放信息
                val playInfoResponse = api.getRoomPlayInfo(
                    roomId = roomId,
                    qn = settingsManager.qualityLevel
                )

                if (roomInfoResponse.isSuccessful && danmakuResponse.isSuccessful) {
                    val roomData = roomInfoResponse.body()?.data
                    val danmakuData = danmakuResponse.body()?.data
                    val playData = playInfoResponse.body()?.data

                    val detail = roomData?.let {
                        LiveRoomDetail(
                            roomId = roomId,
                            title = it.title ?: "",
                            cover = it.cover ?: "",
                            userName = it.uname ?: "",
                            userAvatar = it.userAvatar ?: "",
                            online = it.online ?: 0,
                            status = it.liveStatus == 1,
                            url = "https://live.bilibili.com/$roomId",
                            showTime = null,
                            introduction = null
                        )
                    }

                    // 提取播放URL
                    val playUrls = extractPlayUrls(playData)
                    val playHeaders = mapOf(
                        "Referer" to "https://live.bilibili.com/",
                        "User-Agent" to "Mozilla/5.0"
                    )

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        detail = detail,
                        isLive = roomData?.liveStatus == 1,
                        playUrls = playUrls,
                        playHeaders = playHeaders,
                        danmakuServerHost = danmakuData?.host ?: "",
                        danmakuToken = danmakuData?.token ?: "",
                        hostList = danmakuData?.hostList
                    )

                    // 开始播放
                    if (playUrls.isNotEmpty()) {
                        play()
                    }

                    // 记录历史
                    recordHistory()

                    // 检查是否已关注
                    checkFollowStatus()

                    // 连接弹幕
                    connectDanmaku()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "加载失败"
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
     * 提取播放URL
     */
    private fun extractPlayUrls(playData: com.xycz.bilibili_live.data.remote.dto.PlayInfoData?): List<String> {
        val urls = mutableListOf<String>()
        playData?.playUrlInfo?.playUrl?.stream?.forEach { stream ->
            stream.format?.forEach { format ->
                format.codec?.forEach { codec ->
                    codec.urlInfo?.firstOrNull()?.let { urlInfo ->
                        val host = urlInfo.host ?: return@let
                        val extra = urlInfo.extra
                        if (extra != null) {
                            // 构造完整URL
                            val url = buildString {
                                append(host)
                                extra.m3u8Edr?.let { append("/$it") }
                            }
                            if (url.isNotEmpty()) urls.add(url)
                        }
                    }
                }
            }
        }
        return urls.distinct()
    }

    /**
     * 播放
     */
    fun play() {
        val urls = _uiState.value.playUrls
        val headers = _uiState.value.playHeaders
        if (urls.isNotEmpty()) {
            playerManager.play(urls, headers)
        }
    }

    /**
     * 暂停
     */
    fun pause() {
        playerManager.pause()
    }

    /**
     * 恢复
     */
    fun resume() {
        playerManager.resume()
    }

    /**
     * 切换弹幕显示
     */
    fun toggleDanmaku() {
        val newValue = !_uiState.value.danmakuEnabled
        settingsManager.danmakuEnabled = newValue
        _uiState.value = _uiState.value.copy(danmakuEnabled = newValue)
    }

    /**
     * 显示画质选择
     */
    fun showQualitySheet() {
        _uiState.value = _uiState.value.copy(showQualitySheet = true)
    }

    /**
     * 隐藏画质选择
     */
    fun hideQualitySheet() {
        _uiState.value = _uiState.value.copy(showQualitySheet = false)
    }

    /**
     * 切换关注状态
     */
    fun toggleFollow() {
        viewModelScope.launch {
            val detail = _uiState.value.detail ?: return@launch
            val isFollowed = _uiState.value.followed

            if (isFollowed) {
                // 取消关注
                database.followUserDao().deleteById(detail.roomId)
            } else {
                // 添加关注
                val user = FollowUser(
                    id = detail.roomId,
                    roomId = detail.roomId,
                    userName = detail.userName,
                    face = detail.userAvatar,
                    addTime = System.currentTimeMillis()
                )
                database.followUserDao().insert(user)
            }

            _uiState.value = _uiState.value.copy(followed = !isFollowed)
        }
    }

    /**
     * 检查关注状态
     */
    private fun checkFollowStatus() {
        viewModelScope.launch {
            val isFollowed = database.followUserDao().exists(roomId)
            _uiState.value = _uiState.value.copy(followed = isFollowed)
        }
    }

    /**
     * 记录历史
     * 使用 REPLACE 策略，同一个直播间只会保留一条最新记录
     */
    private fun recordHistory() {
        viewModelScope.launch {
            val detail = _uiState.value.detail ?: return@launch
            val history = History(
                roomId = detail.roomId,
                userName = detail.userName,
                face = detail.userAvatar,
                updateTime = System.currentTimeMillis()
            )
            database.historyDao().insert(history)
        }
    }

    /**
     * 连接弹幕
     */
    private fun connectDanmaku() {
        val host = _uiState.value.danmakuServerHost
        val token = _uiState.value.danmakuToken
        val uid = SecureSettingsManager.getUid().toInt().takeIf { it > 0 } ?: (10000..99999).random()

        if (host.isNotEmpty() && token.isNotEmpty()) {
            danmakuService = DanmakuService(
                roomId = roomId.toIntOrNull() ?: 0,
                token = token,
                serverHost = host,
                buvid = SecureSettingsManager.getBuvid3(),
                uid = uid,
                cookie = SecureSettingsManager.getCookie()
            )

            danmakuService?.let { service ->
                viewModelScope.launch {
                    service.messages.collect { message ->
                        if (message is DanmakuMessage.ChatMessage) {
                            // 检查屏蔽词
                            if (!settingsManager.isShielded(message.message)) {
                                _uiState.value = _uiState.value.copy(
                                    danmakuMessages = _uiState.value.danmakuMessages + message
                                )
                            }
                        }
                        _uiState.value = _uiState.value.copy(online = service.onlineCount.value)
                    }
                }

                viewModelScope.launch {
                    service.connectionState.collect { state ->
                        _uiState.value = _uiState.value.copy(danmakuState = state)
                    }
                }

                service.connect()
            }

            danmakuSender = DanmakuSender(roomId, api)
        }
    }

    /**
     * 发送弹幕
     */
    fun sendDanmaku(message: String) {
        viewModelScope.launch {
            val result = danmakuSender?.send(message)
            result?.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    /**
     * 分享房间
     */
    fun shareRoom() {
        _uiState.value = _uiState.value.copy(shareUrl = "https://live.bilibili.com/$roomId")
    }

    /**
     * 释放资源
     */
    override fun onCleared() {
        super.onCleared()
        danmakuService?.disconnect()
        playerManager.release()
    }
}

/**
 * 直播间UI状态
 */
data class LiveRoomUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val detail: LiveRoomDetail? = null,
    val isLive: Boolean = false,
    val online: Int = 0,
    val playUrls: List<String> = emptyList(),
    val playHeaders: Map<String, String> = emptyMap(),
    val exoPlayer: ExoPlayer? = null,
    val danmakuMessages: List<DanmakuMessage.ChatMessage> = emptyList(),
    val danmakuEnabled: Boolean = true,
    val danmakuOpacity: Float = 1f,
    val danmakuSize: Float = 16f,
    val danmakuServerHost: String = "",
    val danmakuToken: String = "",
    val danmakuState: com.xycz.bilibili_live.service.danmaku.ConnectionState = com.xycz.bilibili_live.service.danmaku.ConnectionState.DISCONNECTED,
    val hostList: List<com.xycz.bilibili_live.data.remote.dto.DanmakuHostDto>? = null,
    val followed: Boolean = false,
    val showQualitySheet: Boolean = false,
    val shareUrl: String? = null
)
