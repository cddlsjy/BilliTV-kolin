package com.xycz.bilibili_live.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xycz.bilibili_live.ui.viewmodel.QRStatus

/**
 * 登录页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    qrStatus: QRStatus,
    qrCodeUrl: String?,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStartPolling: () -> Unit,
    loginSuccess: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 登录成功后自动返回
    LaunchedEffect(loginSuccess) {
        if (loginSuccess) {
            kotlinx.coroutines.delay(1000) // 延迟1秒，让用户看到成功提示
            onBack()
        }
    }

    LaunchedEffect(qrStatus) {
        if (qrStatus == QRStatus.Unscanned) {
            onStartPolling()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录B站账号") },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (qrStatus) {
                QRStatus.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在生成二维码...")
                }
                QRStatus.Unscanned, QRStatus.Scanned -> {
                    qrCodeUrl?.let { url ->
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.White)
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "登录二维码",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = when (qrStatus) {
                            QRStatus.Unscanned -> "请使用B站APP扫码登录"
                            QRStatus.Scanned -> "扫码成功，请在手机确认"
                            else -> ""
                        },
                        color = if (qrStatus == QRStatus.Scanned)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (qrStatus == QRStatus.Scanned) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                QRStatus.Expired -> {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "二维码已过期",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRefresh) {
                        Text("刷新")
                    }
                }
                QRStatus.Success -> {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("登录成功！", color = MaterialTheme.colorScheme.primary)
                }
                QRStatus.Failed -> {
                    Text(
                        text = error ?: "登录失败",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRefresh) {
                        Text("重试")
                    }
                }
            }
        }
    }
}
