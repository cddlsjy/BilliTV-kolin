package com.bili.tv.bili_tv_app.screens.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.bili.tv.bili_tv_app.services.api.BilibiliApiService
import com.bili.tv.bili_tv_app.services.SettingsService
import com.bili.tv.bili_tv_app.widgets.DanmakuView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var danmakuView: DanmakuView? = null

    private var bvid: String = "BV1nK411p7XK" // Default video ID for testing
    private var title: String = "测试视频"
    private var coverUrl: String = ""
    private var videoList: List<com.bili.tv.bili_tv_app.models.Video> = emptyList()
    private var currentIndex: Int = 0

    companion object {
        private const val ARG_BVID = "bvid"
        private const val ARG_TITLE = "title"
        private const val ARG_COVER = "cover"

        fun newInstance(bvid: String, title: String, coverUrl: String): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BVID, bvid)
                    putString(ARG_TITLE, title)
                    putString(ARG_COVER, coverUrl)
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
        loadVideo()
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
                    Log.d("PlayerFragment", "ExoPlayer状态变化: $state")
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            Log.d("PlayerFragment", "ExoPlayer: 正在缓冲")
                            binding.loadingProgress.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            Log.d("PlayerFragment", "ExoPlayer: 准备就绪")
                            binding.loadingProgress.visibility = View.GONE
                            // Ensure audio is enabled
                            it.volume = 1.0f
                            Log.d("PlayerFragment", "音频音量: ${it.volume}")
                        }
                        Player.STATE_ENDED -> {
                            Log.d("PlayerFragment", "ExoPlayer: 播放结束")
                            // Handle video ended - reset player state
                            binding.loadingProgress.visibility = View.GONE
                            // Auto play next video
                            playNextVideo()
                        }
                        Player.STATE_IDLE -> {
                            Log.d("PlayerFragment", "ExoPlayer: 空闲状态")
                            // Handle idle
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.d("PlayerFragment", "ExoPlayer错误: ${error.message}")
                    Log.d("PlayerFragment", "ExoPlayer错误代码: ${error.errorCode}")
                    Log.d("PlayerFragment", "ExoPlayer错误堆栈: ${error.stackTraceToString()}")
                    Toast.makeText(requireContext(), "播放错误: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // Setup danmaku view
        if (SettingsService.danmakuEnabled) {
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

    private fun loadVideo() {
        lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            try {
                Log.d("PlayerFragment", "bvid: $bvid")
                Log.d("PlayerFragment", "title: $title")
                Log.d("PlayerFragment", "coverUrl: $coverUrl")
                Log.d("PlayerFragment", "开始加载视频: $bvid")
                Log.d("PlayerFragment", "开始获取视频信息")
                // Get video info
                val videoInfo = withContext(Dispatchers.IO) {
                    BilibiliApiService.getInstance().getVideoInfo(bvid)
                }

                if (videoInfo == null) {
                    Log.d("PlayerFragment", "获取视频信息失败")
                    Toast.makeText(requireContext(), "获取视频信息失败", Toast.LENGTH_SHORT).show()
                    binding.loadingProgress.visibility = View.GONE
                    return@launch
                }

                videoInfo.let { video ->
                    binding.videoTitle.text = video.title
                    Log.d("PlayerFragment", "视频信息获取成功: ${video.title}, aid: ${video.aid}, cid: ${video.cid}")

                    Log.d("PlayerFragment", "开始获取播放地址")
                    // Get play URL
                    val videoUrl = withContext(Dispatchers.IO) {
                        BilibiliApiService.getInstance().getPlayUrl(
                            video.aid,
                            video.cid,
                            SettingsService.defaultQuality
                        )
                    }

                    if (videoUrl == null) {
                        Log.d("PlayerFragment", "获取播放地址失败")
                        Toast.makeText(requireContext(), "获取播放地址失败", Toast.LENGTH_SHORT).show()
                        binding.loadingProgress.visibility = View.GONE
                        return@launch
                    }

                    videoUrl.let { urlString ->
                        Log.d("PlayerFragment", "视频URL: $urlString")
                        Log.d("PlayerFragment", "开始播放视频")
                        playVideo(urlString)

                        // Temporarily disable danmaku to fix crash
                        // if (SettingsService.danmakuEnabled && video.cid > 0) {
                        //     Log.d("PlayerFragment", "开始加载弹幕")
                        //     loadDanmaku(video.cid)
                        // }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("PlayerFragment", "视频加载失败: ${e.message}")
                Toast.makeText(requireContext(), "视频加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun playVideo(url: String) {
        if (player == null) {
            Log.d("PlayerFragment", "播放器未初始化")
            if (isAdded && context != null) {
                Toast.makeText(context, "播放器未初始化", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (!isAdded || context == null) {
            Log.d("PlayerFragment", "Fragment未添加或Context为空")
            return
        }

        player?.let { exoPlayer ->
            try {
                Log.d("PlayerFragment", "开始播放视频: $url")
                // 重置播放器状态
                try {
                    if (exoPlayer.playbackState != Player.STATE_IDLE) {
                        exoPlayer.stop()
                    }
                    exoPlayer.clearMediaItems()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.d("PlayerFragment", "重置播放器状态失败: ${e.message}")
                }
                
                val mediaItem = MediaItem.fromUri(url)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                // Ensure audio is enabled
                exoPlayer.volume = 1.0f
                Log.d("PlayerFragment", "视频播放开始")
                Log.d("PlayerFragment", "音频音量: ${exoPlayer.volume}")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("PlayerFragment", "播放失败: ${e.message}")
                context?.let { ctx ->
                    if (isAdded) {
                        Toast.makeText(ctx, "视频播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
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
                Log.d("PlayerFragment", "加载弹幕失败: ${e.message}")
            }
        }
    }

    private fun playNextVideo() {
        // TODO: Implement auto play next video functionality
        // Currently disabled because we can't pass video list through Bundle
        Log.d("PlayerFragment", "自动播放下一条视频功能暂未实现")
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
        _binding = null
    }
}
