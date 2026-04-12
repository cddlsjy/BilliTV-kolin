package com.xycz.bilibili_live.service.danmaku

import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import com.xycz.bilibili_live.domain.model.DanmakuMessage
import com.xycz.bilibili_live.util.NetworkModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 弹幕服务
 * 使用OkHttp WebSocket连接B站弹幕服务器
 */
class DanmakuService(
    private val roomId: Int,
    private val token: String,
    private val serverHost: String,
    private val buvid: String,
    private val uid: Int,
    private val cookie: String
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _messages = MutableSharedFlow<DanmakuMessage>(replay = 0, extraBufferCapacity = 100)
    val messages: SharedFlow<DanmakuMessage> = _messages

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _onlineCount = MutableStateFlow(0)
    val onlineCount: StateFlow<Int> = _onlineCount

    companion object {
        // WebSocket协议操作类型
        private const val OP_HEARTBEAT = 2        // 心跳
        private const val OP_HEARTBEAT_REPLY = 3  // 心跳回复
        private const val OP_CONNECT = 7          // 连接
        private const val OP_CONNECT_REPLY = 8    // 连接回复
        private const val OP_DANMAKU = 5          // 弹幕消息
    }

    /**
     * 连接弹幕服务器
     */
    fun connect() {
        scope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING

                val request = Request.Builder()
                    .url("wss://$serverHost/sub")
                    .apply {
                        if (cookie.isNotEmpty()) {
                            addHeader("cookie", cookie)
                        }
                    }
                    .build()

                webSocket = NetworkModule.okHttpClient.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            _connectionState.value = ConnectionState.CONNECTED
                            joinRoom()
                            startHeartbeat()
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            parseMessage(text.toByteArray())
                        }

                        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                            parseMessage(bytes.toByteArray())
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            _connectionState.value = ConnectionState.ERROR
                            t.printStackTrace()
                            // 尝试重连
                            reconnect()
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            _connectionState.value = ConnectionState.DISCONNECTED
                            stopHeartbeat()
                        }
                    }
                )

            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                e.printStackTrace()
            }
        }
    }

    /**
     * 加入房间
     */
    private fun joinRoom() {
        val data = JSONObject().apply {
            put("uid", uid)
            put("roomid", roomId)
            put("protover", 3)
            put("buvid", buvid)
            put("platform", "web")
            put("type", 2)
            put("key", token)
        }
        sendPacket(data.toString(), OP_CONNECT)
    }

    /**
     * 开始心跳
     */
    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30_000) // 30秒心跳一次
                sendPacket("", OP_HEARTBEAT)
            }
        }
    }

    /**
     * 停止心跳
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * 发送数据包
     */
    private fun sendPacket(data: String, action: Int) {
        val bytes = encodeData(data, action)
        webSocket?.send(ByteString.of(*bytes))
    }

    /**
     * 编码数据包
     * 弹幕协议格式：
     * [4字节: 数据包长度][2字节: 头部长度][2字节: 协议版本][4字节: 操作类型][4字节: 固定1][数据]
     */
    private fun encodeData(msg: String, action: Int): ByteArray {
        val data = msg.toByteArray()
        val length = data.size + 16
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(length)     // 数据包长度
        buffer.putShort(16)      // 头部长度
        buffer.putShort(0)        // 协议版本 (0=JSON)
        buffer.putInt(action)     // 操作类型
        buffer.putInt(1)          // 固定1
        buffer.put(data)

        return buffer.array()
    }

    /**
     * 解析消息
     */
    private fun parseMessage(data: ByteArray) {
        try {
            if (data.size < 16) return

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            // 跳过前4字节(长度)
            buffer.int
            val headerLen = buffer.short.toInt()
            val protocolVersion = buffer.short.toInt()
            val operation = buffer.int

            val body = data.copyOfRange(headerLen, data.size)

            when (operation) {
                OP_HEARTBEAT_REPLY -> {
                    // 心跳回复（在线人数）
                    if (body.size >= 4) {
                        val online = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN).int
                        _onlineCount.value = online
                        scope.launch {
                            _messages.emit(DanmakuMessage.OnlineMessage(online))
                        }
                    }
                }
                OP_DANMAKU -> {
                    // 弹幕消息
                    val decompressedBody = when (protocolVersion) {
                        2 -> BrotliDecoder.decompress(body)
                        else -> body
                    }

                    val text = String(decompressedBody, Charsets.UTF_8)
                    val messages = text.split(Regex("[\n]"))

                    messages.filter { it.length > 2 && it.startsWith("{") }
                        .forEach { parseDanmakuJson(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 解析弹幕JSON
     */
    private fun parseDanmakuJson(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val cmd = json.optString("cmd", "")

            when {
                cmd.contains("DANMU_MSG") -> {
                    val info = json.optJSONArray("info") ?: return
                    if (info.length() < 2) return

                    val message = info.getString(1)
                    val color = info.optJSONArray(0)?.optInt(3) ?: 16777215 // 默认白色
                    val userInfo = info.optJSONArray(2)
                    val userName = userInfo?.optJSONObject(1)?.optString("uname") ?: ""

                    scope.launch {
                        _messages.emit(DanmakuMessage.ChatMessage(userName, message, color))
                    }
                }
                cmd == "SUPER_CHAT_MESSAGE" -> {
                    val data = json.optJSONObject("data") ?: return
                    val userInfo = data.optJSONObject("user_info") ?: return

                    scope.launch {
                        _messages.emit(
                            DanmakuMessage.SuperChatMessage(
                                userName = userInfo.optString("uname"),
                                message = data.optString("message"),
                                price = data.optInt("price"),
                                face = userInfo.optString("face"),
                                backgroundColor = data.optString("background_color")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 重新连接
     */
    private fun reconnect() {
        scope.launch {
            delay(3000) // 3秒后重连
            connect()
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "User disconnected")
        scope.cancel()
    }
}

/**
 * 连接状态
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
