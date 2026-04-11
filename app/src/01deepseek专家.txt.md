针对你提出的三个问题，我逐一分析了代码并给出修复方案和实现建议。

---

### 1. 二维码登录 Bug 分析与修复

**问题现象**：手机扫码后显示登录成功，但电视端没有跳转回首页，实际也未登录。

**根本原因**：在 `LoginFragment.kt` 的 `onLoginSuccess` 方法中，解析登录状态返回的数据结构存在错误，导致 `tokenInfo` 和 `user` 信息未能正确保存到 `AuthService`。

**具体问题点**：

```kt
// LoginFragment.kt 第 94 行附近
data.tokenInfo?.let { tokenInfo ->
    data.cookieInfo?.cookies?.let { cookies ->
        // ...
        // 错误：这里试图从 tokenInfo.tokenInfo?.mid 获取 mid，但实际数据中 tokenInfo 本身已包含 mid
        val userInfo = withContext(Dispatchers.IO) {
            AuthApi.getInstance().getLoginInfo(tokenInfo.tokenInfo?.mid ?: 0)
        }
    }
}
```

根据 `models/Login.kt` 中的定义：
- `LoginStatusData` 包含 `tokenInfo: TokenInfo?`
- `TokenInfo` 中包含 `mid` 字段（在 `TokenInfoDetail` 中），但实际 API 返回的 `data.token_info` 里直接有 `mid` 和 `access_token` 等字段。

同时，`AuthApi.getLoginInfo(mid)` 调用时传入的 `mid` 可能为 0，导致获取用户信息失败。

**修复方案**：

1. 修正 `TokenInfo` 数据类，使其与实际 API 返回匹配。
2. 直接从 `tokenInfo` 中提取 `mid` 和 `access_token`，而非嵌套访问。
3. 确保 `AuthService.saveLoginInfo` 被正确调用。

**修改后的 `LoginFragment.onLoginSuccess`**：

```kotlin
private fun onLoginSuccess(data: com.bili.tv.bili_tv_app.models.LoginStatusData) {
    Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show()

    lifecycleScope.launch {
        try {
            // 从返回数据中提取必要信息
            val tokenInfo = data.tokenInfo
            val cookies = data.cookieInfo?.cookies

            if (tokenInfo != null && cookies != null) {
                val accessToken = tokenInfo.accessToken
                val refreshToken = tokenInfo.refreshToken
                val expiresIn = tokenInfo.expiresIn
                val mid = tokenInfo.mid  // TokenInfo 中应包含 mid

                val cookiesString = cookies.joinToString("; ") { "${it.name}=${it.value}" }

                // 获取用户详细信息
                val userInfoResponse = withContext(Dispatchers.IO) {
                    AuthApi.getInstance().getLoginInfo(mid)
                }

                userInfoResponse.data?.card?.let { card ->
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
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = expiresIn,
                        cookies = cookiesString,
                        user = user
                    )

                    // 登录成功，返回首页
                    parentFragmentManager.popBackStack()
                } ?: run {
                    Toast.makeText(requireContext(), "获取用户信息失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "登录信息不完整", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "登录处理异常: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

同时，修改 `models/Login.kt` 中的 `TokenInfo`，使其更符合实际响应：

```kotlin
data class TokenInfo(
    @SerializedName("mid")
    val mid: Long = 0,
    @SerializedName("access_token")
    val accessToken: String = "",
    @SerializedName("refresh_token")
    val refreshToken: String = "",
    @SerializedName("expires_in")
    val expiresIn: Long = 0
)
```

并删除 `LoginStatusData` 中多余的 `tokenInfo: TokenInfoDetail?` 字段（如果不需要可保留但解析时注意）。

---

### 2. 增加“自动播放上一次播放过的视频”设置开关

**功能描述**：App 启动后，如果开启此设置，自动跳转到上次观看的视频并恢复播放进度。

**实现步骤**：

#### 2.1 在 `SettingsService` 中添加开关字段

```kotlin
// SettingsService.kt
private object Keys {
    // ... 其他 key
    val AUTO_RESUME_LAST_VIDEO = booleanPreferencesKey("auto_resume_last_video")
}

var autoResumeLastVideo: Boolean
    get() = runBlocking { dataStore.data.first()[Keys.AUTO_RESUME_LAST_VIDEO] ?: false }
    set(value) = runBlocking { dataStore.edit { it[Keys.AUTO_RESUME_LAST_VIDEO] = value } }
```

#### 2.2 在设置界面添加开关 UI

在 `fragment_settings.xml` 的“播放设置”区域增加一个 Switch：

```xml
<!-- 自动续播上次视频 -->
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
        android:text="自动播放上次视频"
        android:textColor="@color/text_primary"
        android:textSize="16sp" />

    <Switch
        android:id="@+id/auto_resume_switch"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
</LinearLayout>
```

在 `SettingsFragment.kt` 中绑定并保存状态：

```kotlin
binding.autoResumeSwitch.isChecked = SettingsService.autoResumeLastVideo
binding.autoResumeSwitch.setOnCheckedChangeListener { _, isChecked ->
    SettingsService.autoResumeLastVideo = isChecked
}
```

#### 2.3 保存和恢复播放进度

在 `PlayerFragment` 中，当播放进度更新时，保存当前视频信息和进度到 `SettingsService` 或单独的持久化存储（建议用 DataStore 存储一个 `LastPlaybackInfo` 对象）。

定义数据类：

```kotlin
data class LastPlaybackInfo(
    val bvid: String,
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val progress: Long, // 毫秒
    val duration: Long,
    val timestamp: Long
)
```

在 `SettingsService` 中添加保存和读取方法：

```kotlin
private object Keys {
    val LAST_PLAYBACK_INFO = stringPreferencesKey("last_playback_info")
}

