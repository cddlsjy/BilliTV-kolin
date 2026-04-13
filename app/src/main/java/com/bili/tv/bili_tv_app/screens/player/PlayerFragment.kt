package com.bili.tv.bili_tv_app.screens.player

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bili.tv.bili_tv_app.R
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
import kotlin.random.Random

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var danmakuView: DanmakuView? = null

    // ========== 播放模式枚举 ==========
    enum class PlaybackMode {
        SEQUENCE,      // 顺序播放
        LIST_LOOP,    // 列表循环
        SINGLE_LOOP,  // 单曲循环
        SHUFFLE       // 随机播放
    }

    // ========== 播放模式相关 ==========
    private var playbackMode: PlaybackMode = PlaybackMode.SEQUENCE
    private var shuffledIndices: List<Int> = emptyList()

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

    // ========== 防抖相关 ==========
    private val keyDebounceMs = 600L
    private var lastKeyPressTime = 0L
    private val switchDebounceMs = 500L
    private var lastSwitchTime = 0L
    private var isLoading = false

    // 播放进度
    private var seekTo: Long = 0

    // ========== 触摸手势相关 ==========
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val swipeMinDistance = 100f  // 最小滑动距离（像素）
    private val swipeThresholdVelocity = 500f  // 滑动速度阈值

    // ========== 切换提示视图 ==========
    private var swipeIndicatorLeft: ImageView? = null
    private var swipeIndicatorRight: ImageView? = null
    private var swipeIndicatorUp: ImageView? = null
    private var swipeIndicatorDown: ImageView? = null
    private var modeIndicator: TextView? = null

    companion object {
        private const val TAG = "PlayerFragment"
        private const val ARG_BVID = "bvid"
        private const val ARG_TITLE = "title"
        private const val ARG_COVER = "cover"
        private const val ARG_CID = "cid"
        private const val ARG_AID = "aid"
        private const val ARG_IS_LIVE = "is_live"
        private const val ARG_ROOM_ID = "room_id"
        private const val ARG_CATEGORY_VIDEO_LIST = "category_video_list"
        private const val ARG_SEEK_TO = "seek_to"

        // 播放模式对应的图标资源
        private val playbackModeIcons = mapOf(
            PlaybackMode.SEQUENCE to android.R.drawable.ic_media_play,
            PlaybackMode.LIST_LOOP to android.R.drawable.ic_menu_rotate,
            PlaybackMode.SINGLE_LOOP to android.R.drawable.ic_menu_revert,
            PlaybackMode.SHUFFLE to android.R.drawable.ic_menu_directions
        )

        private val playbackModeNames = mapOf(
            PlaybackMode.SEQUENCE to "顺序播放",
            PlaybackMode.LIST_LOOP to "列表循环",
            PlaybackMode.SINGLE_LOOP to "单曲循环",
            PlaybackMode.SHUFFLE to "随机播放"
        )

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

        // 恢复保存的播放模式
        playbackMode = PlaybackMode.entries.getOrNull(
            SettingsService.preferences?.getInt("playback_mode", 0) ?: 0
        ) ?: PlaybackMode.SEQUENCE

        // 初始化随机播放索引
        initShuffledIndices()
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

        // 创建滑动指示器
        createSwipeIndicators()

        setupPlayer()
        setupKeyListener()
        setupTouchGesture()
        setupBackButton()

        if (isLiveMode) {
            loadLiveStream()
            preloadLiveLists()
        } else {
            loadVideo()
        }

        // 显示播放模式
        updateModeIndicator()
    }

    private fun createSwipeIndicators() {
        val params = FrameLayout.LayoutParams(80.dpToPx(), 80.dpToPx()).apply {
            gravity = Gravity.CENTER
        }

        // 左滑指示器
        swipeIndicatorLeft = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(android.graphics.Color.WHITE)
            alpha = 0f
        }

        // 右滑指示器
        swipeIndicatorRight = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(android.graphics.Color.WHITE)
            alpha = 0f
        }

        // 上滑指示器
        swipeIndicatorUp = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.arrow_up_float)
            setColorFilter(android.graphics.Color.WHITE)
            alpha = 0f
        }

        // 下滑指示器
        swipeIndicatorDown = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            setColorFilter(android.graphics.Color.WHITE)
            alpha = 0f
        }

        // 添加到布局
        binding.root.addView(swipeIndicatorLeft, params.apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START; marginStart = 50.dpToPx() })
        binding.root.addView(swipeIndicatorRight, params.apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; marginEnd = 50.dpToPx() })
        binding.root.addView(swipeIndicatorUp, params.apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = 100.dpToPx() })
        binding.root.addView(swipeIndicatorDown, params.apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = 100.dpToPx() })
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

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
                                isLoading = false
                                if (seekTo > 0 && !isLiveMode) {
                                    it.seekTo(seekTo)
                                    seekTo = 0
                                }
                            }
                            Player.STATE_ENDED -> {
                                binding.loadingProgress.visibility = View.GONE
                                isLoading = false
                                if (!isLiveMode) {
                                    // 根据播放模式处理播放结束
                                    handlePlaybackEnded()
                                }
                            }
                            Player.STATE_IDLE -> {
                                binding.loadingProgress.visibility = View.GONE
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Toast.makeText(requireContext(), "播放错误: ${error.message}", Toast.LENGTH_SHORT).show()
                        isLoading = false
                        binding.loadingProgress.visibility = View.GONE
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

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            savePlaybackProgress()
            parentFragmentManager.popBackStack()
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
                    // ========== 上一首/下一首 (D-Pad Left/Right) ==========
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isLiveMode) {
                            playPrevLive()
                        } else {
                            playPrev()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isLiveMode) {
                            playNextLive()
                        } else {
                            playNext()
                        }
                        true
                    }
                    // ========== 上下切换分类 (D-Pad Up/Down) ==========
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isLiveMode) {
                            switchRecommendLiveRoom(1)
                        } else {
                            switchCategoryVideo(1)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isLiveMode) {
                            switchRecommendLiveRoom(-1)
                        } else {
                            switchCategoryVideo(-1)
                        }
                        true
                    }
                    // ========== 播放/暂停 (OK键) ==========
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        player?.let {
                            if (it.isPlaying) it.pause() else it.play()
                        }
                        true
                    }
                    // ========== 切换播放模式 (Menu键或Play键) ==========
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        cyclePlaybackMode()
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

    // ========== 增强手势滑动切换 ==========
    private fun setupTouchGesture() {
        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY

                    // 显示滑动方向指示
                    showSwipeIndicator(deltaX, deltaY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY

                    // 隐藏指示器
                    hideSwipeIndicators()

                    // 检查防抖
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastSwitchTime < switchDebounceMs) {
                        return@setOnTouchListener true
                    }
                    lastSwitchTime = currentTime

                    // 判断滑动方向
                    val absDeltaX = kotlin.math.abs(deltaX)
                    val absDeltaY = kotlin.math.abs(deltaY)

                    if (absDeltaX > swipeMinDistance || absDeltaY > swipeMinDistance) {
                        // 震动反馈
                        performHapticFeedback()

                        if (absDeltaX > absDeltaY) {
                            // 水平滑动 - 上一首/下一首
                            if (deltaX > 0) {
                                if (isLiveMode) playNextLive() else playNext()
                            } else {
                                if (isLiveMode) playPrevLive() else playPrev()
                            }
                        } else {
                            // 垂直滑动 - 切换分类
                            if (deltaY > 0) {
                                // 向下滑动
                                if (isLiveMode) switchRecommendLiveRoom(1) else switchCategoryVideo(1)
                            } else {
                                // 向上滑动
                                if (isLiveMode) switchRecommendLiveRoom(-1) else switchCategoryVideo(-1)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideSwipeIndicators()
                    true
                }
                else -> false
            }
        }
    }

    private fun showSwipeIndicator(deltaX: Float, deltaY: Float) {
        val absDeltaX = kotlin.math.abs(deltaX)
        val absDeltaY = kotlin.math.abs(deltaY)

        val alpha = kotlin.math.min(1f, kotlin.math.max(absDeltaX, absDeltaY) / 200f)

        if (absDeltaX > absDeltaY) {
            // 水平滑动
            swipeIndicatorLeft?.alpha = if (deltaX < 0) alpha else 0f
            swipeIndicatorRight?.alpha = if (deltaX > 0) alpha else 0f
            swipeIndicatorUp?.alpha = 0f
            swipeIndicatorDown?.alpha = 0f
        } else {
            // 垂直滑动
            swipeIndicatorUp?.alpha = if (deltaY < 0) alpha else 0f
            swipeIndicatorDown?.alpha = if (deltaY > 0) alpha else 0f
            swipeIndicatorLeft?.alpha = 0f
            swipeIndicatorRight?.alpha = 0f
        }
    }

    private fun hideSwipeIndicators() {
        swipeIndicatorLeft?.alpha = 0f
        swipeIndicatorRight?.alpha = 0f
        swipeIndicatorUp?.alpha = 0f
        swipeIndicatorDown?.alpha = 0f
    }

    private fun performHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = requireContext().getSystemService(VibratorManager::class.java)
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = requireContext().getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Haptic feedback failed: ${e.message}")
        }
    }

    // ========== 播放模式相关方法 ==========
    private fun cyclePlaybackMode() {
        val nextIndex = (playbackMode.ordinal + 1) % PlaybackMode.entries.size
        playbackMode = PlaybackMode.entries[nextIndex]

        // 保存到设置
        SettingsService.preferences?.edit()?.putInt("playback_mode", playbackMode.ordinal)?.apply()

        // 根据模式重新初始化随机索引
        if (playbackMode == PlaybackMode.SHUFFLE) {
            initShuffledIndices()
        }

        // 显示切换提示
        val modeName = playbackModeNames[playbackMode] ?: "顺序播放"
        showModeSwitchOverlay(modeName)
        updateModeIndicator()

        Toast.makeText(requireContext(), modeName, Toast.LENGTH_SHORT).show()
    }

    private fun updateModeIndicator() {
        // 更新hintText显示当前播放模式
        val modeText = playbackModeNames[playbackMode] ?: ""
        binding.hintText.text = "播放模式: $modeText | ←→切换分集/直播间  ↑↓切换分类视频/推荐直播"
    }

    private fun showModeSwitchOverlay(modeName: String) {
        binding.switchOverlay.visibility = View.VISIBLE
        binding.switchOverlay.text = "播放模式: $modeName"
        binding.switchOverlay.textSize = 20f

        lifecycleScope.launch {
            delay(1500)
            binding.switchOverlay.visibility = View.GONE
            binding.switchOverlay.textSize = 16f
        }
    }

    private fun initShuffledIndices() {
        val listSize = episodeList.size
        shuffledIndices = (0 until listSize).shuffled(Random)
    }

    // ========== 核心切换逻辑：playNext() 和 playPrev() ==========
    private fun playNext() {
        Log.d(TAG, "playNext called, mode: $playbackMode")

        // 如果正在加载，直接返回
        if (isLoading) {
            Toast.makeText(requireContext(), "正在加载中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查列表是否为空
        if (episodeList.isEmpty()) {
            // 如果没有分集列表，尝试切换到下一个分类视频
            if (categoryVideoList.isNotEmpty()) {
                switchCategoryVideo(1)
            } else {
                Toast.makeText(requireContext(), "没有更多内容了", Toast.LENGTH_SHORT).show()
            }
            return
        }

        when (playbackMode) {
            PlaybackMode.SEQUENCE -> {
                // 顺序播放：播完当前集后播下一集，到末尾停止
                if (currentEpisodeIndex < episodeList.size - 1) {
                    switchEpisode(1)
                } else {
                    // 已到达列表末尾
                    if (categoryVideoList.isNotEmpty() && currentCategoryVideoIndex < categoryVideoList.size - 1) {
                        // 尝试切换到下一个分类视频
                        switchCategoryVideo(1)
                    } else {
                        Toast.makeText(requireContext(), "已是最后一个视频", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            PlaybackMode.LIST_LOOP -> {
                // 列表循环：播完当前集后播下一集，到末尾循环到开头
                if (currentEpisodeIndex < episodeList.size - 1) {
                    switchEpisode(1)
                } else {
                    // 循环到第一集
                    switchEpisode(-currentEpisodeIndex)
                }
            }
            PlaybackMode.SINGLE_LOOP -> {
                // 单曲循环：重新播放当前视频
                Toast.makeText(requireContext(), "单曲循环: ${episodeList[currentEpisodeIndex].part}", Toast.LENGTH_SHORT).show()
                player?.seekTo(0)
                player?.play()
            }
            PlaybackMode.SHUFFLE -> {
                // 随机播放：随机切换到另一个视频
                if (episodeList.size > 1) {
                    val currentShuffleIndex = shuffledIndices.indexOf(currentEpisodeIndex)
                    val nextShuffleIndex = (currentShuffleIndex + 1) % shuffledIndices.size
                    val nextEpisodeIndex = shuffledIndices[nextShuffleIndex]
                    switchEpisode(nextEpisodeIndex - currentEpisodeIndex)
                } else {
                    switchCategoryVideo(1)
                }
            }
        }
    }

    private fun playPrev() {
        Log.d(TAG, "playPrev called, mode: $playbackMode")

        if (isLoading) {
            Toast.makeText(requireContext(), "正在加载中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        if (episodeList.isEmpty()) {
            if (categoryVideoList.isNotEmpty()) {
                switchCategoryVideo(-1)
            } else {
                Toast.makeText(requireContext(), "没有更多内容了", Toast.LENGTH_SHORT).show()
            }
            return
        }

        when (playbackMode) {
            PlaybackMode.SEQUENCE -> {
                if (currentEpisodeIndex > 0) {
                    switchEpisode(-1)
                } else {
                    // 已到达列表开头
                    if (categoryVideoList.isNotEmpty() && currentCategoryVideoIndex > 0) {
                        switchCategoryVideo(-1)
                    } else {
                        Toast.makeText(requireContext(), "已是第一个视频", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            PlaybackMode.LIST_LOOP -> {
                if (currentEpisodeIndex > 0) {
                    switchEpisode(-1)
                } else {
                    // 循环到最后一集
                    switchEpisode(episodeList.size - 1 - currentEpisodeIndex)
                }
            }
            PlaybackMode.SINGLE_LOOP -> {
                Toast.makeText(requireContext(), "单曲循环: ${episodeList[currentEpisodeIndex].part}", Toast.LENGTH_SHORT).show()
                player?.seekTo(0)
                player?.play()
            }
            PlaybackMode.SHUFFLE -> {
                if (episodeList.size > 1) {
                    val currentShuffleIndex = shuffledIndices.indexOf(currentEpisodeIndex)
                    val prevShuffleIndex = if (currentShuffleIndex > 0) currentShuffleIndex - 1 else shuffledIndices.size - 1
                    val prevEpisodeIndex = shuffledIndices[prevShuffleIndex]
                    switchEpisode(prevEpisodeIndex - currentEpisodeIndex)
                } else {
                    switchCategoryVideo(-1)
                }
            }
        }
    }

    private fun playNextLive() {
        if (isLoading) {
            Toast.makeText(requireContext(), "正在加载中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        // 直播模式下切换关注直播间
        if (followLiveList.isEmpty()) {
            Toast.makeText(requireContext(), "没有更多直播间了", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentFollowLiveIndex < followLiveList.size - 1) {
            switchFollowLiveRoom(1)
        } else {
            // 循环到第一个
            switchFollowLiveRoom(-currentFollowLiveIndex)
        }
    }

    private fun playPrevLive() {
        if (isLoading) {
            Toast.makeText(requireContext(), "正在加载中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        if (followLiveList.isEmpty()) {
            Toast.makeText(requireContext(), "没有更多直播间了", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentFollowLiveIndex > 0) {
            switchFollowLiveRoom(-1)
        } else {
            // 循环到最后一个
            switchFollowLiveRoom(followLiveList.size - 1 - currentFollowLiveIndex)
        }
    }

    // ========== 播放结束处理 ==========
    private fun handlePlaybackEnded() {
        when (playbackMode) {
            PlaybackMode.SINGLE_LOOP -> {
                // 单曲循环：重新播放
                player?.seekTo(0)
                player?.play()
            }
            PlaybackMode.LIST_LOOP -> {
                // 列表循环：自动播放下一集
                if (currentEpisodeIndex < episodeList.size - 1) {
                    switchEpisode(1)
                } else {
                    // 回到第一集
                    switchEpisode(-currentEpisodeIndex)
                }
            }
            else -> {
                // 顺序播放和随机播放：已经由用户手动触发
                Log.d(TAG, "Playback ended in ${playbackMode.name} mode")
            }
        }
    }

    private fun loadVideo() {
        lifecycleScope.launch {
            isLoading = true
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                if (bvid.isEmpty()) {
                    Toast.makeText(requireContext(), "视频ID为空", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    isLoading = false
                    return@launch
                }

                // Get video info
                val videoDetail = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getVideoInfo(bvid)
                }

                if (videoDetail == null) {
                    Toast.makeText(requireContext(), "获取视频信息失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    isLoading = false
                    return@launch
                }

                videoDetail.let { video ->
                    binding.videoTitle.text = video.title
                    aid = video.aid

                    // Get episode list
                    episodeList = video.pages

                    // 初始化随机播放索引
                    initShuffledIndices()

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
                        isLoading = false
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
                isLoading = false
            }
        }
    }

    private fun loadLiveStream() {
        lifecycleScope.launch {
            isLoading = true
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                if (roomId <= 0) {
                    Toast.makeText(requireContext(), "直播间ID无效", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    isLoading = false
                    return@launch
                }

                // Get live stream URL
                val streamUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getLiveStreamUrl(roomId)
                }

                if (streamUrl == null) {
                    Toast.makeText(requireContext(), "获取直播流地址失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    isLoading = false
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
                isLoading = false
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

    // ========== 优化后的 switchEpisode 方法（防抖、加载状态） ==========
    private fun switchEpisode(direction: Int) {
        if (isLoading) {
            Toast.makeText(requireContext(), "正在切换中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        if (episodeList.isEmpty()) {
            Toast.makeText(requireContext(), "没有分集列表", Toast.LENGTH_SHORT).show()
            return
        }

        val newIndex = currentEpisodeIndex + direction
        if (newIndex < 0 || newIndex >= episodeList.size) {
            Toast.makeText(requireContext(), "已是边界，无法继续", Toast.LENGTH_SHORT).show()
            return
        }

        // 显示加载提示
        val newEpisode = episodeList[newIndex]
        showSwitchOverlay("正在加载: 第${newEpisode.page}集")

        currentEpisodeIndex = newIndex
        val currentEpisode = episodeList[currentEpisodeIndex]
        cid = currentEpisode.cid

        // Load new episode
        lifecycleScope.launch {
            isLoading = true
            try {
                val videoUrl = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getPlayUrl(aid, cid, SettingsService.defaultQuality)
                }

                videoUrl?.let {
                    playVideo(it)
                    saveLastPlayedVideo()
                    showSwitchOverlay("第${currentEpisode.page}集: ${currentEpisode.part}")
                } ?: run {
                    // 加载失败，恢复到原状态
                    currentEpisodeIndex = newIndex - direction
                    showSwitchOverlay("加载失败")
                    Toast.makeText(requireContext(), "获取播放地址失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 加载失败，恢复到原状态
                currentEpisodeIndex = newIndex - direction
                showSwitchOverlay("加载失败: ${e.message}")
                Toast.makeText(requireContext(), "切换失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // ========== 优化后的 switchCategoryVideo 方法 ==========
    private fun switchCategoryVideo(direction: Int) {
        if (isLoading) {
            Toast.makeText(requireContext(), "正在切换中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        if (categoryVideoList.isEmpty()) {
            Toast.makeText(requireContext(), "没有分类视频列表", Toast.LENGTH_SHORT).show()
            return
        }

        val newIndex = currentCategoryVideoIndex + direction
        if (newIndex < 0 || newIndex >= categoryVideoList.size) {
            Toast.makeText(requireContext(), "已是边界，无法继续", Toast.LENGTH_SHORT).show()
            return
        }

        showSwitchOverlay("正在切换分类...")

        currentCategoryVideoIndex = newIndex
        val newVideo = categoryVideoList[newIndex]

        // Load new video
        bvid = newVideo.bvid
        title = newVideo.title
        coverUrl = newVideo.pic
        cid = newVideo.cid
        aid = newVideo.aid
        currentEpisodeIndex = 0

        loadVideo()
    }

    // ========== 优化后的 switchFollowLiveRoom 方法 ==========
    private fun switchFollowLiveRoom(direction: Int) {
        if (isLoading) {
            Toast.makeText(requireContext(), "正在切换中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        if (followLiveList.isEmpty()) {
            Toast.makeText(requireContext(), "没有关注的直播间", Toast.LENGTH_SHORT).show()
            return
        }

        val newIndex = currentFollowLiveIndex + direction
        if (newIndex < 0) {
            Toast.makeText(requireContext(), "已是第一个直播间", Toast.LENGTH_SHORT).show()
            return
        }

        if (newIndex >= followLiveList.size) {
            // Load next page
            lifecycleScope.launch {
                isLoading = true
                try {
                    val moreRooms = withContext(Dispatchers.IO) {
                        BilibiliApiService.getInstance().getFollowLiveRooms((newIndex / 20) + 1)
                    }
                    if (moreRooms.isNotEmpty()) {
                        followLiveList += moreRooms
                        switchFollowLiveRoom(direction)
                    } else {
                        Toast.makeText(requireContext(), "没有更多直播间了", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isLoading = false
                }
            }
            return
        }

        showSwitchOverlay("正在切换直播间...")

        currentFollowLiveIndex = newIndex
        val newRoom = followLiveList[newIndex]

        // Load new live
        roomId = newRoom.roomId
        title = newRoom.title
        loadLiveStream()
    }

    // ========== 优化后的 switchRecommendLiveRoom 方法 ==========
    private fun switchRecommendLiveRoom(direction: Int) {
        if (isLoading) {
            Toast.makeText(requireContext(), "正在切换中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        if (recommendLiveList.isEmpty()) {
            Toast.makeText(requireContext(), "没有推荐直播间", Toast.LENGTH_SHORT).show()
            return
        }

        val newIndex = currentRecommendLiveIndex + direction
        if (newIndex < 0) {
            Toast.makeText(requireContext(), "已是第一个推荐", Toast.LENGTH_SHORT).show()
            return
        }

        if (newIndex >= recommendLiveList.size) {
            // Load next page
            lifecycleScope.launch {
                isLoading = true
                try {
                    val moreRooms = withContext(Dispatchers.IO) {
                        BilibiliApiService.getInstance().getRecommendLiveRooms((newIndex / 20) + 1)
                    }
                    if (moreRooms.isNotEmpty()) {
                        recommendLiveList += moreRooms
                        switchRecommendLiveRoom(direction)
                    } else {
                        Toast.makeText(requireContext(), "没有更多推荐了", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isLoading = false
                }
            }
            return
        }

        showSwitchOverlay("正在切换推荐直播间...")

        currentRecommendLiveIndex = newIndex
        val newRoom = recommendLiveList[newIndex]

        // Load new live
        roomId = newRoom.roomId
        title = newRoom.title
        loadLiveStream()
    }

    private fun showSwitchOverlay(text: String) {
        binding.switchOverlay.visibility = View.VISIBLE
        binding.switchOverlay.text = text

        lifecycleScope.launch {
            delay(2000)
            binding.switchOverlay.visibility = View.GONE
        }
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
