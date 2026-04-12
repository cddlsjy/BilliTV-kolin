package com.xycz.bilibili_live.ui.screens.live

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.xycz.bilibili_live.domain.model.DanmakuMessage
import com.xycz.bilibili_live.service.danmaku.ConnectionState
import com.xycz.bilibili_live.ui.components.DanmakuView
import com.xycz.bilibili_live.ui.viewmodel.LiveRoomUiState
import com.xycz.bilibili_live.util.formatCount
import androidx.media3.ui.PlayerView

/**
 * 直播间页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRoomScreen(
    uiState: LiveRoomUiState,
    onBack: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onToggleFollow: () -> Unit,
    onShowQuality: () -> Unit,
    onShare: () -> Unit,
    onSendDanmaku: (String) -> Unit,
    onQualitySelect: (Int) -> Unit,
    followList: List<String> = emptyList(),
    recommendList: List<String> = emptyList(),
    onSwitchRoom: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var danmakuInput by remember { mutableStateOf("") }
    var showDanmakuInput by remember { mutableStateOf(false) }
    var currentRoomIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 遥控器按键处理
    fun handleKeyEvent(keyEvent: KeyEvent): Boolean {
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return false
        if (onSwitchRoom == null) return false

        val allRooms = followList + recommendList
        if (allRooms.isEmpty()) return false

        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // 切换到上一个直播间
                if (currentRoomIndex > 0) {
                    currentRoomIndex--
                    onSwitchRoom(allRooms[currentRoomIndex])
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // 切换到下一个直播间
                if (currentRoomIndex < allRooms.size - 1) {
                    currentRoomIndex++
                    onSwitchRoom(allRooms[currentRoomIndex])
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                // 切换到上一个关注直播间
                val currentFollowIndex = followList.indexOf(uiState.detail?.roomId?.toString())
                if (currentFollowIndex > 0) {
                    onSwitchRoom(followList[currentFollowIndex - 1])
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // 切换到下一个关注直播间
                val currentFollowIndex = followList.indexOf(uiState.detail?.roomId?.toString())
                if (currentFollowIndex >= 0 && currentFollowIndex < followList.size - 1) {
                    onSwitchRoom(followList[currentFollowIndex + 1])
                    return true
                }
            }
        }
        return false
    }

    // 画质选项
    val qualityOptions = remember {
        listOf(
            QualityOption(0, "流畅", 80),
            QualityOption(1, "高清", 150),
            QualityOption(2, "超清", 250),
            QualityOption(3, "原画", 400)
        )
    }

    // 处理分享
    LaunchedEffect(uiState.shareUrl) {
        uiState.shareUrl?.let { url ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "我在B站看直播，快来一起看！$url")
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享到"))
            onShare() // 清除分享状态
        }
    }

    // 画质选择底部弹窗
    if (uiState.showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = onShowQuality,
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "选择画质",
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                qualityOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onQualitySelect(option.level)
                                onShowQuality()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Hd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.name,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${option.bitrate}kbps",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.detail?.url?.contains("qn=${option.level}") == true ||
                            (uiState.detail == null && option.level == 2)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "当前",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.detail?.title ?: "直播间") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFollow) {
                        Icon(
                            imageVector = if (uiState.followed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "关注",
                            tint = if (uiState.followed) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
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
                uiState.exoPlayer?.let { player ->
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

                // 弹幕层
                AndroidView(
                    factory = { ctx ->
                        DanmakuView(ctx).apply {
                            this.danmakuAlpha = uiState.danmakuOpacity
                            this.danmakuSize = uiState.danmakuSize * context.resources.displayMetrics.density
                            this.showDanmaku = uiState.danmakuEnabled
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 连接状态指示
                if (uiState.danmakuState != ConnectionState.CONNECTED) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when (uiState.danmakuState) {
                                ConnectionState.CONNECTING -> "弹幕连接中..."
                                ConnectionState.DISCONNECTED -> "弹幕未连接"
                                ConnectionState.ERROR -> "弹幕连接失败"
                                else -> ""
                            },
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 功能按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onToggleDanmaku) {
                    Icon(
                        imageVector = if (uiState.danmakuEnabled) Icons.Filled.ChatBubble else Icons.Filled.ChatBubbleOutline,
                        contentDescription = "弹幕"
                    )
                }
                IconButton(onClick = onShowQuality) {
                    Icon(Icons.Default.Hd, contentDescription = "画质")
                }
                IconButton(onClick = { showDanmakuInput = !showDanmakuInput }) {
                    Icon(Icons.Default.Send, contentDescription = "发弹幕")
                }
            }

            // 弹幕输入框
            if (showDanmakuInput) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = danmakuInput,
                        onValueChange = { danmakuInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("发送弹幕...") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (danmakuInput.isNotBlank()) {
                                onSendDanmaku(danmakuInput)
                                danmakuInput = ""
                            }
                        }
                    ) {
                        Text("发送")
                    }
                }
            }

            Divider()

            // 主播信息
            uiState.detail?.let { detail ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = detail.userName,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${uiState.online.formatCount()}人气",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            // 弹幕列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(8.dp)
            ) {
                items(uiState.danmakuMessages.takeLast(50).reversed()) { message ->
                    DanmakuMessageItem(message = message)
                }
            }
        }
    }
}

/**
 * 弹幕消息项
 */
@Composable
fun DanmakuMessageItem(message: DanmakuMessage.ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = message.userName,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message.message,
            fontSize = 14.sp,
            color = Color(message.color)
        )
    }
}

/**
 * 画质选项
 */
private data class QualityOption(
    val level: Int,
    val name: String,
    val bitrate: Int
)
