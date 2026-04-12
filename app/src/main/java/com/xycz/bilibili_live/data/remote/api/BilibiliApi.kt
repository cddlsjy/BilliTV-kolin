package com.xycz.bilibili_live.data.remote.api

import com.xycz.bilibili_live.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * B站直播API接口
 */
interface BilibiliApi {

    // 获取分类列表
    @GET("room/v1/Area/getList")
    suspend fun getCategories(
        @Query("need_entrance") needEntrance: Int = 1,
        @Query("parent_id") parentId: Int = 0
    ): Response<CategoryResponse>

    // 获取分类房间列表
    @GET("xlive/web-interface/v1/second/getList")
    suspend fun getCategoryRooms(
        @Query("platform") platform: String = "web",
        @Query("parent_area_id") parentAreaId: String,
        @Query("area_id") areaId: String,
        @Query("page") page: Int,
        @Query("w_webid") wWebid: String,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: Long
    ): Response<RoomListResponse>

    // 获取推荐直播
    @GET("xlive/web-interface/v1/second/getListByArea")
    suspend fun getRecommendRooms(
        @Query("platform") platform: String = "web",
        @Query("sort") sort: String = "online",
        @Query("page_size") pageSize: Int = 30,
        @Query("page") page: Int,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: Long
    ): Response<RoomListResponse>

    // 获取房间信息
    @GET("xlive/web-room/v1/index/getInfoByRoom")
    suspend fun getRoomInfo(
        @Query("room_id") roomId: String,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: Long
    ): Response<RoomInfoResponse>

    // 获取弹幕服务器信息
    @GET("xlive/web-room/v1/index/getDanmuInfo")
    suspend fun getDanmakuInfo(
        @Query("id") roomId: String,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: Long
    ): Response<DanmakuInfoResponse>

    // 获取播放信息
    @GET("xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun getRoomPlayInfo(
        @Query("room_id") roomId: String,
        @Query("protocol") protocol: String = "0,1",
        @Query("format") format: String = "0,1,2",
        @Query("codec") codec: String = "0,1",
        @Query("platform") platform: String = "web",
        @Query("qn") qn: Int? = null
    ): Response<PlayInfoResponse>

    // 发送弹幕
    @FormUrlEncoded
    @POST("msg/send")
    suspend fun sendDanmaku(
        @Field("bvid") bvid: String = "",
        @Field("msg") msg: String,
        @Field("roomid") roomId: String,
        @Field("rnd") rnd: String,
        @Field("csrf") csrf: String,
        @Field("csrf_token") csrfToken: String
    ): Response<SendDanmakuResponse>

    // 搜索直播间
    @GET("x/web-interface/search/type")
    suspend fun searchRooms(
        @Query("search_type") searchType: String = "live",
        @Query("keyword") keyword: String,
        @Query("page") page: Int
    ): Response<SearchResponse>

    // 搜索主播
    @GET("x/web-interface/search/type")
    suspend fun searchAnchors(
        @Query("search_type") searchType: String = "live_user",
        @Query("keyword") keyword: String,
        @Query("page") page: Int
    ): Response<SearchAnchorResponse>

    // 获取SC消息
    @GET("av/v1/SuperChat/getMessageList")
    suspend fun getSuperChatMessage(
        @Query("room_id") roomId: String
    ): Response<SuperChatResponse>

    // 获取二维码登录信息
    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQRCode(): Response<QRCodeResponse>

    // 轮询扫码状态
    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQRStatus(
        @Query("qrcode_key") qrcodeKey: String
    ): Response<QRStatusResponse>

    // 获取用户信息
    @GET("x/space/myinfo")
    suspend fun getUserInfo(): Response<UserInfoResponse>

    // 获取 WBI 签名密钥
    @GET("x/web-interface/nav")
    suspend fun getWbiKeys(): Response<WbiKeysResponse>

    // 获取 buvid
    @GET("x/frontend/finger/spi")
    suspend fun getBuvid(): Response<BuvidResponse>

    // ==================== 点播API接口 ====================

    // 获取视频详情（含分P列表）
    @GET("x/web-interface/view")
    suspend fun getVideoDetail(
        @Query("bvid") bvid: String
    ): Response<VideoDetailResponse>

    // 获取视频播放地址
    @GET("x/player/playurl")
    suspend fun getVideoPlayUrl(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int? = null
    ): Response<VideoPlayUrlResponse>

    // 获取推荐视频列表
    @GET("x/web-interface/related")
    suspend fun getRecommendVideos(
        @Query("bvid") bvid: String
    ): Response<RecommendVideosResponse>
}
