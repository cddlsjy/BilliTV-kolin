package com.xycz.bilibili_live.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 分类列表响应
 */
data class CategoryResponse(
    val code: Int,
    val message: String,
    val data: List<CategoryDto>?,
    @SerializedName("refresh_row") val refreshRow: Int?
)

data class CategoryDto(
    val id: Int,
    val name: String,
    val pic: String?,
    val type: Int?,
    val list: List<SubCategoryDto>?
)

data class SubCategoryDto(
    val id: Int,
    val name: String,
    val pic: String?,
    @SerializedName("parent_id") val parentId: Int?,
    @SerializedName("area_type") val areaType: Int?
)

/**
 * 房间列表响应
 */
data class RoomListResponse(
    val code: Int,
    val message: String,
    val data: RoomListData?
)

data class RoomListData(
    val list: List<RoomDto>?,
    val page: Int?,
    @SerializedName("has_more") val hasMore: Int?
)

data class RoomDto(
    val roomid: Int,
    val uid: Int,
    val uname: String?,
    val title: String?,
    val cover: String?,
    val user_cover: String?,
    val online: Int?,
    @SerializedName("roomid_str") val roomidStr: String?
)

/**
 * 房间信息响应
 */
data class RoomInfoResponse(
    val code: Int,
    val message: String,
    val data: RoomInfoData?
)

data class RoomInfoData(
    @SerializedName("room_id") val roomId: Int,
    val uid: Int,
    @SerializedName("live_status") val liveStatus: Int,
    val uname: String?,
    val title: String?,
    val cover: String?,
    @SerializedName("user_avatar") val userAvatar: String?,
    val online: Int?,
    val attention: Int?,
    @SerializedName("room_layout") val roomLayout: String?,
    @SerializedName("area_name") val areaName: String?
)

/**
 * 弹幕服务器信息响应
 */
data class DanmakuInfoResponse(
    val code: Int,
    val message: String,
    val data: DanmakuInfoData?
)

data class DanmakuInfoData(
    val host: String?,
    val port: Int?,
    val token: String?,
    @SerializedName("host_list") val hostList: List<DanmakuHostDto>?
)

data class DanmakuHostDto(
    val host: String?,
    val port: Int?,
    val wss_port: Int?,
    @SerializedName("ws_port") val wsPort: Int?
)

/**
 * 播放信息响应
 */
data class PlayInfoResponse(
    val code: Int,
    val message: String,
    val data: PlayInfoData?
)

data class PlayInfoData(
    @SerializedName("room_id") val roomId: Int,
    @SerializedName("live_status") val liveStatus: Int,
    val uid: Int?,
    @SerializedName("playurl_info") val playUrlInfo: PlayUrlInfo?
)

data class PlayUrlInfo(
    @SerializedName("playurl") val playUrl: PlayUrl?
)

data class PlayUrl(
    val stream: List<StreamDto>?
)

data class StreamDto(
    @SerializedName("protocol_name") val protocolName: String?,
    @SerializedName("format") val format: List<FormatDto>?
)

data class FormatDto(
    @SerializedName("format_name") val formatName: String?,
    @SerializedName("codec") val codec: List<CodecDto>?
)

data class CodecDto(
    @SerializedName("codec_name") val codecName: String?,
    @SerializedName("url_info") val urlInfo: List<UrlInfoDto>?
)

data class UrlInfoDto(
    val host: String?,
    @SerializedName("extra") val extra: ExtraDto?
)

data class ExtraDto(
    @SerializedName("duration") val duration: Long?,
    @SerializedName("m3u8_edr") val m3u8Edr: String?,
    @SerializedName("send_cover") val sendCover: String?,
    @SerializedName("stream_weight") val streamWeight: Int?,
    @SerializedName("ga4c_id") val ga4cId: String?,
    val qn: Int?,
    @SerializedName("hdr_type") val hdrType: Int?
)

/**
 * 二维码登录响应
 */
data class QRCodeResponse(
    val code: Int,
    val message: String,
    val data: QRCodeData?
)

data class QRCodeData(
    @SerializedName("qrcode_key") val qrcodeKey: String?,
    val url: String?
)

/**
 * 二维码状态响应
 */
data class QRStatusResponse(
    val code: Int,
    val message: String,
    val data: QRStatusData?
)

data class QRStatusData(
    @SerializedName("url") val url: String?,
    @SerializedName("oauth2_url") val oauth2Url: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    val token: String?,
    @SerializedName("cookie_info") val cookieInfo: CookieInfo?
)

data class CookieInfo(
    val cookies: List<CookieDto>?,
    val domain: String?
)

