我们针对三个问题分别进行修复和功能增强。以下是详细的代码修改方案。

---

## 问题1：修复二维码登录成功后无法返回且未真正登录的 Bug

### 原因分析
- `LoginFragment` 中登录成功后调用 `parentFragmentManager.popBackStack()` 返回，但可能存在数据未完全保存或 `AuthService.currentUser` 未及时更新的问题。
- `AuthService.saveLoginInfo` 是挂起函数，虽然正确等待，但后续获取用户信息时可能因网络延迟或数据为空导致保存失败。
- 登录成功后没有强制刷新 `HomeFragment` 的 UI 状态（如用户按钮显示头像）。

### 修复方案
1. 在 `LoginFragment.onLoginSuccess` 中增加详细的空值判断和日志。
2. 确保保存完所有数据后再执行返回操作，并刷新首页用户状态。
3. 在 `AuthService.saveLoginInfo` 中增加数据存储后的校验。

### 修改文件

#### `LoginFragment.kt` 中的 `onLoginSuccess` 方法

```kotlin
private fun onLoginSuccess(data: LoginStatusData) {
    lifecycleScope.launch {
        try {
            // 校验必要数据
            val tokenInfo = data.tokenInfo
            val cookieInfo = data.cookieInfo
            if (tokenInfo == null || cookieInfo?.cookies == null) {
                Toast.makeText(requireContext(), "登录信息不完整，请重试", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val cookiesString = cookieInfo.cookies.joinToString("; ") { "${it.name}=${it.value}" }
            val mid = tokenInfo.tokenInfo?.mid ?: tokenInfo.mid
            if (mid == 0L) {
                Toast.makeText(requireContext(), "获取用户ID失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 获取用户详细信息
            val userInfoResponse = withContext(Dispatchers.IO) {
                AuthApi.getInstance().getLoginInfo(mid)
            }
            val card = userInfoResponse.data?.card
            if (card == null) {
                Toast.makeText(requireContext(), "获取用户信息失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val user = User(
                mid = card.mid,
                uname = card.name,
                face = card.face,
                sign = card.sign,
                level = card.level,
                vipType = card.vipType,
                vipStatus = card.vipStatus
            )

            // 保存登录信息（会同时设置 currentUser）
            AuthService.saveLoginInfo(
                accessToken = tokenInfo.accessToken,
                refreshToken = tokenInfo.refreshToken,
                expiresIn = tokenInfo.expiresIn,
                cookies = cookiesString,
                user = user
            )

            // 延迟一点确保 DataStore 写入完成
            delay(100)

            Toast.makeText(requireContext(), "登录成功，欢迎 ${user.uname}", Toast.LENGTH_SHORT).show()

            // 返回并通知首页刷新用户状态（通过重新创建首页或发送广播）
            parentFragmentManager.popBackStack()
            // 可选：发送一个广播或 EventBus 事件，让 HomeFragment 更新 UI
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "登录异常: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

#### `AuthService.kt` 中确保 `currentUser` 被正确赋值

```kotlin
suspend fun saveLoginInfo(
    accessToken: String,
    refreshToken: String,
    expiresIn: Long,
    cookies: String,
    user: User
) {
    val expiryTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn)

    dataStore.edit { prefs ->
        prefs[Keys.ACCESS_TOKEN] = accessToken
        prefs[Keys.REFRESH_TOKEN] = refreshToken
        prefs[Keys.TOKEN_EXPIRY] = expiryTime
        prefs[Keys.COOKIES] = cookies
        prefs[Keys.USER_INFO] = gson.toJson(user)
    }
    // 确保内存中的 currentUser 更新
    currentUser = user
}
```

#### `HomeFragment.kt` 中增加登录状态监听（可选，通过 `onResume` 刷新用户按钮）

```kotlin
override fun onResume() {
    super.onResume()
    // 刷新用户按钮的显示状态（例如显示头像或登录提示）
    updateUserButton()
}

