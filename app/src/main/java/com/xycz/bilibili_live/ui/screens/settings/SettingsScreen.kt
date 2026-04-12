package com.xycz.bilibili_live.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xycz.bilibili_live.ui.viewmodel.SettingsUiState

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onToggleDanmaku: (Boolean) -> Unit,
    onSetDanmakuOpacity: (Float) -> Unit,
    onSetDanmakuSize: (Float) -> Unit,
    onSetQualityLevel: (Int) -> Unit,
    onSetBackgroundPlay: (Boolean) -> Unit,
    onSetAutoResumeLast: (Boolean) -> Unit,
    onAddShieldWord: (String) -> Unit,
    onRemoveShieldWord: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showShieldWordsDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 弹幕设置
            item {
                SettingsSection(title = "弹幕设置")
            }

            item {
                SettingsSwitchItem(
                    title = "显示弹幕",
                    checked = uiState.danmakuEnabled,
                    onCheckedChange = onToggleDanmaku
                )
            }

            item {
                SettingsSliderItem(
                    title = "弹幕透明度",
                    value = uiState.danmakuOpacity,
                    valueRange = 0f..1f,
                    onValueChange = onSetDanmakuOpacity
                )
            }

            item {
                SettingsSliderItem(
                    title = "弹幕大小",
                    value = uiState.danmakuSize,
                    valueRange = 12f..24f,
                    onValueChange = onSetDanmakuSize
                )
            }

            item {
                SettingsClickItem(
                    title = "屏蔽词管理",
                    subtitle = "${uiState.shieldWords.size}个屏蔽词",
                    onClick = { showShieldWordsDialog = true }
                )
            }

            // 播放设置
            item {
                SettingsSection(title = "播放设置")
            }

            item {
                SettingsClickItem(
                    title = "默认画质",
                    subtitle = qualityNames[uiState.qualityLevel] ?: "原画",
                    onClick = { showQualityDialog = true }
                )
            }

            item {
                SettingsSwitchItem(
                    title = "后台播放",
                    checked = uiState.backgroundPlay,
                    onCheckedChange = onSetBackgroundPlay
                )
            }

            item {
                SettingsSwitchItem(
                    title = "自动播放上次观看",
                    checked = uiState.autoResumeLast,
                    onCheckedChange = onSetAutoResumeLast
                )
            }

            // 账号设置
            item {
                SettingsSection(title = "账号")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogoutDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.isLoggedIn) Icons.Default.Logout else Icons.Default.Login,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (uiState.isLoggedIn) "退出登录" else "未登录",
                        fontSize = 16.sp
                    )
                }
            }

            // 关于
            item {
                SettingsSection(title = "关于")
            }

            item {
                SettingsClickItem(
                    title = "版本",
                    subtitle = "1.0.0",
                    onClick = { }
                )
            }
        }
    }

    // 屏蔽词管理弹窗
    if (showShieldWordsDialog) {
        ShieldWordsDialog(
            shieldWords = uiState.shieldWords,
            onDismiss = { showShieldWordsDialog = false },
            onAddWord = onAddShieldWord,
            onRemoveWord = onRemoveShieldWord
        )
    }

    // 画质选择弹窗
    if (showQualityDialog) {
        QualityDialog(
            currentLevel = uiState.qualityLevel,
            onDismiss = { showQualityDialog = false },
            onSelect = {
                onSetQualityLevel(it)
                showQualityDialog = false
            }
        )
    }

    // 退出登录确认弹窗
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onLogout()
                    showLogoutDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 设置区块标题
 */
@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 设置开关项
 */
@Composable
private fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 设置滑动条项
 */
@Composable
private fun SettingsSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, fontSize = 16.sp)
            Text(
                text = if (valueRange.endInclusive > 10) value.toInt().toString() else String.format("%.0f%%", value * 100),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

/**
 * 设置点击项
 */
@Composable
private fun SettingsClickItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 屏蔽词管理弹窗
 */
@Composable
private fun ShieldWordsDialog(
    shieldWords: Set<String>,
    onDismiss: () -> Unit,
    onAddWord: (String) -> Unit,
    onRemoveWord: (String) -> Unit
) {
    var newWord by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("屏蔽词管理") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newWord,
                        onValueChange = { newWord = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入屏蔽词") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newWord.isNotBlank()) {
                            onAddWord(newWord)
                            newWord = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn {
                    items(shieldWords.toList()) { word ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(word, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemoveWord(word) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 画质选择弹窗
 */
@Composable
private fun QualityDialog(
    currentLevel: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择画质") },
        text = {
            Column {
                qualityNames.forEach { (level, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(level) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = level == currentLevel,
                            onClick = { onSelect(level) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private val qualityNames = mapOf(
    0 to "流畅",
    1 to "高清",
    2 to "超清",
    3 to "原画"
)
