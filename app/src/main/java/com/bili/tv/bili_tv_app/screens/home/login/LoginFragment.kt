package com.bili.tv.bili_tv_app.screens.home.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bili.tv.bili_tv_app.databinding.FragmentLoginBinding
import com.bili.tv.bili_tv_app.models.User
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.api.AuthApi
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private var qrcodeKey = ""
    private var isPolling = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        generateQRCode()
    }

    private fun generateQRCode() {
        lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE
            val qrCode = withContext(Dispatchers.IO) { AuthApi.getInstance().getQRCode() }
            binding.loadingProgress.visibility = View.GONE

            if (qrCode.code == 0 && qrCode.data != null) {
                qrcodeKey = qrCode.data.qrcodeKey
                val size = (resources.displayMetrics.widthPixels * 0.7).toInt().coerceAtMost(500)
                val bitmap = BarcodeEncoder().encodeBitmap(
                    qrCode.data.url,
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    size, size
                )
                binding.qrCodeImage.setImageBitmap(bitmap)
                startPolling()
            } else {
                Toast.makeText(requireContext(), "获取二维码失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        lifecycleScope.launch {
            while (isPolling && qrcodeKey.isNotEmpty()) {
                delay(2000)
                val status = withContext(Dispatchers.IO) { AuthApi.getInstance().checkQRCodeStatus(qrcodeKey) }
                status?.data?.let { data ->
                    when (data.code) {
                        0 -> { isPolling = false; onLoginSuccess(data) }
                        86038 -> { isPolling = false; Toast.makeText(requireContext(), "二维码已过期", Toast.LENGTH_SHORT).show(); generateQRCode() }
                        86101 -> { /* 已扫码，未确认，继续轮询 */ }
                        else -> { /* 继续轮询 */ }
                    }
                }
            }
        }
    }

    private fun onLoginSuccess(data: com.bili.tv.bili_tv_app.models.LoginStatusData) {
        lifecycleScope.launch {
            try {
                val cookiesList = data.cookieInfo?.cookies
                if (cookiesList.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "登录信息不完整", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val cookiesString = cookiesList.joinToString("; ") { "${it.name}=${it.value}" }

                // 优先通过 Cookie 获取用户信息
                val navResp = withContext(Dispatchers.IO) { AuthApi.getInstance().getUserInfoByCookie(cookiesString) }
                val user = if (navResp.code == 0 && navResp.data?.card != null) {
                    val card = navResp.data.card!!
                    User(
                        mid = card.mid,
                        uname = card.name,
                        face = card.face,
                        sign = card.sign,
                        level = card.levelInfo?.currentLevel ?: 0,
                        vipType = card.vipInfo?.type ?: 0,
                        vipStatus = card.vipInfo?.status ?: 0
                    )
                } else {
                    // 降级：从 tokenInfo.mid 获取
                    val mid = data.tokenInfo?.mid ?: 0
                    if (mid > 0) {
                        val cardResp = withContext(Dispatchers.IO) { AuthApi.getInstance().getLoginInfo(mid) }
                        cardResp.data?.card?.let {
                            User(
                                mid = it.mid,
                                uname = it.name,
                                face = it.face,
                                sign = it.sign,
                                level = it.levelInfo?.currentLevel ?: 0,
                                vipType = it.vipInfo?.type ?: 0,
                                vipStatus = it.vipInfo?.status ?: 0
                            )
                        } ?: return@launch
                    } else return@launch
                }

                AuthService.saveLoginInfo(
                    accessToken = data.tokenInfo?.accessToken ?: "",
                    refreshToken = data.tokenInfo?.refreshToken ?: "",
                    expiresIn = data.tokenInfo?.expiresIn ?: 0,
                    cookies = cookiesString,
                    user = user
                )
                delay(300)
                parentFragmentManager.popBackStack()
                Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "登录异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isPolling = false
        _binding = null
    }
}