private fun updateUserButton() {
    if (AuthService.isLoggedIn) {
        binding.userButton.setImageResource(android.R.drawable.ic_menu_myplaces) // 可替换为头像
        binding.userButton.contentDescription = "用户：${AuthService.currentUser?.uname}"
    } else {
        binding.userButton.setImageResource(android.R.drawable.ic_menu_myplaces)
        binding.userButton.contentDescription = "点击登录"
    }
}
```

---

## 问题2：增加“自动播放上一次播放过的视频”设置开关

### 设计思路
- 使用 `SettingsService` 增加一个布尔开关 `autoResumeLastVideo`。
- 在 `PlayerFragment` 中，每5秒或暂停时保存当前视频的 `bvid`、`title`、`cover`、`progress` 到 `DataStore`。
- 在 `SplashFragment` 或 `MainActivity` 启动完成后，检查该开关是否开启，若开启且有历史记录，则直接跳转播放器并 seek 到上次进度。

### 修改文件

#### `SettingsService.kt` 增加新属性

```kotlin
// 在 SettingsService 的 Keys 对象中添加
val AUTO_RESUME_LAST_VIDEO = booleanPreferencesKey("auto_resume_last_video")

// 添加属性访问器
var autoResumeLastVideo: Boolean
    get() = runBlocking { dataStore.data.first()[Keys.AUTO_RESUME_LAST_VIDEO] ?: false }
    set(value) = runBlocking { dataStore.edit { it[Keys.AUTO_RESUME_LAST_VIDEO] = value } }
```

#### 新建 `LastPlaybackRecord.kt` 模型

```kotlin
package com.bili.tv.bili_tv_app.models

import kotlinx.serialization.Serializable

@Serializable
data class LastPlaybackRecord(
    val bvid: String,
    val title: String,
    val coverUrl: String,
    val progress: Long,   // 毫秒
    val timestamp: Long = System.currentTimeMillis()
)
```

#### `PlayerFragment.kt` 添加保存进度逻辑

```kotlin
// 添加定时保存的协程
private var saveProgressJob: Job? = null

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    // ... existing code ...
    startProgressSaver()
}

private fun startProgressSaver() {
    saveProgressJob = lifecycleScope.launch {
        while (isAdded) {
            delay(5000) // 每5秒保存一次
            player?.let { p ->
                if (p.isPlaying && p.currentPosition > 0) {
                    saveCurrentPlaybackProgress(p.currentPosition)
                }
            }
        }
    }
}

private fun saveCurrentPlaybackProgress(positionMs: Long) {
    val record = LastPlaybackRecord(
        bvid = bvid,
        title = title,
        coverUrl = coverUrl,
        progress = positionMs
    )
    // 使用 DataStore 或 Preferences 保存
    lifecycleScope.launch {
        context?.let { ctx ->
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
            prefs.edit().putString("last_playback", Json.encodeToString(record)).apply()
        }
    }
}

override fun onPause() {
    super.onPause()
    // 暂停时立即保存一次
    player?.let { p ->
        if (p.currentPosition > 0) {
            saveCurrentPlaybackProgress(p.currentPosition)
        }
    }
}

override fun onDestroyView() {
    saveProgressJob?.cancel()
    // ... existing code ...
}
```

#### `SplashFragment.kt` 或 `MainActivity` 中检查并跳转

在 `SplashFragment.kt` 的 `initialize()` 中，完成预加载后，检查设置和记录：

```kotlin
private fun navigateToHomeOrResume() {
    val autoResume = SettingsService.autoResumeLastVideo
    val lastRecordJson = context?.let {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(it)
            .getString("last_playback", null)
    }
    
    if (autoResume && !lastRecordJson.isNullOrEmpty()) {
        try {
            val record = Json.decodeFromString<LastPlaybackRecord>(lastRecordJson)
            // 直接跳转播放器
            val fragment = PlayerFragment.newInstance(record.bvid, record.title, record.coverUrl)
            // 需要传递进度，可以在 PlayerFragment 中添加参数
            val args = Bundle().apply {
                putString("bvid", record.bvid)
                putString("title", record.title)
                putString("cover", record.coverUrl)
                putLong("seek_to", record.progress)
            }
            fragment.arguments = args
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        } catch (e: Exception) {
            e.printStackTrace()
            goToHome()
        }
    } else {
        goToHome()
    }
}

