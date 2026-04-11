package com.bili.tv.bili_tv_app.services.api

import android.util.Log
import com.bili.tv.bili_tv_app.models.*
import com.bili.tv.bili_tv_app.services.AuthService
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class BilibiliApiService {

    private val client: OkHttpClient
    private val gson = Gson()
    private val baseUrl = "https://api.bilibili.com"

    companion object {
        private const val APP_KEY = "4409e2ce8ffd12b8"
        private const val APP_SECRET = "59b43e04c6964cf34319092"

        @Volatile
        private var instance: BilibiliApiService? = null

        fun getInstance(): BilibiliApiService {
            return instance ?: synchronized(this) {
                instance ?: BilibiliApiService().also { instance = it }
            }
        }
    }

    init {
        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Get recommended videos for homepage
    suspend fun getRecommendVideos(idx: Int = 0): List<Video> {
        val url = "$baseUrl/x/web-interface/popular?pn=1&ps=20&idx=$idx"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://www.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            val result = gson.fromJson(body, RecommendResponse::class.java)
            result.data?.list ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Get video info by bvid
    suspend fun getVideoInfo(bvid: String): VideoDetail? {
        val url = "$baseUrl/x/web-interface/view?bvid=$bvid"
        Log.d("BilibiliApiService", "获取视频信息URL: $url")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://www.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            Log.d("BilibiliApiService", "API响应状态码: ${response.code}")
            
            val body = response.body?.string()
            if (body == null) {
                Log.d("BilibiliApiService", "API响应为空")
                return null
            }
            
            Log.d("BilibiliApiService", "API响应: $body")
            
            val result = gson.fromJson(body, VideoDetailResponse::class.java)
            Log.d("BilibiliApiService", "解析结果: code=${result.code}, message=${result.message}")
            
            result.data
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("BilibiliApiService", "获取视频信息异常: ${e.message}")
            null
        }
    }

    // Get play URL
    suspend fun getPlayUrl(aid: Long, cid: Long, quality: Int = 80): String? {
        val timestamp = System.currentTimeMillis() / 1000
        val random = Random.nextInt(10000, 99999)
        
        val params = mapOf(
            "avid" to aid.toString(),
            "cid" to cid.toString(),
            "qn" to quality.toString(),
            "fnval" to "4048", // DASH format
            "fourk" to "1",
            "appkey" to APP_KEY,
            "ts" to timestamp.toString(),
            "random" to random.toString()
        )
        val signedParams = signParams(params)
        val url = "$baseUrl/x/player/playurl?$signedParams"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .addHeader("Referer", "https://www.bilibili.com")
            .addHeader("Origin", "https://www.bilibili.com")
            .addHeader("Cookie", "buvid3=BFE8F29D-9B1B-4B0A-9B0A-9B0A9B0A9B0Ainfoc; CURRENT_FNVAL=80; b_nut=1700000000")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .addHeader("Connection", "keep-alive")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            Log.d("BilibiliApiService", "API响应: $body")
            
            // Check if body is null
            if (body == null) {
                Log.d("BilibiliApiService", "API响应为空")
                return null
            }
            
            // Parse JSON response
            val json = JSONObject(body)
            val code = json.getInt("code")
            val message = json.getString("message")
            Log.d("BilibiliApiService", "API响应: code=$code, message=$message")
            
            if (code == 0) {
                val data = json.getJSONObject("data")
                
                // Try to get video URL from dash -> video -> baseUrl
                if (data.has("dash")) {
                    val dash = data.getJSONObject("dash")
                    if (dash.has("video")) {
                        val videoArray = dash.getJSONArray("video")
                        if (videoArray.length() > 0) {
                            val firstVideo = videoArray.getJSONObject(0)
                            val firstVideoUrl = if (firstVideo.has("baseUrl")) {
                                firstVideo.getString("baseUrl")
                            } else if (firstVideo.has("base_url")) {
                                firstVideo.getString("base_url")
                            } else {
                                null
                            }
                            if (firstVideoUrl != null) {
                                Log.d("BilibiliApiService", "获取视频URL成功: $firstVideoUrl")
                                return firstVideoUrl
                            }
                        }
                    }
                }
                
                // Try to get video URL from durl
                if (data.has("durl")) {
                    val durlArray = data.getJSONArray("durl")
                    if (durlArray.length() > 0) {
                        val firstDurl = durlArray.getJSONObject(0)
                        if (firstDurl.has("url")) {
                            val firstVideoUrl = firstDurl.getString("url")
                            Log.d("BilibiliApiService", "获取视频URL成功: $firstVideoUrl")
                            return firstVideoUrl
                        }
                    }
                }
                
                // Try to get video URL from other sources
                if (data.has("video")) {
                    val videoArray = data.getJSONArray("video")
                    if (videoArray.length() > 0) {
                        val firstVideo = videoArray.getJSONObject(0)
                        val videoUrl = if (firstVideo.has("baseUrl")) {
                            firstVideo.getString("baseUrl")
                        } else if (firstVideo.has("base_url")) {
                            firstVideo.getString("base_url")
                        } else if (firstVideo.has("url")) {
                            firstVideo.getString("url")
                        } else {
                            null
                        }
                        if (videoUrl != null) {
                            Log.d("BilibiliApiService", "从video字段获取视频URL成功: $videoUrl")
                            return videoUrl
                        }
                    }
                }
            }
            
            Log.d("BilibiliApiService", "获取视频URL失败")
            null
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("BilibiliApiService", "获取视频URL异常: ${e.message}")
            null
        }
    }

    // Get danmaku (bullet comments)
    suspend fun getDanmaku(cid: Long): List<DanmakuSegment>? {
        val url = "$baseUrl/x/v1/subtitle/list?cid=$cid"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://www.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (body == null) {
                Log.d("BilibiliApiService", "获取字幕列表响应为空")
                return null
            }
            
            val result = gson.fromJson(body, DanmakuListResponse::class.java)
            result.data?.subtitles?.firstOrNull()?.let { subtitle ->
                Log.d("BilibiliApiService", "获取字幕URL: ${subtitle.url}")
                
                // Download subtitle URL
                val subtitleRequest = Request.Builder()
                    .url(subtitle.url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", "https://www.bilibili.com")
                    .build()
                
                val subtitleResponse = client.newCall(subtitleRequest).execute()
                val subtitleBody = subtitleResponse.body?.string()
                
                if (subtitleBody == null) {
                    Log.d("BilibiliApiService", "获取字幕内容响应为空")
                    return null
                }
                
                Log.d("BilibiliApiService", "字幕内容长度: ${subtitleBody.length}")
                
                try {
                    val danmakuResponse = gson.fromJson(subtitleBody, DanmakuResponse::class.java)
                    val comments = danmakuResponse?.body?.comments
                    Log.d("BilibiliApiService", "获取弹幕数量: ${comments?.size ?: 0}")
                    comments
                } catch (e: Exception) {
                    Log.d("BilibiliApiService", "解析字幕内容失败: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.d("BilibiliApiService", "获取弹幕异常: ${e.message}")
            null
        }
    }

    // Get live stream URL
    suspend fun getLiveUrl(roomId: Long): LiveUrl? {
        val url = "$baseUrl/xlive/web-room/v1/index/getRoomPlayInfo?room_id=$roomId&quality=4&pn=1&ps=1000"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://live.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            val result = gson.fromJson(body, LiveUrlResponse::class.java)
            result.data
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Get follow live rooms
    suspend fun getFollowLiveRooms(page: Int): List<LiveRoom> {
        val url = "$baseUrl/xlive/web-interface/v1/relation/live?page=$page&page_size=20"
        val cookies = AuthService.cookies
        
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://live.bilibili.com")
            .addHeader("Cookie", cookies)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            val result = gson.fromJson(body, LiveRoomListResponse::class.java)
            result.data?.list ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Get recommend live rooms
    suspend fun getRecommendLiveRooms(page: Int): List<LiveRoom> {
        val url = "$baseUrl/xlive/web-interface/v1/room/sync?page=$page&page_size=20"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://live.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            val result = gson.fromJson(body, LiveRoomListResponse::class.java)
            result.data?.list ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Get live stream URL
    suspend fun getLiveStreamUrl(roomId: Long): String? {
        val url = "$baseUrl/xlive/web-room/v1/index/getRoomPlayInfo?room_id=$roomId&quality=4&pn=1&ps=1000"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://live.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            val result = gson.fromJson(body, LiveUrlResponse::class.java)
            result.data?.durl?.firstOrNull()?.url
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Get user info
    suspend fun getUserInfo(mid: Long): User? {
        val url = "$baseUrl/x/web-interface/card?mid=$mid"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://space.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            val result = gson.fromJson(body, UserInfoResponse::class.java)
            result.data?.card?.let { card ->
                User(
                    mid = card.mid,
                    uname = card.name,
                    face = card.face,
                    sign = card.sign,
                    level = card.level,
                    vipType = card.vipType,
                    vipStatus = card.vipStatus
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Search videos
    suspend fun searchVideos(keyword: String, page: Int = 1): List<Video> {
        val url = "$baseUrl/x/web-interface/search/type?search_type=video&keyword=$keyword&page=$page"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://search.bilibili.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            val result = gson.fromJson(body, SearchResponse::class.java)
            result.data?.result ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Get user history
    suspend fun getHistory(): List<Video> {
        val url = "$baseUrl/x/web-interface/history/cursor?pn=1&ps=20"
        val cookies = AuthService.getCookies()

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", "https://www.bilibili.com")
            .addHeader("Cookie", cookies)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            val result = gson.fromJson(body, HistoryResponse::class.java)
            result.data?.list?.mapNotNull { it.businessData?.video } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Helper functions
    private fun signParams(params: Map<String, String>): String {
        val sortedParams = params.entries.sortedBy { it.key }
        val paramString = sortedParams.joinToString("&") { "${it.key}=${it.value}" }
        val signString = "$paramString$APP_SECRET"

        val hmacSha256 = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(APP_SECRET.toByteArray(), "HmacSHA256")
        hmacSha256.init(secretKey)
        val signature = hmacSha256.doFinal(signString.toByteArray())
            .joinToString("") { "%02x".format(it) }

        return "$paramString&sign=$signature"
    }

    // Response classes
    private data class RecommendResponse(
        val code: Int = 0,
        val message: String = "",
        val data: RecommendData? = null
    )

    private data class RecommendData(
        val list: List<Video>? = null
    )

    private data class VideoInfoResponse(
        val code: Int = 0,
        val message: String = "",
        val data: Video? = null
    )

    private data class VideoDetailResponse(
        val code: Int = 0,
        val message: String = "",
        val data: VideoDetail? = null
    )

    private data class LiveRoomListResponse(
        val code: Int = 0,
        val message: String = "",
        val data: LiveRoomListData? = null
    )

    private data class LiveRoomListData(
        val list: List<LiveRoom>? = null
    )

    private data class DanmakuListResponse(
        val code: Int = 0,
        val data: DanmakuListData? = null
    )

    private data class DanmakuListData(
        val subtitles: List<Subtitle>? = null
    )

    private data class Subtitle(
        val id: Long = 0,
        val url: String = "",
        val lang: String = "zh-CN"
    )

    private data class DanmakuResponse(
        val code: Int = 0,
        val message: String = "",
        val body: DanmakuBody? = null
    )

    private data class DanmakuBody(
        val comments: List<DanmakuSegment>? = null
    )

    data class LiveUrlResponse(
        val code: Int = 0,
        val message: String = "",
        val data: LiveUrl? = null
    )

    data class LiveUrl(
        val quality: Int = 0,
        val format: String = "",
        val durl: List<LiveDUrl>? = null
    )

    data class LiveDUrl(
        val url: String = "",
        val length: Int = 0,
        val order: Int = 0
    )

    data class PlayUrl(
        val code: Int = 0,
        val message: String = "",
        val data: PlayUrlResult? = null
    )

    data class PlayUrlResult(
        val dash: Dash? = null
    )

    data class Dash(
        val video: List<PlayVideo>? = null
    )

    data class PlayVideo(
        val baseUrl: String = "",
        val base_url: String = ""
    )

    private data class UserInfoResponse(
        val code: Int = 0,
        val message: String = "",
        val data: UserInfoData? = null
    )

    private data class UserInfoData(
        val card: UserCard? = null
    )

    private data class UserCard(
        val mid: Long = 0,
        val name: String = "",
        val face: String = "",
        val sign: String = "",
        val level: Int = 0,
        val vipType: Int = 0,
        val vipStatus: Int = 0
    )

    private data class SearchResponse(
        val code: Int = 0,
        val message: String = "",
        val data: SearchData? = null
    )

    private data class SearchData(
        val result: List<Video>? = null
    )

    private data class HistoryResponse(
        val code: Int = 0,
        val message: String = "",
        val data: HistoryData? = null
    )

    private data class HistoryData(
        val list: List<HistoryItem>? = null
    )

    private data class HistoryItem(
        val k: String = "",
        val businessData: BusinessData? = null
    )

    private data class BusinessData(
        val video: Video? = null
    )
}

// Danmaku segment model
data class DanmakuSegment(
    val id: Long = 0,
    val p: String = "", // Position, mode, color, etc.
    val m: String = ""  // Message text
)
