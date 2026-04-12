package com.xycz.bilibili_live.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xycz.bilibili_live.data.local.entity.FollowUser
import com.xycz.bilibili_live.data.local.entity.History
import com.xycz.bilibili_live.ui.screens.category.CategoryScreen
import com.xycz.bilibili_live.ui.screens.follow.FollowScreen
import com.xycz.bilibili_live.ui.screens.history.HistoryScreen
import com.xycz.bilibili_live.ui.screens.home.HomeScreen
import com.xycz.bilibili_live.ui.screens.live.LiveRoomScreen
import com.xycz.bilibili_live.ui.screens.login.LoginScreen
import com.xycz.bilibili_live.ui.screens.mine.MineScreen
import com.xycz.bilibili_live.ui.screens.settings.SettingsScreen
import com.xycz.bilibili_live.ui.viewmodel.CategoryUiState
import com.xycz.bilibili_live.ui.viewmodel.FollowUiState
import com.xycz.bilibili_live.ui.viewmodel.HistoryUiState
import com.xycz.bilibili_live.ui.viewmodel.HomeUiState
import com.xycz.bilibili_live.ui.viewmodel.LiveRoomUiState
import com.xycz.bilibili_live.ui.viewmodel.LiveRoomViewModel
import com.xycz.bilibili_live.ui.viewmodel.LiveRoomViewModelFactory
import com.xycz.bilibili_live.ui.viewmodel.LoginUiState
import com.xycz.bilibili_live.ui.viewmodel.LoginViewModel
import com.xycz.bilibili_live.ui.viewmodel.SettingsUiState

/**
 * 主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeUiState: HomeUiState,
    categoryUiState: CategoryUiState,
    followUiState: FollowUiState,
    followedList: List<FollowUser>,
    historyUiState: HistoryUiState,
    settingsUiState: SettingsUiState,
    onSearch: (String) -> Unit,
    onRoomClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSubCategoryClick: (com.xycz.bilibili_live.ui.viewmodel.SubCategoryItem, String) -> Unit,
    onBackToCategories: () -> Unit,
    onCategoryLoadMore: () -> Unit,
    onCategoryRefresh: () -> Unit,
    onFollowRoomClick: (String) -> Unit,
    onUnfollow: (FollowUser) -> Unit,
    onHistoryClick: (History) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteHistory: (History) -> Unit,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // 判断是否显示底部导航栏
    val showBottomBar = currentRoute in listOf(
        "home",
        "category",
        "follow",
        "mine"
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            // 首页
            composable("home") {
                HomeScreen(
                    uiState = homeUiState,
                    onSearch = onSearch,
                    onRoomClick = onRoomClick,
                    onLoadMore = onLoadMore
                )
            }

            // 分类页面
            composable("category") {
                CategoryScreen(
                    uiState = categoryUiState,
                    onSubCategoryClick = onSubCategoryClick,
                    onBackToCategories = onBackToCategories,
                    onRoomClick = onRoomClick,
                    onLoadMore = onCategoryLoadMore,
                    onRefresh = onCategoryRefresh
                )
            }

            // 关注页面
            composable("follow") {
                FollowScreen(
                    followedList = followedList,
                    uiState = followUiState,
                    onUserClick = { user -> onFollowRoomClick(user.roomId) },
                    onUnfollow = onUnfollow
                )
            }

            // 我的页面
            composable("mine") {
                MineScreen(
                    settingsState = settingsUiState,
                    onLoginClick = onLoginClick,
                    onHistoryClick = { navController.navigate("history") },
                    onSettingsClick = { navController.navigate("settings") }
                )
            }

            // 直播间页面
            composable(
                route = "live/{roomId}",
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                val context = LocalContext.current
                val viewModel: LiveRoomViewModel = viewModel(
                    factory = LiveRoomViewModelFactory(
                        application = context.applicationContext as Application,
                        roomId = roomId
                    )
                )
                val uiState by viewModel.uiState.collectAsState()
                LiveRoomScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onToggleDanmaku = { viewModel.toggleDanmaku() },
                    onToggleFollow = { viewModel.toggleFollow() },
                    onShowQuality = { viewModel.showQualitySheet() },
                    onShare = { viewModel.shareRoom() },
                    onSendDanmaku = { viewModel.sendDanmaku(it) },
                    onQualitySelect = { /* 画质切换暂不实现 */ }
                )
            }

            // 登录页面
            composable("login") {
                val viewModel: LoginViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                LoginScreen(
                    qrStatus = uiState.qrStatus,
                    qrCodeUrl = uiState.qrCodeUrl,
                    error = uiState.error,
                    onBack = { navController.popBackStack() },
                    onRefresh = { viewModel.refreshQRCode() },
                    onStartPolling = { viewModel.startPolling() }
                )
            }

            // 历史记录页面
            composable("history") {
                HistoryScreen(
                    historyList = historyUiState.historyList,
                    onBack = { navController.popBackStack() },
                    onHistoryClick = onHistoryClick,
                    onClearAll = onClearHistory,
                    onDeleteHistory = onDeleteHistory
                )
            }

            // 设置页面
            composable("settings") {
                SettingsScreen(
                    uiState = settingsUiState,
                    onBack = { navController.popBackStack() },
                    onToggleDanmaku = { },
                    onSetDanmakuOpacity = { },
                    onSetDanmakuSize = { },
                    onSetQualityLevel = { },
                    onSetBackgroundPlay = { },
                    onAddShieldWord = { },
                    onRemoveShieldWord = { },
                    onLogout = onLogout
                )
            }
        }
    }
}

// 底部导航栏项
private val tabs = listOf(
    TabItem("首页", Icons.Default.Home, "home"),
    TabItem("分类", Icons.Default.Category, "category"),
    TabItem("关注", Icons.Default.Favorite, "follow"),
    TabItem("我的", Icons.Default.Person, "mine")
)

data class TabItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)
