结论：方案05 (glm) 为最优方案
登录部分：通过 Cookie 调用 /nav 接口获取用户信息，可靠性最高；多重降级（tokenInfo.mid → Cookie中DedeUserID）确保成功率。

自动播放：完整实现开关、保存、恢复，支持进度跳转。

方向键切换：点播模式左右切分集、上下切分类视频；直播模式左右切关注列表、上下切推荐列表；含防抖、浮层提示、后台预加载。

代码完整，可直接复制替换，无需二次补充。

二、整合后的修改大纲（供 trae 助手使用）
目标
修复二维码登录成功后电视端未登录、不返回的 Bug。

增加“自动播放上一次视频”设置开关。

实现直播功能，并支持点播/直播模式下遥控器上下/左右键切换内容。

模块一：修复登录 Bug
1.1 修改 models/Login.kt
操作：在 TokenInfo 类中增加 mid 字段（B站API直接返回在顶层）。

kotlin
data class TokenInfo(
    @SerializedName("mid")
    val mid: Long = 0,          // 新增
    @SerializedName("access_token")
    val accessToken: String = "",
    @SerializedName("refresh_token")
    val refreshToken: String = "",
    @SerializedName("expires_in")
    val expiresIn: Long = 0,
    @SerializedName("token_info")
    val tokenInfo: TokenInfoDetail? = null
)
1.2 重写 services/AuthService.kt
操作：改用 SharedPreferences 持久化（简单可靠），存储 Cookie、用户信息、登录状态。

方法：init, restoreLoginState, saveLoginInfo, getCookieValue, getDedeUserId, clearLogin

关键字段：isLoggedIn, currentUser, cookies, accessToken

1.3 修改 services/api/AuthApi.kt
操作：增加 getUserInfoByCookie(cookies: String) 方法（调用 /x/web-interface/nav），并修正 UserCard 数据结构（添加 LevelInfo/VipInfo 嵌套类）。

1.4 修改 screens/home/login/LoginFragment.kt
操作：重写 onLoginSuccess 方法。

优先通过 Cookie 调用 getUserInfoByCookie 获取用户信息。

降级方案：从 tokenInfo.mid 获取；最终降级：从 Cookie 提取 DedeUserID。

保存成功后延迟 300ms 再 popBackStack，确保持久化完成。

修正状态码判断（86038 过期，86101 已扫码未确认等）。

模块二：自动播放上次视频
2.1 修改 services/SettingsService.kt
操作：增加以下属性及方法。

kotlin
var autoPlayLastVideo: Boolean
var lastPlayedBvid: String
var lastPlayedTitle: String
var lastPlayedCover: String
var lastPlayedCid: Long
var lastPlayedProgress: Long
var lastPlayedIsLive: Boolean
var lastPlayedRoomId: Long

fun saveLastPlayedVideo(...)
fun clearLastPlayedVideo()
fun hasLastPlayedVideo(): Boolean
2.2 修改 res/layout/fragment_settings.xml
操作：在“播放设置”卡片内增加带说明文字的 Switch（id=autoPlayLastVideoSwitch）。

2.3 修改 screens/home/settings/SettingsFragment.kt
操作：绑定开关，保存状态；关闭时清除记录。

2.4 修改 screens/home/HomeFragment.kt
操作：在 loadContent() 完成后调用 checkAndAutoPlayLastVideo()。

检查 autoPlayLastVideo 开关和记录有效性。

延迟 500ms 后跳转播放器，传递上次视频信息。

2.5 修改 screens/player/PlayerFragment.kt
操作：

在 onPause 和 onDestroyView 中调用 savePlaybackProgress() 保存当前进度。

在 onCreate 中读取 seek_to 参数，onPlaybackStateChanged 中 STATE_READY 时跳转进度。

模块三：直播功能 + 方向键切换
3.1 新增模型文件
文件名	内容
models/Episode.kt	分集信息（cid, page, part, duration）
models/VideoDetail.kt	视频详情（含 pages 列表、getPageIndex）
models/LiveRoom.kt	直播间信息 + 直播流响应解析类
3.2 修改 services/api/BilibiliApiService.kt
操作：增加三个直播 API。

kotlin
fun getFollowLiveRooms(page: Int): List<LiveRoom>      // 关注列表（左右键）
fun getRecommendLiveRooms(page: Int): List<LiveRoom>   // 推荐列表（上下键）
fun getLiveStreamUrl(roomId: Long): String?           // 获取直播流地址
同时修改 getVideoInfo 返回 VideoDetail?，getPlayUrl 支持指定 cid。

3.3 修改 res/layout/fragment_player.xml
操作：增加切换提示浮层（switchOverlay）和底部提示文字（hintText）。

3.4 重写 screens/player/PlayerFragment.kt
操作：完全重写，支持点播/直播双模式。

模式判断：isLiveMode（从 arguments 传入）。

点播数据：

episodeList（分集列表，来自 VideoDetail.pages）→ 左右键切换

categoryVideoList（分类视频列表，从 HomeFragment 传入）→ 上下键切换

直播数据：

followLiveList（关注列表）→ 左右键切换

recommendLiveList（推荐列表）→ 上下键切换

按键处理：

在 PlayerView 上设置 setOnKeyListener，拦截 D-pad 四键（返回 true 阻止 ExoPlayer 默认快进快退）。

防抖：keyDebounceMs = 600L

切换方法：

switchEpisode(direction) / switchCategoryVideo(direction)

switchFollowLiveRoom(direction) / switchRecommendLiveRoom(direction)

切换提示：居中浮层显示 2 秒，显示标题和进度。

直播预加载：后台协程加载关注/推荐列表，提升切换速度。

播放记录：点播模式下保存 bvid/title/cover/cid，直播模式下保存 roomId。

三、文件修改清单（供 trae 助手直接使用）
需要替换/修改的现有文件
路径	操作
models/Login.kt	修改 TokenInfo，增加 mid 字段
services/AuthService.kt	完全重写（基于 SharedPreferences）
services/api/AuthApi.kt	增加 getUserInfoByCookie，修正 UserCard 结构
screens/home/login/LoginFragment.kt	重写 onLoginSuccess，修正状态码和用户信息获取
services/SettingsService.kt	增加自动播放相关属性及方法
res/layout/fragment_settings.xml	增加自动播放开关
screens/home/settings/SettingsFragment.kt	绑定自动播放开关
screens/home/HomeFragment.kt	增加 checkAndAutoPlayLastVideo 方法
services/api/BilibiliApiService.kt	增加三个直播 API，修改 getVideoInfo 返回 VideoDetail
res/layout/fragment_player.xml	增加切换提示浮层和底部提示
screens/player/PlayerFragment.kt	完全重写
需要新增的文件
路径	内容
models/Episode.kt	分集数据类
models/VideoDetail.kt	视频详情数据类（含 pages）
models/LiveRoom.kt	直播间及直播流响应数据类
四、注意事项
登录部分：新 AuthService 使用 SharedPreferences，需在 MainActivity 或 Application 中调用 AuthService.init(context)。

直播 API：关注列表需要登录 Cookie，未登录时返回空列表；推荐列表无需登录。

分集切换：依赖 VideoDetail.pages 字段，需要 B站 API 返回该数据（通常 /x/web-interface/view 会返回 pages）。

分类视频列表：从 HomeFragment 通过 PlayerFragment.newInstance 的 categoryVideoList 参数传入，用于上下键切换。

防抖时间：600ms 可避免按键过快导致多次切换。

弹幕兼容：原有弹幕代码保留，仅在点播模式下加载弹幕（直播无弹幕）。

