package com.bili.tv.bili_tv_app.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
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
        arguments?.let {\n            bvid = it.getString(ARG_BVID, "")
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
        binding.playerView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastKeyPressTime < keyDebounceMs) {
                    return@setOnKeyListener true
                }
                lastKeyPressTime = currentTime

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isLiveMode) {
                            switchFollowLiveRoom(-1)
                        } else {
                            switchEpisode(-1)
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isLiveMode) {
                            switchFollowLiveRoom(1)
                        } else {
                            switchEpisode(1)
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isLiveMode) {
                            switchRecommendLiveRoom(1)
                        } else {
                            switchCategoryVideo(1)
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isLiveMode) {
                            switchRecommendLiveRoom(-1)
                        } else {
                            switchCategoryVideo(-1)
                        }
                        return@setOnKeyListener true
                    }
                }
            }
            false
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
        if (episodeList.isEmpty()) return

        val newIndex = currentEpisodeIndex + direction
        if (newIndex < 0 || newIndex >= episodeList.size) return

        currentEpisodeIndex = newIndex
        val newEpisode = episodeList[currentEpisodeIndex]
        cid = newEpisode.cid

        showSwitchOverlay("第${newEpisode.page}集: ${newEpisode.part}")

        // Load new episode
        lifecycleScope.launch {
            val videoUrl = withContext(Dispatchers.IO) {
                BilibiliApiService.getInstance().getPlayUrl(aid, cid, SettingsService.defaultQuality)
            }

            videoUrl?.let {
                playVideo(it)
                saveLastPlayedVideo()
            }
        }
    }

    private fun switchCategoryVideo(direction: Int) {
        if (categoryVideoList.isEmpty()) return

        val newIndex = currentCategoryVideoIndex + direction
        if (newIndex < 0 || newIndex >= categoryVideoList.size) return

        currentCategoryVideoIndex = newIndex
        val newVideo = categoryVideoList[newIndex]

        showSwitchOverlay(newVideo.title)

        // Load new video
        bvid = newVideo.bvid
        title = newVideo.title
        coverUrl = newVideo.pic
        cid = newVideo.cid
        aid = newVideo.aid
        currentEpisodeIndex = 0

        loadVideo()
    }

    private fun switchFollowLiveRoom(direction: Int) {
        if (followLiveList.isEmpty()) return

        val newIndex = currentFollowLiveIndex + direction
        if (newIndex < 0) return
        if (newIndex >= followLiveList.size) {
            // Load next page
            lifecycleScope.launch {
                val moreRooms = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getFollowLiveRooms((newIndex / 20) + 1)
                }
                if (moreRooms.isNotEmpty()) {
                    followLiveList += moreRooms
                    switchFollowLiveRoom(direction)
                }
            }
            return
        }

        currentFollowLiveIndex = newIndex
        val newRoom = followLiveList[newIndex]

        showSwitchOverlay(newRoom.title)

        // Load new live
        roomId = newRoom.roomId
        title = newRoom.title
        loadLiveStream()
    }

    private fun switchRecommendLiveRoom(direction: Int) {
        if (recommendLiveList.isEmpty()) return

        val newIndex = currentRecommendLiveIndex + direction
        if (newIndex < 0) return
        if (newIndex >= recommendLiveList.size) {
            // Load next page
            lifecycleScope.launch {
                val moreRooms = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getRecommendLiveRooms((newIndex / 20) + 1)
                }
                if (moreRooms.isNotEmpty()) {
                    recommendLiveList += moreRooms
                    switchRecommendLiveRoom(direction)
                }
            }
            return
        }

        currentRecommendLiveIndex = newIndex
        val newRoom = recommendLiveList[newIndex]

        showSwitchOverlay(newRoom.title)

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

    private fun playNextEpisode() {
        if (episodeList.size > currentEpisodeIndex + 1) {
            switchEpisode(1)
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
