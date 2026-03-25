package com.bili.tv.bili_tv_app.screens.home.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bili.tv.bili_tv_app.databinding.FragmentLoginBinding
import com.bili.tv.bili_tv_app.services.AuthService
import com.bili.tv.bili_tv_app.services.api.AuthApi
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private var qrcodeKey: String = ""
    private var isPolling = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        generateQRCode()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun generateQRCode() {
        lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE

            val qrCode = withContext(Dispatchers.IO) {
                AuthApi.getInstance().getQRCode()
            }

            binding.loadingProgress.visibility = View.GONE

            if (qrCode.code == 0 && qrCode.data != null) {
                qrcodeKey = qrCode.data.qrcodeKey

                // Generate QR code image
                try {
                    val barcodeEncoder = BarcodeEncoder()
                    val bitmap = barcodeEncoder.encodeBitmap(
                        qrCode.data.url,
                        com.google.zxing.BarcodeFormat.QR_CODE,
                        400,
                        400
                    )
                    binding.qrCodeImage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "二维码生成失败", Toast.LENGTH_SHORT).show()
                }

                // Start polling
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
                delay(2000) // Poll every 2 seconds

                val status = withContext(Dispatchers.IO) {
                    AuthApi.getInstance().checkQRCodeStatus(qrcodeKey)
                }

                status?.data?.let { data ->
                    when (data.code) {
                        0 -> {
                            // Login success
                            isPolling = false
                            onLoginSuccess(data)
                        }
                        -1 -> {
                            // QR code expired
                            isPolling = false
                            Toast.makeText(requireContext(), "二维码已过期，请重新获取", Toast.LENGTH_SHORT).show()
                            generateQRCode()
                        }
                        -2 -> {
                            // User cancelled
                            // Continue polling
                        }
                        else -> {
                            // Continue polling
                        }
                    }
                }
            }
        }
    }

    private fun onLoginSuccess(data: com.bili.tv.bili_tv_app.models.LoginStatusData) {
        Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show()

        // Save login info
        lifecycleScope.launch {
            data.tokenInfo?.let { tokenInfo ->
                data.cookieInfo?.cookies?.let { cookies ->
                    val cookiesString = cookies.joinToString("; ") { "${it.name}=${it.value}" }

                    // Get user info
                    val userInfo = withContext(Dispatchers.IO) {
                        AuthApi.getInstance().getLoginInfo(tokenInfo.tokenInfo?.mid ?: 0)
                    }

                    userInfo.data?.card?.let { card ->
                        val user = com.bili.tv.bili_tv_app.models.User(
                            mid = card.mid,
                            uname = card.name,
                            face = card.face,
                            sign = card.sign,
                            level = card.level,
                            vipType = card.vipType,
                            vipStatus = card.vipStatus
                        )

                        AuthService.saveLoginInfo(
                            accessToken = tokenInfo.accessToken,
                            refreshToken = tokenInfo.refreshToken,
                            expiresIn = tokenInfo.expiresIn,
                            cookies = cookiesString,
                            user = user
                        )
                    }
                }
            }

            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isPolling = false
        _binding = null
    }
}