suspend fun saveLastPlaybackInfo(info: LastPlaybackInfo) {
    dataStore.edit { prefs ->
        prefs[Keys.LAST_PLAYBACK_INFO] = gson.toJson(info)
    }
}

suspend fun getLastPlaybackInfo(): LastPlaybackInfo? {
    val json = dataStore.data.first()[Keys.LAST_PLAYBACK_INFO] ?: return null
    return try {
        gson.fromJson(json, LastPlaybackInfo::class.java)
    } catch (e: Exception) {
        null
    }
}
```

在 `PlayerFragment` 中监听播放进度（如每 5 秒保存一次），并在 `onPause` 或 `onStop` 时保存。

#### 2.4 启动时自动跳转

在 `MainActivity` 或 `HomeFragment` 的 `onViewCreated` 中检查开关，若开启且有上次播放记录，则直接跳转到 `PlayerFragment` 并 seek 到指定进度。

```kotlin
lifecycleScope.launch {
    if (SettingsService.autoResumeLastVideo) {
        val lastInfo = SettingsService.getLastPlaybackInfo()
        if (lastInfo != null && System.currentTimeMillis() - lastInfo.timestamp < 7 * 24 * 3600 * 1000) {
            // 一周内有效
            val fragment = PlayerFragment.newInstance(
                lastInfo.bvid,
                lastInfo.title,
                lastInfo.coverUrl,
                lastInfo.progress // 需要修改 PlayerFragment.newInstance 支持传入进度
            )
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            return@launch
        }
    }
    // 否则显示正常首页
}
```

---

### 3. 直播与点播的导航逻辑（上下/左右键切换）

应用同时支持点播（视频）和直播，需要区分当前播放类型，并实现不同的切换逻辑。

#### 3.1 设计思路

- 在 `PlayerFragment` 中增加一个 `playMode` 枚举：`VIDEO`（点播）和 `LIVE`（直播）。
- 点播模式下，维护一个 `videoList`（当前分类/推荐列表）和当前索引 `currentIndex`。
- 直播模式下，维护两个列表：`followingLiveList`（关注列表）和 `recommendLiveList`（推荐列表），分别用于左右键和上下键切换。

#### 3.2 修改 `PlayerFragment`

增加参数传递列表信息，因 `Bundle` 无法直接传递大对象，可使用共享 ViewModel 或单例管理播放队列。

**简化方案**：在 `PlayerFragment` 中通过 `BilibiliApiService` 动态获取下一页/上一页数据，但切换流畅度较差。建议使用全局 `PlayQueueManager` 单例。

**示例代码结构**：

```kotlin
enum class PlayMode { VIDEO, LIVE }

object PlayQueueManager {
    // 点播队列
    var videoList: List<Video> = emptyList()
    var currentVideoIndex: Int = -1

    // 直播队列
    var followingLiveList: List<LiveRoom> = emptyList()
    var recommendLiveList: List<LiveRoom> = emptyList()
    var currentLiveRoom: LiveRoom? = null

    fun getNextVideo(): Video? { ... }
    fun getPreviousVideo(): Video? { ... }
    fun getNextFollowingLive(): LiveRoom? { ... }
    // 等等
}
```

在 `PlayerFragment` 中处理按键事件（需要重写 `onKeyDown` 或使用 `OnKeyListener`）：

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    return when (playMode) {
        PlayMode.VIDEO -> handleVideoKey(keyCode)
        PlayMode.LIVE -> handleLiveKey(keyCode)
    } ?: super.onKeyDown(keyCode, event)
}

private fun handleVideoKey(keyCode: Int): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            playPreviousVideo()
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            playNextVideo()
            true
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            playPreviousCategoryVideo() // 切换分类列表中的上一个视频
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            playNextCategoryVideo()
            true
        }
        else -> false
    }
}

private fun handleLiveKey(keyCode: Int): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            switchToPreviousFollowingLive()
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            switchToNextFollowingLive()
            true
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            switchToPreviousRecommendLive()
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            switchToNextRecommendLive()
            true
        }
        else -> false
    }
}
```

具体切换方法中，更新播放器 URL 并重新加载。

#### 3.3 数据源准备

- 点播的分类列表：可在进入播放页时传入当前视频所属的分类 ID（tid），通过 API 获取同分类视频列表。
- 直播的关注列表：需要登录后调用 B站直播 API 获取关注中的直播间。
- 直播推荐列表：调用 B站直播推荐接口。

**注意**：B站 API 大多需要登录 Cookie，确保 `AuthService.getCookies()` 可用。

---

### 总结修改清单

| 问题 | 涉及文件 | 修改内容 |
|------|----------|----------|
| 1. 登录 Bug | `LoginFragment.kt`<br>`models/Login.kt` | 修正 `onLoginSuccess` 中的数据解析逻辑，正确保存用户信息。 |
| 2. 自动续播 | `SettingsService.kt`<br>`fragment_settings.xml`<br>`SettingsFragment.kt`<br>`PlayerFragment.kt`<br>`MainActivity.kt` | 添加设置项，持久化上次播放信息，启动时自动跳转并恢复进度。 |
| 3. 直播/点播导航 | `PlayerFragment.kt`<br>新增 `PlayQueueManager.kt` | 区分播放模式，重写按键处理，实现上下/左右切换逻辑，对接相应 API。 |

以上方案基于现有代码结构设计，可直接集成。如需完整代码示例，可进一步提供。