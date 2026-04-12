package com.xycz.bilibili_live.service.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * 播放器管理器
 * 使用Media3 ExoPlayer
 */
class PlayerManager(private val context: Context) {

    private var player: ExoPlayer? = null

    private var onPlayerStateChangeListener: ((Boolean) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    /**
     * 初始化播放器
     */
    fun initialize(): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context)
                .setSeekForwardIncrementMs(10000)
                .setSeekBackIncrementMs(10000)
                .build().apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> onPlayerStateChangeListener?.invoke(true)
                                Player.STATE_BUFFERING -> {} // 缓冲中
                                Player.STATE_ENDED -> onPlayerStateChangeListener?.invoke(false)
                                Player.STATE_IDLE -> {}
                            }
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            onErrorListener?.invoke(error.message ?: "播放错误")
                        }
                    })
                }
        }
        return player!!
    }

    /**
     * 播放
     */
    fun play(urls: List<String>, headers: Map<String, String> = emptyMap()) {
        val mediaItems = urls.map { url ->
            MediaItem.Builder()
                .setUri(url)
                .build()
        }

        player?.apply {
            setMediaItems(mediaItems)
            prepare()
            play()
        }
    }

    /**
     * 播放单个URL
     */
    fun playQuality(url: String, headers: Map<String, String> = emptyMap()) {
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    /**
     * 暂停
     */
    fun pause() {
        player?.pause()
    }

    /**
     * 恢复播放
     */
    fun resume() {
        player?.play()
    }

    /**
     * 停止
     */
    fun stop() {
        player?.stop()
    }

    /**
     * 设置音量
     */
    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    /**
     * 获取播放器实例
     */
    fun getPlayer(): ExoPlayer? = player

    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean = player?.isPlaying == true

    /**
     * 设置播放状态监听
     */
    fun setOnPlayerStateChangeListener(listener: (Boolean) -> Unit) {
        onPlayerStateChangeListener = listener
    }

    /**
     * 设置错误监听
     */
    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }

    /**
     * 释放资源
     */
    fun release() {
        player?.release()
        player = null
    }
}
