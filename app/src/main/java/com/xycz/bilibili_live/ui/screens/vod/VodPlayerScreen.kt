package com.xycz.bilibili_live.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.xycz.bilibili_live.domain.model.VodRecommend
import com.xycz.bilibili_live.ui.viewmodel.VodUiState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 点播播放器页面
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun VodPlayerScreen(
    uiState: VodUiState,
    onBack: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onSwitchRecommend: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // 播放器
    DisposableEffect(uiState.playUrl) {
        if (uiState.playUrl != null && exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(uiState.playUrl)
                    .setRequestHeaders(mapOf("Referer" to "https://www.bilibili.com"))
                    .build()
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }

        onDispose {
            // 页面销毁时释放播放器
        }
    }

    // 更新播放URL
    LaunchedEffect(uiState.playUrl) {
        uiState.playUrl?.let { url ->
            exoPlayer?.let { player ->
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setRequestHeaders(mapOf("Referer" to "https://www.bilibili.com"))
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.video?.title ?: "点播",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 播放器区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                // ExoPlayer
                exoPlayer?.let { player ->
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 加载指示器
                if (uiState.isLoadingPlayUrl) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }

                // 错误提示
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = Color.Red,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
            }

            // 视频信息
            uiState.video?.let { video ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = video.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = video.ownerName,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 分P信息
                    if (uiState.currentEpisode != null && video.pages.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "P${uiState.currentEpisode.page}: ${uiState.currentEpisode.title}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSwitchEpisode(-1) },
                                enabled = video.pages.indexOf(uiState.currentEpisode) > 0
                            ) {
                                Text("上一集")
                            }

                            Button(
                                onClick = { onSwitchEpisode(1) },
                                enabled = video.pages.indexOf(uiState.currentEpisode) < video.pages.size - 1
                            ) {
                                Text("下一集")
                            }
                        }
                    }
                }
            }

            Divider()

            // 推荐视频列表
            Text(
                text = "相关推荐",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.recommendVideos) { video ->
                    RecommendVideoItem(
                        video = video,
                        onClick = { onSwitchRecommend(uiState.recommendVideos.indexOf(video)) }
                    )
                }
            }
        }
    }
}

/**
 * 推荐视频项
 */
@Composable
private fun RecommendVideoItem(
    video: VodRecommend,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 视频封面
            coil.compose.AsyncImage(
                model = video.cover,
                contentDescription = video.title,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
            )

            // 视频信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = video.ownerName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = formatDuration(video.duration),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 格式化时长
 */
private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}
