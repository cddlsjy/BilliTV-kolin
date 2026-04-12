package com.xycz.bilibili_live.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * WBI签名工具
 * B站部分API需要WBI签名
 */
object WbiSigner {
    private var imgKey: String = ""
    private var subKey: String = ""
    private var lastFetchTime: Long = 0
    private val cacheDuration = 24 * 60 * 60 * 1000L // 24小时缓存

    // WBI混淆表
    private val mixinKeyEncTab = listOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
        26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    /**
     * 获取带签名的参数
     */
    suspend fun getSignedParams(params: Map<String, String>): Map<String, String> = withContext(Dispatchers.IO) {
        // 检查缓存是否过期
        if (System.currentTimeMillis() - lastFetchTime > cacheDuration ||
            imgKey.isEmpty() || subKey.isEmpty()
        ) {
            fetchWbiKeys()
        }

        val wts = System.currentTimeMillis() / 1000
        val signedParams = params.toMutableMap()
        signedParams["wts"] = wts.toString()

        // 按 key 排序并过滤特殊字符
        val filteredParams = signedParams.entries
            .sortedBy { it.key }
            .associate { (k, v) -> k to v.filter { c -> "!'()*".contains(c).not() } }

        // 生成查询字符串
        val query = filteredParams.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }

        // 计算 w_rid
        val mixinKey = getMixinKey(imgKey + subKey)
        val wRid = md5("$query$mixinKey")

        filteredParams + ("w_rid" to wRid)
    }

    /**
     * 获取WBI密钥
     */
    private suspend fun fetchWbiKeys() {
        try {
            val response = NetworkModule.passportApi.getWbiKeys()
            if (response.isSuccessful) {
                val data = response.body()?.data
                imgKey = data?.wbiImg?.imgUrl?.substringAfterLast("/")?.substringBefore(".") ?: ""
                subKey = data?.wbiImg?.subUrl?.substringAfterLast("/")?.substringBefore(".") ?: ""
                lastFetchTime = System.currentTimeMillis()

                // 保存到缓存
                if (imgKey.isNotEmpty() && subKey.isNotEmpty()) {
                    SecureSettingsManager.saveWbiKeys(imgKey, subKey)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 尝试从缓存加载
            SecureSettingsManager.getWbiKeys()?.let { (savedImg, savedSub) ->
                imgKey = savedImg
                subKey = savedSub
            }
        }
    }

    /**
     * 获取混淆后的key
     */
    private fun getMixinKey(origin: String): String {
        return mixinKeyEncTab.mapIndexed { index, _ ->
            origin.getOrElse(index) { '0' }
        }.joinToString("").take(32)
    }

    /**
     * MD5计算
     */
    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            ""
        }
    }

    /**
     * 生成wts时间戳
     */
    fun getWts(): Long = System.currentTimeMillis() / 1000

    /**
     * 生成随机ID
     */
    fun generateWwebid(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..64).map { chars.random() }.joinToString("")
    }
}