data class CookieDto(
    val name: String?,
    val value: String?,
    val expires: Long?,
    @SerializedName("http_only") val httpOnly: Int?
)

/**
 * 用户信息响应
 */
data class UserInfoResponse(
    val code: Int,
    val message: String,
    val data: UserInfoData?
)

data class UserInfoData(
    val mid: Long?,
    val uname: String?,
    val face: String?,
    val level: Int?,
    @SerializedName("is_login") val isLogin: Boolean?
)

/**
 * WBI密钥响应
 */
data class WbiKeysResponse(
    val code: Int,
    val message: String,
    val data: WbiKeysData?
)

data class WbiKeysData(
    val url: String?,
    @SerializedName("img_url") val imgUrl: String?,
    @SerializedName("sub_url") val subUrl: String?,
    val wbiImg: WbiImgData?
)

data class WbiImgData(
    @SerializedName("img_url") val imgUrl: String?,
    @SerializedName("sub_url") val subUrl: String?
)

/**
 * buvid响应
 */
data class BuvidResponse(
    val code: Int,
    val message: String,
    val data: BuvidData?
)

data class BuvidData(
    @SerializedName("b_3") val buvid3: String?,
    @SerializedName("b_4") val buvid4: String?,
    val buvid: String?,
    val fp: String?
)

/**
 * 搜索响应
 */
data class SearchResponse(
    val code: Int,
    val message: String,
    val data: SearchData?
)

data class SearchData(
    val items: List<SearchItemDto>?,
    @SerializedName("numPages") val numPages: Int?,
    @SerializedName("total") val total: Int?
)

data class SearchItemDto(
    @SerializedName("roomid") val roomid: Int?,
    val title: String?,
    val cover: String?,
    @SerializedName("owner") val owner: OwnerDto?,
    val online: Int?
)

data class OwnerDto(
    val name: String?,
    val face: String?
)

/**
 * 主播搜索响应
 */
data class SearchAnchorResponse(
    val code: Int,
    val message: String,
    val data: AnchorSearchData?
)

data class AnchorSearchData(
    val items: List<AnchorItemDto>?,
    @SerializedName("numPages") val numPages: Int?,
    @SerializedName("total") val total: Int?
)

data class AnchorItemDto(
    val uid: Int?,
    val uname: String?,
    val face: String?,
    @SerializedName("roomid") val roomid: Int?,
    val title: String?,
    val cover: String?,
    val online: Int?
)

/**
 * SC消息响应
 */
data class SuperChatResponse(
    val code: Int,
    val message: String,
    val data: SuperChatData?
)

data class SuperChatData(
    val list: List<SuperChatItemDto>?
)

data class SuperChatItemDto(
    val id: Int?,
    @SerializedName("uid") val uid: Int?,
    @SerializedName("roomid") val roomid: Int?,
    @SerializedName("user_info") val userInfo: ScUserInfo?,
    val message: String?,
    val price: Int?,
    @SerializedName("background_color") val backgroundColor: String?,
    val time: Int?
)

data class ScUserInfo(
    @SerializedName("uname") val uname: String?,
    val face: String?
)

/**
 * 发送弹幕响应
 */
data class SendDanmakuResponse(
    val code: Int,
    val message: String,
    val data: Any?
)

// ==================== 点播DTO ====================

/**
 * 视频详情响应
 */
data class VideoDetailResponse(
    val code: Int,
    val message: String,
    val data: VideoDetailData?
)

data class VideoDetailData(
    val bvid: String?,
    val title: String?,
    val cover: String?,
    val desc: String?,
    val duration: Long?,
    val owner: VideoOwner?,
    val pages: List<VideoPageDto>?
)

data class VideoOwner(
    val mid: Long?,
    val name: String?,
    val face: String?
)

data class VideoPageDto(
    val cid: Long?,
    val page: Int?,
    val part: String?,
    val duration: Long?,
    val weblink: String?,
    val desc: String?
)

/**
 * 视频播放地址响应
 */
data class VideoPlayUrlResponse(
    val code: Int,
    val message: String,
    val data: VideoPlayUrlData?
)

data class VideoPlayUrlData(
    val durl: List<VideoDurlDto>?
)

data class VideoDurlDto(
    val order: Int?,
    val length: Long?,
    val size: Long?,
    val url: String?,
    val md5: String?
)

/**
 * 推荐视频列表响应
 */
data class RecommendVideosResponse(
    val code: Int,
    val message: String,
    val data: List<RecommendVideoDto>?
)

data class RecommendVideoDto(
    val bvid: String?,
    val title: String?,
    val pic: String?,
    val owner: VideoOwner?,
    val short_link: String?,
    val duration: Int?,
    val aid: Long?
)
