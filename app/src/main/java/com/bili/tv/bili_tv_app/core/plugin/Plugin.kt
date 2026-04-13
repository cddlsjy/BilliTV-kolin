package com.bili.tv.bili_tv_app.core.plugin

import com.bili.tv.bili_tv_app.services.api.DanmakuSegment

interface Plugin {
    fun getId(): String
    fun getName(): String
    fun getDescription(): String
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}
