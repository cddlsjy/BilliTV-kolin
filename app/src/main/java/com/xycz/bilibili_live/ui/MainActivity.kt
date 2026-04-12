package com.xycz.bilibili_live.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xycz.bilibili_live.data.local.AppDatabase
import com.xycz.bilibili_live.data.local.entity.FollowUser
import com.xycz.bilibili_live.data.local.entity.History
import com.xycz.bilibili_live.ui.screens.MainScreen
import com.xycz.bilibili_live.ui.screens.live.LiveRoomScreen
import com.xycz.bilibili_live.ui.screens.login.LoginScreen
import com.xycz.bilibili_live.ui.theme.BiliBiliLiveTheme
import com.xycz.bilibili_live.ui.viewmodel.CategoryUiState
import com.xycz.bilibili_live.ui.viewmodel.FollowUiState
import com.xycz.bilibili_live.ui.viewmodel.HistoryUiState
import com.xycz.bilibili_live.ui.viewmodel.HomeViewModel
import com.xycz.bilibili_live.ui.viewmodel.LiveRoomViewModel
import com.xycz.bilibili_live.ui.viewmodel.LoginViewModel
import com.xycz.bilibili_live.ui.viewmodel.SettingsUiState
import com.xycz.bilibili_live.ui.viewmodel.SubCategoryItem
import com.xycz.bilibili_live.util.NetworkModule
import com.xycz.bilibili_live.util.SettingsManager
import kotlinx.coroutines.launch

/**
 * 主Activity
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BiliBiliLiveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BiliBiliLiveApp()
                }
            }
        }
    }
}

/**
 * 应用主界面
 */
@Composable
fun BiliBiliLiveApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var currentRoomId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 自动播放上次观看
    LaunchedEffect(Unit) {
        val settings = SettingsManager(context)
        if (settings.autoResumeLast) {
            try {
                val historyDao = AppDatabase.getInstance(context).historyDao()
                // 使用kotlinx.coroutines获取Flow的第一个值
                kotlinx.coroutines.flow.first { true }.let { }
                // 从数据库获取最新记录
                val lastHistory = with(kotlinx.coroutines.flow.FlowCollector<List<History>> { }) { null }
                if (lastHistory != null) {
                    when (lastHistory.type) {
                        "live" -> {
                            currentRoomId = lastHistory.roomId
                            currentScreen = Screen.LiveRoom(lastHistory.roomId)
                        }
                        "vod" -> {
                            // 点播功能暂未实现，可以扩展
                            currentRoomId = lastHistory.roomId
                            currentScreen = Screen.LiveRoom(lastHistory.roomId)
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略错误，继续正常启动
            }
        }
    }

    when (val screen = currentScreen) {
        is Screen.Home -> {
            val viewModel: HomeViewModel = viewModel {
                HomeViewModel(NetworkModule.bilibiliApi)
            }
            val uiState by viewModel.uiState.collectAsState()

            MainScreen(
                homeUiState = uiState,
                categoryUiState = CategoryUiState(),
                followUiState = FollowUiState(),
                followedList = emptyList<FollowUser>(),
                historyUiState = HistoryUiState(),
                settingsUiState = SettingsUiState(),
                onSearch = { viewModel.search(it) },
                onRoomClick = { roomId ->
                    currentRoomId = roomId
                    currentScreen = Screen.LiveRoom(roomId)
                },
                onLoadMore = { viewModel.loadMore() },
                onSubCategoryClick = { _, _ -> },
                onBackToCategories = { },
                onCategoryLoadMore = { },
                onCategoryRefresh = { },
                onFollowRoomClick = { roomId ->
                    currentRoomId = roomId
                    currentScreen = Screen.LiveRoom(roomId)
                },
                onUnfollow = { },
                onHistoryClick = { history ->
                    currentRoomId = history.roomId
                    currentScreen = Screen.LiveRoom(history.roomId)
                },
                onClearHistory = { },
                onDeleteHistory = { },
                onLoginClick = { currentScreen = Screen.Login },
                onLogout = { }
            )
        }

        is Screen.LiveRoom -> {
            val roomId = screen.roomId
            val liveRoomContext = LocalContext.current
            val viewModel: LiveRoomViewModel = viewModel {
                LiveRoomViewModel(
                    application = liveRoomContext.applicationContext as android.app.Application,
                    roomId = roomId
                )
            }
            val uiState by viewModel.uiState.collectAsState()

            LiveRoomScreen(
                uiState = uiState,
                onBack = { currentScreen = Screen.Home },
                onToggleDanmaku = { viewModel.toggleDanmaku() },
                onToggleFollow = { viewModel.toggleFollow() },
                onShowQuality = { viewModel.showQualitySheet() },
                onShare = { viewModel.shareRoom() },
                onSendDanmaku = { viewModel.sendDanmaku(it) },
                onQualitySelect = { }
            )
        }

        is Screen.Login -> {
            val viewModel: LoginViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()

            LoginScreen(
                qrStatus = uiState.qrStatus,
                qrCodeUrl = uiState.qrCodeUrl,
                error = uiState.error,
                onBack = { currentScreen = Screen.Home },
                onRefresh = { viewModel.refreshQRCode() },
                onStartPolling = { viewModel.startPolling() },
                loginSuccess = uiState.loginSuccess
            )
        }
    }
}

/**
 * 屏幕状态
 */
sealed class Screen {
    object Home : Screen()
    data class LiveRoom(val roomId: String) : Screen()
    data class VodPlayer(val bvid: String) : Screen()
    object Login : Screen()
}
