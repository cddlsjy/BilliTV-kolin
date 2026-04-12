package com.xycz.bilibili_live.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xycz.bilibili_live.data.remote.api.PassportApi
import com.xycz.bilibili_live.util.NetworkModule
import com.xycz.bilibili_live.util.SecureSettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录ViewModel
 */
class LoginViewModel : ViewModel() {

    private val passportApi = NetworkModule.passportApi

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var qrcodeKey: String = ""
    private var pollJob: kotlinx.coroutines.Job? = null

    init {
        generateQRCode()
    }

    /**
     * 生成二维码
     */
    fun generateQRCode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                qrStatus = QRStatus.Loading,
                error = null
            )

            try {
                val response = passportApi.generateQRCode()

                if (response.isSuccessful) {
                    val data = response.body()?.data
                    qrcodeKey = data?.qrcodeKey ?: ""
                    _uiState.value = _uiState.value.copy(
                        qrStatus = QRStatus.Unscanned,
                        qrCodeUrl = data?.url
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        qrStatus = QRStatus.Failed,
                        error = "生成二维码失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    qrStatus = QRStatus.Failed,
                    error = "网络错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 刷新二维码
     */
    fun refreshQRCode() {
        stopPolling()
        generateQRCode()
    }

    /**
     * 开始轮询扫码状态
     */
    fun startPolling() {
        stopPolling()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(2000) // 2秒轮询一次

                if (qrcodeKey.isEmpty()) continue

                try {
                    val response = passportApi.pollQRStatus(qrcodeKey)

                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        val code = response.body()?.code

                        when (code) {
                            0 -> {
                                // 登录成功
                                val cookies = data?.cookieInfo?.cookies
                                val cookieString = cookies?.mapNotNull {
                                    "${it.name}=${it.value}"
                                }?.joinToString("; ") ?: ""

                                // 保存Cookie
                                NetworkModule.setCookie(cookieString)

                                // 保存UID
                                data?.url?.let { url ->
                                    val uidMatch = Regex("mid=(\\d+)").find(url)
                                    val uid = uidMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                                    SecureSettingsManager.saveUid(uid)
                                }

                                _uiState.value = _uiState.value.copy(
                                    qrStatus = QRStatus.Success,
                                    loginSuccess = true
                                )
                                break
                            }
                            86038 -> {
                                // 已扫码未确认
                                _uiState.value = _uiState.value.copy(qrStatus = QRStatus.Scanned)
                            }
                            86101 -> {
                                // 未扫码
                                _uiState.value = _uiState.value.copy(qrStatus = QRStatus.Unscanned)
                            }
                            86090 -> {
                                // 已过期
                                _uiState.value = _uiState.value.copy(qrStatus = QRStatus.Expired)
                                break
                            }
                            else -> {
                                // 其他错误
                                _uiState.value = _uiState.value.copy(
                                    qrStatus = QRStatus.Failed,
                                    error = data?.url ?: "登录失败"
                                )
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 忽略网络错误，继续轮询
                }
            }
        }
    }

    /**
     * 停止轮询
     */
    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * 登出
     */
    fun logout() {
        SecureSettingsManager.clearAll()
        NetworkModule.setCookie("")
        _uiState.value = LoginUiState()
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}

/**
 * 登录UI状态
 */
data class LoginUiState(
    val qrStatus: QRStatus = QRStatus.Loading,
    val qrCodeUrl: String? = null,
    val loginSuccess: Boolean = false,
    val error: String? = null
)

/**
 * 二维码状态
 */
enum class QRStatus {
    Loading,    // 加载中
    Unscanned,  // 未扫码
    Scanned,    // 已扫码
    Expired,    // 已过期
    Success,    // 成功
    Failed      // 失败
}
