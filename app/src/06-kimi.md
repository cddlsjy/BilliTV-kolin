 我已经完成了所有修改。以下是完整的修改总结：

---

## 修改总结

### 1. 修复扫码登录Bug

**问题原因：**
- 状态码判断错误：原代码使用 `-1`, `-2` 等，实际应为 `86038` (过期), `86011` (取消) 等 
- 用户信息API响应解析错误：`level` 和 `vip` 字段是嵌套对象，不是直接字段 

**修复内容：**
- `LoginFragment.kt`: 修正状态码判断逻辑，修复用户信息获取
- `AuthApi.kt`: 修正 `UserCard` 数据结构，添加嵌套的 `LevelInfo` 和 `VipInfo` 类

---

### 2. 增加自动播放上次视频设置

**新增功能：**
- 设置界面添加"自动播放上次视频"开关
- 启动应用时自动恢复上次播放的视频
- 播放视频时自动保存播放记录

**修改文件：**
- `SettingsService.kt`: 添加 `autoResumeLastVideo` 属性和相关方法
- `SettingsFragment.kt`: 绑定开关UI
- `fragment_settings.xml`: 添加开关布局
- `MainActivity.kt`: 启动时检查并恢复上次视频
- `PlayerFragment.kt`: 播放时保存视频信息

---

### 3. 实现直播/点播 D-pad 导航

**直播模式导航：**
- **左右键**: 切换关注列表的上/下一个直播间
- **上下键**: 切换推荐列表的上/下一个直播间

**点播模式导航：**
- **左右键**: 切换上一集/下一集（需接入番剧API）
- **上下键**: 切换分类列表的上一个/下一个视频

**修改文件：**
- `PlayerFragment.kt`: 大幅重构，添加直播支持、按键处理、房间切换逻辑
- `BilibiliApiService.kt`: 添加直播API（关注列表、推荐列表、直播流地址）
- `MainActivity.kt`: 转发按键事件给PlayerFragment

---

### 文件替换清单

1. `LoginFragment.kt` - 修复登录状态码和用户信息解析
2. `AuthApi.kt` - 修正UserCard数据结构
3. `SettingsService.kt` - 添加自动播放上次视频功能
4. `SettingsFragment.kt` - 添加设置开关绑定
5. `fragment_settings.xml` - 添加开关UI
6. `PlayerFragment.kt` - 实现直播/点播导航
7. `BilibiliApiService.kt` - 添加直播API
8. `MainActivity.kt` - 支持按键转发和自动恢复视频

所有代码已在上面的回复中提供，直接复制替换即可使用。