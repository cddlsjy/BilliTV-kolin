package com.bili.tv.bili_tv_app.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bili.tv.bili_tv_app.databinding.FragmentPlayerBinding
import com.bili.tv.bili_tv_app.models.Episode
import com.bili.tv.bili_tv_app.models.LiveRoom
import com.bili.tv.bili_tv_app.models.Video
import com.bili.tv.bili_tv_app.services.SettingsService
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.widgets.DanmakuView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var danmakuView: DanmakuView? = null

    // 点播模式参数
    private var bvid: String = ""
    private var title: String = ""
    private var coverUrl: String = ""
    private var cid: Long = 0
    private var aid: Long = 0
    private var episodeList: List<Episode> = emptyList()
    private var currentEpisodeIndex: Int = 0
    private var categoryVideoList: List<Video> = emptyList()
    private var currentCategoryVideoIndex: Int = 0

    // 直播模式参数
    private var isLiveMode: Boolean = false
    private var roomId: Long = 0
    private var followLiveList: List<LiveRoom> = emptyList()
    private var currentFollowLiveIndex: Int = 0
    private var recommendLiveList: List<LiveRoom> = emptyList()
    private var currentRecommendLiveIndex: Int = 0

    // 按键防抖
    private val keyDebounceMs = 600L
    private var lastKeyPressTime = 0L

    // 播放进度
    private var seekTo: Long = 0

    // 触摸手势
    private var touchStartY = 0f
    private var touchStartX = 0f
    private val swipeMinDistance = 100f  // 最小滑动距离（像素）
    
    // 播放模式
    enum class PlaybackMode {
        SEQUENTIAL,  // 顺序播放
        LOOP_ALL,    // 列表循环
        LOOP_ONE,    // 单曲循环
        RANDOM       // 随机播放
    }
    
    private var playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL
    
    // 手势防抖
    private val gestureDebounceMs = 500L
    private var lastGestureTime = 0L
    
    // 加载状态
    private var isLoading = false

    companion object {
        private const val ARG_BVID = "bvid"
        private const val ARG_TITLE = "title"
        private const val ARG_COVER = "cover"
        private const val ARG_CID = "cid"
        private const val ARG_AID = "aid"
        private const val ARG_IS_LIVE = "is_live"
        private const val ARG_ROOM_ID = "room_id"
        private const val ARG_CATEGORY_VIDEO_LIST = "category_video_list"
        private const val ARG_SEEK_TO = "seek_to"

        fun newInstance(
            bvid: String = "",
            title: String = "",
            coverUrl: String = "",
            cid: Long = 0,
            aid: Long = 0,
            isLive: Boolean = false,
            roomId: Long = 0,
            categoryVideoList: List<Video> = emptyList(),
            seekTo: Long = 0
        ): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BVID, bvid)
                    putString(ARG_TITLE, title)
                    putString(ARG_COVER, coverUrl)
                    putLong(ARG_CID, cid)
                    putLong(ARG_AID, aid)
                    putBoolean(ARG_IS_LIVE, isLive)
                    putLong(ARG_ROOM_ID, roomId)
                    putSerializable(ARG_CATEGORY_VIDEO_LIST, ArrayList(categoryVideoList))
                    putLong(ARG_SEEK_TO, seekTo)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            bvid = it.getString(ARG_BVID, "")
            title = it.getString(ARG_TITLE, "")
            coverUrl = it.getString(ARG_COVER, "")
            cid = it.getLong(ARG_CID, 0)
            aid = it.getLong(ARG_AID, 0)
            isLiveMode = it.getBoolean(ARG_IS_LIVE, false)
            roomId = it.getLong(ARG_ROOM_ID, 0)
            categoryVideoList = it.getSerializable(ARG_CATEGORY_VIDEO_LIST) as? ArrayList<Video> ?: emptyList()
            seekTo = it.getLong(ARG_SEEK_TO, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPlayer()
        setupKeyListener()
        setupTouchGesture()
        if (isLiveMode) {
            loadLiveStream()
            preloadLiveLists()
        } else {
            loadVideo()
        }
    }

    private fun setupPlayer() {
        // Create a data source factory with proper headers for Bilibili
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://www.bilibili.com",
                "Origin" to "https://www.bilibili.com"
            ))
        
        val dataSourceFactory = DefaultDataSource.Factory(requireContext(), httpDataSourceFactory)
        
        // Create a player with the data source factory
        player = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory))
            .build().also {
            binding.playerView.player = it

            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            binding.loadingProgress.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            binding.loadingProgress.visibility = View.GONE
                            if (seekTo > 0 && !isLiveMode) {
                                it.seekTo(seekTo)
                                seekTo = 0
                            }
                        }
                        Player.STATE_ENDED -> {
                            binding.loadingProgress.visibility = View.GONE
                            if (!isLiveMode) {
                                playNextEpisode()
                            }
                        }
                        Player.STATE_IDLE -> {
                            binding.loadingProgress.visibility = View.GONE
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Toast.makeText(requireContext(), "播放错误: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // Setup danmaku view (only for non-live mode)
        if (!isLiveMode && SettingsService.danmakuEnabled) {
            setupDanmaku()
        }
    }

    private fun setupDanmaku() {
        danmakuView = binding.danmakuView
        danmakuView?.apply {
            setTextSize(SettingsService.danmakuFontSize)
            setAlpha(SettingsService.danmakuOpacity)
            setSpeed(SettingsService.danmakuDensity)
        }
    }

    private fun setupKeyListener() {
        // 让根布局获得焦点，并拦截所有按键
        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastKeyPressTime < keyDebounceMs) {
                    return@setOnKeyListener true
                }
                lastKeyPressTime = currentTime

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isLiveMode) switchFollowLiveRoom(-1) else switchEpisode(-1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isLiveMode) switchFollowLiveRoom(1) else switchEpisode(1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isLiveMode) switchRecommendLiveRoom(1) else switchCategoryVideo(1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isLiveMode) switchRecommendLiveRoom(-1) else switchCategoryVideo(-1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        // OK键：播放/暂停
                        player?.let {
                            if (it.isPlaying) it.pause() else it.play()
                        }
                        true
                    }
                    else -> false
                }
            } else false
        }
        
        // 可选：禁用 PlayerView 内部的默认按键处理，避免冲突
        binding.playerView.useController = true
        // 仍然显示控制条，但方向键已被我们拦截
    }

    private fun setupTouchGesture() {
        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.y
                    touchStartX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastGestureTime < gestureDebounceMs) {
                        return@setOnTouchListener true
                    }
                    lastGestureTime = currentTime

                    val deltaY = event.y - touchStartY
                    val deltaX = event.x - touchStartX

                    if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > swipeMinDistance) {
                        // 上下滑动：切换分类视频或推荐直播
                        if (deltaY > 0) {
                            // 向下滑动 -> 下一个视频/推荐直播
                            if (isLiveMode) switchRecommendLiveRoom(1) else switchCategoryVideo(1)
                        } else {
                            // 向上滑动 -> 上一个视频/推荐直播
                            if (isLiveMode) switchRecommendLiveRoom(-1) else switchCategoryVideo(-1)
                        }
                    } else if (Math.abs(deltaX) > swipeMinDistance) {
                        // 左右滑动：切换上一个/下一个
                        if (deltaX > 0) {
                            // 向右滑动 -> 下一个
                            playNext()
                        } else {
                            // 向左滑动 -> 上一个
                            playPrev()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun loadVideo() {
        lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                if (bvid.isEmpty()) {
                    Toast.makeText(requireContext(), "视频ID为空", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                // Get video info
                val videoDetail = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getVideoInfo(bvid)
                }

                if (videoDetail == null) {
                    Toast.makeText(requireContext(), "获取视频信息失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                videoDetail.let {video ->
                    binding.videoTitle.text = video.title
                    aid = video.aid
                    
                    // Get episode list
                    episodeList = video.pages
                    if (cid > 0) {
                        currentEpisodeIndex = video.getPageIndex(cid)
                    }

                    if (currentEpisodeIndex < 0 || currentEpisodeIndex >= episodeList.size) {
                        currentEpisodeIndex = 0
                    }

                    val currentEpisode = episodeList[currentEpisodeIndex]
                    cid = currentEpisode.cid

                    // Get play URL
                    val videoUrl = withContext(Dispatchers.IO) {
                        BilibiliApiService.getInstance().getPlayUrl(
                            aid,
                            cid,
                            SettingsService.defaultQuality
                        )
                    }

                    if (videoUrl == null) {
                        Toast.makeText(requireContext(), "获取播放地址失败", Toast.LENGTH_SHORT).show()
                        binding.loadingProgress.visibility = View.GONE
                        return@launch
                    }

                    playVideo(videoUrl)

                    // Load danmaku
                    if (SettingsService.danmakuEnabled && cid > 0) {
                        loadDanmaku(cid)
                    }

                    // Save last played video
                    saveLastPlayedVideo()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "视频加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun loadLiveStream() {
        lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                if (roomId <= 0) {
                    Toast.makeText(requireContext(), "直播间ID无效", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                // Get live stream URL
                val streamUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getLiveStreamUrl(roomId)
                }

                if (streamUrl == null) {
                    Toast.makeText(requireContext(), "获取直播流地址失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                playVideo(streamUrl)
                binding.videoTitle.text = title

                // Save last played live
                saveLastPlayedLive()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "直播加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun preloadLiveLists() {
        // Preload follow live list
        lifecycleScope.launch {
            followLiveList = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getFollowLiveRooms(1)
            }
        }

        // Preload recommend live list
        lifecycleScope.launch {
            recommendLiveList = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getRecommendLiveRooms(1)
            }
        }
    }

    private fun playVideo(url: String) {
        player?.let { exoPlayer ->
            try {
                if (exoPlayer.playbackState != Player.STATE_IDLE) {
                    exoPlayer.stop()
                }
                exoPlayer.clearMediaItems()

                val mediaItem = MediaItem.fromUri(url)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "视频播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadDanmaku(cid: Long) {
        lifecycleScope.launch {
            try {
                val danmakuList = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getDanmaku(cid)
                }

                danmakuList?.let { list ->
                    danmakuView?.setDanmakuList(list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun switchEpisode(direction: Int) {
        if (isLoading || episodeList.isEmpty()) return

        val newIndex = currentEpisodeIndex + direction
        if (newIndex < 0 || newIndex >= episodeList.size) {
            if (playbackMode == PlaybackMode.LOOP_ALL) {
                // 列表循环，处理边界情况
                val loopIndex = if (newIndex < 0) episodeList.size - 1 else 0
                currentEpisodeIndex = loopIndex
            } else {
                return
            }
        } else {
            currentEpisodeIndex = newIndex
        }

        val newEpisode = episodeList[currentEpisodeIndex]
        cid = newEpisode.cid

        showSwitchOverlay("加载中...")
        isLoading = true

        // Load new episode
        lifecycleScope.launch {
            try {
                val videoUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getPlayUrl(aid, cid, SettingsService.defaultQuality)
                }

                videoUrl?.let {
                    playVideo(it)
                    saveLastPlayedVideo()
                    showSwitchOverlay("第${newEpisode.page}集: ${newEpisode.part}")
                } ?: run {
                    showSwitchOverlay("加载失败")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showSwitchOverlay("加载失败")
            } finally {
                isLoading = false
            }
        }
    }

    private fun switchCategoryVideo(direction: Int) {
        if (isLoading || categoryVideoList.isEmpty()) return

        val newIndex = currentCategoryVideoIndex + direction
        if (newIndex < 0 || newIndex >= categoryVideoList.size) {
            if (playbackMode == PlaybackMode.LOOP_ALL) {
                // 列表循环，处理边界情况
                val loopIndex = if (newIndex < 0) categoryVideoList.size - 1 else 0
                currentCategoryVideoIndex = loopIndex
            } else {
                return
            }
        } else {
            currentCategoryVideoIndex = newIndex
        }

        val newVideo = categoryVideoList[currentCategoryVideoIndex]

        showSwitchOverlay("加载中...")
        isLoading = true

        // Load new video
        bvid = newVideo.bvid
        title = newVideo.title
        coverUrl = newVideo.pic
        cid = newVideo.cid
        aid = newVideo.aid
        currentEpisodeIndex = 0

        lifecycleScope.launch {
            try {
                loadVideo()
                showSwitchOverlay(newVideo.title)
            } catch (e: Exception) {
                e.printStackTrace()
                showSwitchOverlay("加载失败")
            } finally {
                isLoading = false
            }
        }
    }

    private fun switchFollowLiveRoom(direction: Int) {
        if (isLoading || followLiveList.isEmpty()) return

        val newIndex = currentFollowLiveIndex + direction
        if (newIndex < 0) {
            if (playbackMode == PlaybackMode.LOOP_ALL) {
                // 列表循环，回到最后一个
                currentFollowLiveIndex = followLiveList.size - 1
            } else {
                return
            }
        } else if (newIndex >= followLiveList.size) {
            // Load next page
            showSwitchOverlay("加载中...")
            isLoading = true
            lifecycleScope.launch {
                try {
                    val moreRooms = withContext(Dispatchers.IO) {
                        BilibiliApiService.getInstance().getFollowLiveRooms((newIndex / 20) + 1)
                    }
                    if (moreRooms.isNotEmpty()) {
                        followLiveList += moreRooms
                        switchFollowLiveRoom(direction)
                    } else {
                        showSwitchOverlay("没有更多直播间")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showSwitchOverlay("加载失败")
                } finally {
                    isLoading = false
                }
            }
            return
        } else {
            currentFollowLiveIndex = newIndex
        }

        val newRoom = followLiveList[currentFollowLiveIndex]

        showSwitchOverlay("加载中...")
        isLoading = true

        // Load new live
        roomId = newRoom.roomId
        title = newRoom.title

        lifecycleScope.launch {
            try {
                loadLiveStream()
                showSwitchOverlay(newRoom.title)
            } catch (e: Exception) {
                e.printStackTrace()
                showSwitchOverlay("加载失败")
            } finally {
                isLoading = false
            }
        }
    }

    private fun switchRecommendLiveRoom(direction: Int) {
        if (isLoading || recommendLiveList.isEmpty()) return

        val newIndex = currentRecommendLiveIndex + direction
        if (newIndex < 0) {
            if (playbackMode == PlaybackMode.LOOP_ALL) {
                // 列表循环，回到最后一个
                currentRecommendLiveIndex = recommendLiveList.size - 1
            } else {
                return
            }
        } else if (newIndex >= recommendLiveList.size) {
            // Load next page
            showSwitchOverlay("加载中...")
            isLoading = true
            lifecycleScope.launch {
                try {
                    val moreRooms = withContext(Dispatchers.IO) {
                        BilibiliApiService.getInstance().getRecommendLiveRooms((newIndex / 20) + 1)
                    }
                    if (moreRooms.isNotEmpty()) {
                        recommendLiveList += moreRooms
                        switchRecommendLiveRoom(direction)
                    } else {
                        showSwitchOverlay("没有更多直播间")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showSwitchOverlay("加载失败")
                } finally {
                    isLoading = false
                }
            }
            return
        } else {
            currentRecommendLiveIndex = newIndex
        }

        val newRoom = recommendLiveList[currentRecommendLiveIndex]

        showSwitchOverlay("加载中...")
        isLoading = true

        // Load new live
        roomId = newRoom.roomId
        title = newRoom.title

        lifecycleScope.launch {
            try {
                loadLiveStream()
                showSwitchOverlay(newRoom.title)
            } catch (e: Exception) {
                e.printStackTrace()
                showSwitchOverlay("加载失败")
            } finally {
                isLoading = false
            }
        }
    }

    private fun showSwitchOverlay(text: String) {
        binding.switchOverlay.visibility = View.VISIBLE
        binding.switchOverlay.text = text

        lifecycleScope.launch {
            delay(2000)
            binding.switchOverlay.visibility = View.GONE
        }
    }

    private fun playNextEpisode() {
        if (episodeList.size > currentEpisodeIndex + 1) {
            switchEpisode(1)
        } else if (playbackMode == PlaybackMode.LOOP_ALL) {
            // 列表循环，回到第一集
            currentEpisodeIndex = -1
            switchEpisode(1)
        }
    }
    
    private fun playNext() {
        when {
            isLiveMode -> {
                // 直播模式：优先切换关注的直播间，然后切换推荐直播间
                if (followLiveList.isNotEmpty() && currentFollowLiveIndex < followLiveList.size - 1) {
                    switchFollowLiveRoom(1)
                } else if (recommendLiveList.isNotEmpty()) {
                    switchRecommendLiveRoom(1)
                }
            }
            else -> {
                // 点播模式：优先切换分集，然后切换分类视频
                if (episodeList.size > currentEpisodeIndex + 1) {
                    switchEpisode(1)
                } else if (categoryVideoList.isNotEmpty()) {
                    switchCategoryVideo(1)
                } else if (playbackMode == PlaybackMode.LOOP_ALL) {
                    // 列表循环，回到第一集
                    currentEpisodeIndex = -1
                    switchEpisode(1)
                }
            }
        }
    }
    
    private fun playPrev() {
        when {
            isLiveMode -> {
                // 直播模式：优先切换关注的直播间，然后切换推荐直播间
                if (followLiveList.isNotEmpty() && currentFollowLiveIndex > 0) {
                    switchFollowLiveRoom(-1)
                } else if (recommendLiveList.isNotEmpty() && currentRecommendLiveIndex > 0) {
                    switchRecommendLiveRoom(-1)
                }
            }
            else -> {
                // 点播模式：优先切换分集，然后切换分类视频
                if (currentEpisodeIndex > 0) {
                    switchEpisode(-1)
                } else if (categoryVideoList.isNotEmpty() && currentCategoryVideoIndex > 0) {
                    switchCategoryVideo(-1)
                } else if (playbackMode == PlaybackMode.LOOP_ALL) {
                    // 列表循环，回到最后一集
                    currentEpisodeIndex = episodeList.size
                    switchEpisode(-1)
                }
            }
        }
    }
    
    private fun setPlaybackMode(mode: PlaybackMode) {
        playbackMode = mode
        // 可以在这里添加持久化逻辑，保存到SettingsService
    }
    
    private fun getPlaybackMode(): PlaybackMode {
        return playbackMode
    }

    private fun savePlaybackProgress() {
        if (!isLiveMode) {
            val currentPosition = player?.currentPosition ?: 0
            SettingsService.saveLastPlayedVideo(
                bvid = bvid,
                title = title,
                cover = coverUrl,
                cid = cid,
                progress = currentPosition,
                isLive = false,
                roomId = 0
            )
        }
    }

    private fun saveLastPlayedVideo() {
        SettingsService.saveLastPlayedVideo(
            bvid = bvid,
            title = title,
            cover = coverUrl,
            cid = cid,
            progress = 0,
            isLive = false,
            roomId = 0
        )
    }

    private fun saveLastPlayedLive() {
        SettingsService.saveLastPlayedVideo(
            bvid = "",
            title = title,
            cover = "",
            cid = 0,
            progress = 0,
            isLive = true,
            roomId = roomId
        )
    }

    override fun onResume() {
        super.onResume()
        binding.root.requestFocus()  // 确保根布局获得焦点
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        savePlaybackProgress()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        savePlaybackProgress()
        player?.release()
        player = null
        _binding = null
    }
}