private fun goToHome() {
    parentFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, HomeFragment())
        .commit()
}
```

在 `PlayerFragment` 的 `onCreate` 中读取 `seek_to` 参数，并在视频准备就绪后跳转：

```kotlin
private var seekToPosition: Long = 0L

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    arguments?.let {
        bvid = it.getString("bvid", "")
        title = it.getString("title", "")
        coverUrl = it.getString("cover", "")
        seekToPosition = it.getLong("seek_to", 0L)
    }
}

// 在 playVideo 中，prepare 后跳转
player?.addListener(object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY && seekToPosition > 0) {
            player?.seekTo(seekToPosition)
            seekToPosition = 0 // 只跳一次
        }
    }
})
```

#### `SettingsFragment.kt` 添加开关

在布局 `fragment_settings.xml` 中增加一行 Switch，并在 `SettingsFragment` 中绑定：

```xml
<!-- 在 Interface Settings 卡片内添加 -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:layout_marginBottom="16dp">
    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="自动续播上次视频"
        android:textColor="@color/text_primary"
        android:textSize="16sp" />
    <Switch
        android:id="@+id/auto_resume_switch"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
</LinearLayout>
```

```kotlin
// 在 SettingsFragment.setupUI() 中
binding.autoResumeSwitch.isChecked = SettingsService.autoResumeLastVideo
binding.autoResumeSwitch.setOnCheckedChangeListener { _, isChecked ->
    SettingsService.autoResumeLastVideo = isChecked
}
```

---

## 问题3：增加直播功能，并实现点播/直播模式下的上下左右键切换

### 整体架构
- 新增 `LiveFragment` 用于显示直播间列表和播放直播流。
- 修改 `PlayerFragment` 使其能同时处理点播（`Video`）和直播（`LiveRoom`）两种数据源。
- 通过 `PlayerFragment` 的 `arguments` 传入一个 `type` 字段（`"vod"` 或 `"live"`）以及对应的 ID。
- 实现键盘事件：左右键切换上一集/下一集（点播）或切换直播间（直播）；上下键切换推荐列表中的上一个/下一个视频/直播间。
- 需要获取直播列表 API：关注列表、推荐列表（B站没有直接的推荐直播间接口，可使用热门直播或分区直播替代）。

### 新增直播相关 API（在 `BilibiliApiService.kt` 中）

```kotlin
// 获取直播推荐列表（热门直播）
suspend fun getHotLiveRooms(page: Int = 1): List<LiveRoom> {
    val url = "$baseUrl/xlive/web-interface/v1/index/getHot?page=$page&page_size=20"
    val request = Request.Builder().url(url)
        .addHeader("User-Agent", USER_AGENT)
        .addHeader("Referer", "https://live.bilibili.com")
        .build()
    return try {
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        val json = JSONObject(body)
        val list = json.getJSONObject("data").getJSONArray("list")
        (0 until list.length()).map { i ->
            val obj = list.getJSONObject(i)
            LiveRoom(
                roomId = obj.getLong("roomid"),
                title = obj.getString("title"),
                uname = obj.getString("uname"),
                cover = obj.getString("user_cover"),
                viewer = obj.getInt("online")
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// 获取用户关注的直播间列表（需要登录）
suspend fun getFollowedLiveRooms(): List<LiveRoom> {
    val url = "$baseUrl/xlive/web-interface/v1/index/following?page=1&page_size=20"
    val cookies = AuthService.getCookies()
    val request = Request.Builder().url(url)
        .addHeader("User-Agent", USER_AGENT)
        .addHeader("Referer", "https://live.bilibili.com")
        .addHeader("Cookie", cookies)
        .build()
    // 解析逻辑类似...
}

data class LiveRoom(
    val roomId: Long,
    val title: String,
    val uname: String,
    val cover: String,
    val viewer: Int
)
```

### 新增 `LiveFragment.kt`

类似于 `HomeFragment`，展示直播间列表，点击进入 `PlayerFragment` 并标记为直播模式。

### 修改 `PlayerFragment.kt` 支持双模式并处理按键

```kotlin
class PlayerFragment : Fragment() {
    private var playType: String = "vod" // "vod" or "live"
    private var currentVideo: Video? = null
    private var currentLiveRoom: LiveRoom? = null
    private var currentVideoList: List<Video> = emptyList()   // 用于上下键切换
    private var currentLiveList: List<LiveRoom> = emptyList() // 用于上下键切换
    
    // 左右键切换相关
    private var episodeList: List<Video> = emptyList() // 剧集列表（点播）
    private var followedRooms: List<LiveRoom> = emptyList() // 关注直播间（直播左右键）
    private var recommendRooms: List<LiveRoom> = emptyList() // 推荐直播间（直播上下键）
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 监听按键
        view.isFocusable = true
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> onLeftKey()
                    KeyEvent.KEYCODE_DPAD_RIGHT -> onRightKey()
                    KeyEvent.KEYCODE_DPAD_UP -> onUpKey()
                    KeyEvent.KEYCODE_DPAD_DOWN -> onDownKey()
                    else -> return@setOnKeyListener false
                }
                true
            } else false
        }
        view.requestFocus()
    }
    
    private fun onLeftKey() {
        if (playType == "vod") {
            // 切换到上一集（需要获取剧集列表）
            playPreviousEpisode()
        } else {
            // 切换到关注列表中的上一个直播间
            playPreviousFollowedLive()
        }
    }
    
    private fun onRightKey() {
        if (playType == "vod") {
            playNextEpisode()
        } else {
            playNextFollowedLive()
        }
    }
    
    private fun onUpKey() {
        if (playType == "vod") {
            // 切换到推荐视频列表中的上一个视频
            playPreviousRecommendVideo()
        } else {
            // 切换到推荐直播间列表中的上一个直播间
            playPreviousRecommendLive()
        }
    }
    
    private fun onDownKey() {
        if (playType == "vod") {
            playNextRecommendVideo()
        } else {
            playNextRecommendLive()
        }
    }
    
    // 实现具体切换逻辑，需要提前加载列表并维护当前索引
    // 例如：
    private fun playNextEpisode() {
        val currentIndex = episodeList.indexOfFirst { it.bvid == currentVideo?.bvid }
        if (currentIndex >= 0 && currentIndex < episodeList.size - 1) {
            val nextVideo = episodeList[currentIndex + 1]
            loadVideo(nextVideo)
        }
    }
    
    private fun playNextRecommendVideo() {
        // 使用全局推荐列表 currentVideoList 或单独加载
        // ...
    }
    
    // 直播切换类似，调用 loadLiveRoom(roomId)
}
```

### 切换数据源时的加载方法

需要重构 `loadVideo()` 和新增 `loadLiveRoom()`，并在切换时更新播放器内容。

### 注意点
- 直播流播放：使用 `getLiveUrl` 获取 m3u8 或 flv 地址，同样通过 ExoPlayer 播放。
- 上下键的数据源：点播推荐视频列表可以在进入播放器时预加载（调用 `getRecommendVideos`），直播推荐列表调用 `getHotLiveRooms`。
- 左右键数据源：点播剧集列表需要从视频信息中解析 `season_id` 调用 `getSeasonEpisodes` 接口（需补充）；直播左右键使用关注列表 `getFollowedLiveRooms`。

由于接口较多且实现细节复杂，以上为架构和关键代码示例。实际开发中需要完善 API 调用和错误处理。

---

## 总结
以上三个问题的解决方案涵盖了：
1. 修复登录逻辑，增加空值校验和 UI 刷新。
2. 添加自动续播功能，通过 DataStore 保存播放记录。
3. 搭建直播模块基础，并在播放器中实现双模式下的上下左右切换逻辑。

请根据项目实际 API 返回格式微调解析代码。如有需要，我可以提供更完整的文件源码。