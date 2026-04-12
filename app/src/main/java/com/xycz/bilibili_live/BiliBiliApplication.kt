package com.xycz.bilibili_live

import android.app.Application
import com.xycz.bilibili_live.data.local.AppDatabase
import com.xycz.bilibili_live.util.NetworkModule
import com.xycz.bilibili_live.util.SecureSettingsManager
import com.xycz.bilibili_live.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application类
 */
class BiliBiliApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // 初始化安全存储
        SecureSettingsManager.init(this)

        // 初始化网络模块
        NetworkModule.init(this)

        // 初始化数据库
        AppDatabase.getInstance(this)

        // 获取Buvid
        fetchBuvid()
    }

    /**
     * 获取Buvid
     */
    private fun fetchBuvid() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkModule.bilibiliApi.getBuvid()
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    data?.let {
                        val buvid3 = it.buvid3 ?: ""
                        val buvid4 = it.buvid4 ?: ""
                        if (buvid3.isNotEmpty() && buvid4.isNotEmpty()) {
                            SecureSettingsManager.saveBuvid(buvid3, buvid4)
                            NetworkModule.setBuvid(buvid3, buvid4)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
