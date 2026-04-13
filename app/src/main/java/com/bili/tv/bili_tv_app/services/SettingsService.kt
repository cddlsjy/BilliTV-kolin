package com.bili.tv.bili_tv_app.services

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsService {

    private lateinit var dataStore: DataStore<Preferences>
    private var sharedPreferences: SharedPreferences? = null

    // Keys
    private object Keys {
        // Playback settings
        val DEFAULT_QUALITY = intPreferencesKey("default_quality")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val AUTO_NEXT = booleanPreferencesKey("auto_next")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val DANMAKU_ENABLED = booleanPreferencesKey("danmaku_enabled")
        val DANMAKU_OPACITY = floatPreferencesKey("danmaku_opacity")
        val DANMAKU_FONT_SIZE = floatPreferencesKey("danmaku_font_size")
        val DANMAKU_DENSITY = floatPreferencesKey("danmaku_density")

        // Auto play last video
        val AUTO_PLAY_LAST_VIDEO = booleanPreferencesKey("auto_play_last_video")
        val LAST_PLAYED_BVID = stringPreferencesKey("last_played_bvid")
        val LAST_PLAYED_TITLE = stringPreferencesKey("last_played_title")
        val LAST_PLAYED_COVER = stringPreferencesKey("last_played_cover")
        val LAST_PLAYED_CID = longPreferencesKey("last_played_cid")
        val LAST_PLAYED_PROGRESS = longPreferencesKey("last_played_progress")
        val LAST_PLAYED_IS_LIVE = booleanPreferencesKey("last_played_is_live")
        val LAST_PLAYED_ROOM_ID = longPreferencesKey("last_played_room_id")

        // Interface settings
        val SPLASH_ANIMATION = booleanPreferencesKey("splash_animation")
        val SHOW_FPS = booleanPreferencesKey("show_fps")
        val THEME_MODE = intPreferencesKey("theme_mode")

        // Storage settings
        val CACHE_SIZE = longPreferencesKey("cache_size")
        val AUTO_CLEAR = booleanPreferencesKey("auto_clear")

        // Plugin settings
        val AD_FILTER_ENABLED = booleanPreferencesKey("ad_filter_enabled")
        val SPONSOR_BLOCK_ENABLED = booleanPreferencesKey("sponsor_block_enabled")
        val DANMAKU_ENHANCE_ENABLED = booleanPreferencesKey("danmaku_enhance_enabled")
    }

    suspend fun init(context: Context? = null) {
        if (!::dataStore.isInitialized && context != null) {
            dataStore = context.settingsDataStore
            sharedPreferences = context.getSharedPreferences("bili_tv_prefs", Context.MODE_PRIVATE)
        }
    }

    // 获取SharedPreferences用于简单设置存储
    val preferences: SharedPreferences?
        get() = sharedPreferences

    private fun ensureInitialized() {
        if (!::dataStore.isInitialized) {
            throw IllegalStateException("SettingsService not initialized. Call init() first.")
        }
    }

    // Playback settings
    var defaultQuality: Int
        get() = runBlocking { dataStore.data.first()[Keys.DEFAULT_QUALITY] ?: 80 } // Default 1080P
        set(value) = runBlocking { dataStore.edit { it[Keys.DEFAULT_QUALITY] = value } }

    var autoPlay: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.AUTO_PLAY] ?: true }
        set(value) = runBlocking { dataStore.edit { it[Keys.AUTO_PLAY] = value } }

    var autoNext: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.AUTO_NEXT] ?: true }
        set(value) = runBlocking { dataStore.edit { it[Keys.AUTO_NEXT] = value } }

    var playbackSpeed: Float
        get() = runBlocking { dataStore.data.first()[Keys.PLAYBACK_SPEED] ?: 1.0f }
        set(value) = runBlocking { dataStore.edit { it[Keys.PLAYBACK_SPEED] = value } }

    var danmakuEnabled: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.DANMAKU_ENABLED] ?: true }
        set(value) = runBlocking { dataStore.edit { it[Keys.DANMAKU_ENABLED] = value } }

    var danmakuOpacity: Float
        get() = runBlocking { dataStore.data.first()[Keys.DANMAKU_OPACITY] ?: 0.7f }
        set(value) = runBlocking { dataStore.edit { it[Keys.DANMAKU_OPACITY] = value } }

    var danmakuFontSize: Float
        get() = runBlocking { dataStore.data.first()[Keys.DANMAKU_FONT_SIZE] ?: 25f }
        set(value) = runBlocking { dataStore.edit { it[Keys.DANMAKU_FONT_SIZE] = value } }

    var danmakuDensity: Float
        get() = runBlocking { dataStore.data.first()[Keys.DANMAKU_DENSITY] ?: 1.0f }
        set(value) = runBlocking { dataStore.edit { it[Keys.DANMAKU_DENSITY] = value } }

    // Interface settings
    var splashAnimationEnabled: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.SPLASH_ANIMATION] ?: true }
        set(value) = runBlocking { dataStore.edit { it[Keys.SPLASH_ANIMATION] = value } }

    var showFps: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.SHOW_FPS] ?: false }
        set(value) = runBlocking { dataStore.edit { it[Keys.SHOW_FPS] = value } }

    var themeMode: Int
        get() = runBlocking { dataStore.data.first()[Keys.THEME_MODE] ?: 0 } // 0 = dark
        set(value) = runBlocking { dataStore.edit { it[Keys.THEME_MODE] = value } }

    // Storage settings
    var cacheSize: Long
        get() = runBlocking { dataStore.data.first()[Keys.CACHE_SIZE] ?: 500L } // MB
        set(value) = runBlocking { dataStore.edit { it[Keys.CACHE_SIZE] = value } }

    var autoClear: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.AUTO_CLEAR] ?: true }
        set(value) = runBlocking { dataStore.edit { it[Keys.AUTO_CLEAR] = value } }

    // Plugin settings
    var adFilterEnabled: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.AD_FILTER_ENABLED] ?: true }
        set(value) = runBlocking { dataStore.edit { it[Keys.AD_FILTER_ENABLED] = value } }

    var sponsorBlockEnabled: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.SPONSOR_BLOCK_ENABLED] ?: true }
        set(value) = runBlocking { dataStore.edit { it[Keys.SPONSOR_BLOCK_ENABLED] = value } }

    var danmakuEnhanceEnabled: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.DANMAKU_ENHANCE_ENABLED] ?: true }
        set(value) = runBlocking { dataStore.edit { it[Keys.DANMAKU_ENHANCE_ENABLED] = value } }

    // Auto play last video settings
    var autoPlayLastVideo: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.AUTO_PLAY_LAST_VIDEO] ?: false }
        set(value) = runBlocking { dataStore.edit { it[Keys.AUTO_PLAY_LAST_VIDEO] = value } }

    var lastPlayedBvid: String
        get() = runBlocking { dataStore.data.first()[Keys.LAST_PLAYED_BVID] ?: "" }
        set(value) = runBlocking { dataStore.edit { it[Keys.LAST_PLAYED_BVID] = value } }

    var lastPlayedTitle: String
        get() = runBlocking { dataStore.data.first()[Keys.LAST_PLAYED_TITLE] ?: "" }
        set(value) = runBlocking { dataStore.edit { it[Keys.LAST_PLAYED_TITLE] = value } }

    var lastPlayedCover: String
        get() = runBlocking { dataStore.data.first()[Keys.LAST_PLAYED_COVER] ?: "" }
        set(value) = runBlocking { dataStore.edit { it[Keys.LAST_PLAYED_COVER] = value } }

    var lastPlayedCid: Long
        get() = runBlocking { dataStore.data.first()[Keys.LAST_PLAYED_CID] ?: 0 }
        set(value) = runBlocking { dataStore.edit { it[Keys.LAST_PLAYED_CID] = value } }

    var lastPlayedProgress: Long
        get() = runBlocking { dataStore.data.first()[Keys.LAST_PLAYED_PROGRESS] ?: 0 }
        set(value) = runBlocking { dataStore.edit { it[Keys.LAST_PLAYED_PROGRESS] = value } }

    var lastPlayedIsLive: Boolean
        get() = runBlocking { dataStore.data.first()[Keys.LAST_PLAYED_IS_LIVE] ?: false }
        set(value) = runBlocking { dataStore.edit { it[Keys.LAST_PLAYED_IS_LIVE] = value } }

    var lastPlayedRoomId: Long
        get() = runBlocking { dataStore.data.first()[Keys.LAST_PLAYED_ROOM_ID] ?: 0 }
        set(value) = runBlocking { dataStore.edit { it[Keys.LAST_PLAYED_ROOM_ID] = value } }

    // Auto play last video methods
    fun saveLastPlayedVideo(
        bvid: String,
        title: String,
        cover: String,
        cid: Long,
        progress: Long,
        isLive: Boolean,
        roomId: Long
    ) {
        runBlocking {
            dataStore.edit {
                it[Keys.LAST_PLAYED_BVID] = bvid
                it[Keys.LAST_PLAYED_TITLE] = title
                it[Keys.LAST_PLAYED_COVER] = cover
                it[Keys.LAST_PLAYED_CID] = cid
                it[Keys.LAST_PLAYED_PROGRESS] = progress
                it[Keys.LAST_PLAYED_IS_LIVE] = isLive
                it[Keys.LAST_PLAYED_ROOM_ID] = roomId
            }
        }
    }

    fun clearLastPlayedVideo() {
        runBlocking {
            dataStore.edit {
                it.remove(Keys.LAST_PLAYED_BVID)
                it.remove(Keys.LAST_PLAYED_TITLE)
                it.remove(Keys.LAST_PLAYED_COVER)
                it.remove(Keys.LAST_PLAYED_CID)
                it.remove(Keys.LAST_PLAYED_PROGRESS)
                it.remove(Keys.LAST_PLAYED_IS_LIVE)
                it.remove(Keys.LAST_PLAYED_ROOM_ID)
            }
        }
    }

    fun hasLastPlayedVideo(): Boolean {
        return runBlocking {
            val data = dataStore.data.first()
            val isLive = data[Keys.LAST_PLAYED_IS_LIVE] ?: false
            if (isLive) {
                data[Keys.LAST_PLAYED_ROOM_ID] ?: 0 > 0
            } else {
                data[Keys.LAST_PLAYED_BVID]?.isNotEmpty() == true
            }
        }
    }

    // Flow getters for reactive updates
    fun getDefaultQualityFlow(): Flow<Int> = dataStore.data.map { it[Keys.DEFAULT_QUALITY] ?: 80 }
    fun getAutoPlayFlow(): Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_PLAY] ?: true }
    fun getDanmakuEnabledFlow(): Flow<Boolean> = dataStore.data.map { it[Keys.DANMAKU_ENABLED] ?: true }
    fun getSplashAnimationFlow(): Flow<Boolean> = dataStore.data.map { it[Keys.SPLASH_ANIMATION] ?: true }
    fun getAutoPlayLastVideoFlow(): Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_PLAY_LAST_VIDEO] ?: false }
}
