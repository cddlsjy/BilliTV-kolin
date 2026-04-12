package com.xycz.bilibili_live.domain.model

/**
 * 弹幕消息类型
 */
sealed class DanmakuMessage {
    /**
     * 普通弹幕
     * @param position 0=顶部 1=滚动 2=底部
     */
    data class ChatMessage(
        val userName: String,
        val message: String,
        val color: Int,
        val position: Int = 1  // 默认滚动弹幕
    ) : DanmakuMessage()

    /**
     * 醒目留言 (Super Chat)
     */
    data class SuperChatMessage(
        val userName: String,
        val message: String,
        val price: Int,
        val face: String,
        val backgroundColor: String
    ) : DanmakuMessage()

    /**
     * 在线人数更新
     */
    data class OnlineMessage(val count: Int) : DanmakuMessage()
}
