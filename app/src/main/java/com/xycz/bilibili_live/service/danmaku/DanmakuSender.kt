package com.xycz.bilibili_live.service.danmaku

import com.xycz.bilibili_live.data.remote.api.BilibiliApi
import com.xycz.bilibili_live.util.SecureSettingsManager

/**
 * 弹幕发送器
 */
class DanmakuSender(
    private val roomId: String,
    private val api: BilibiliApi
) {
    /**
     * 发送弹幕
     */
    suspend fun send(message: String): Result<Boolean> {
        return try {
            val csrf = SecureSettingsManager.getCsrf()

            if (csrf.isEmpty()) {
                return Result.failure(Exception("未登录，无法发送弹幕"))
            }

            val rnd = System.currentTimeMillis().toString()

            val response = api.sendDanmaku(
                msg = message,
                roomId = roomId,
                rnd = rnd,
                csrf = csrf,
                csrfToken = csrf
            )

            if (response.isSuccessful && response.body()?.code == 0) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.message ?: "发送失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
