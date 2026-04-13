package com.bili.tv.bili_tv_app.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import com.bili.tv.bili_tv_app.services.api.DanmakuSegment
import kotlinx.coroutines.*
import kotlin.random.Random

class DanmakuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val danmakuList = mutableListOf<DanmakuItem>()
    private val paint = Paint().apply {
        isAntiAlias = true
        textSize = 50f
    }

    private var textSize = 50f
    private var alpha = 0.7f
    private var speed = 1.0f

    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val visibleDanmaku = mutableListOf<DanmakuItem>()

    data class DanmakuItem(
        val text: String,
        val color: Int,
        var x: Float,
        val y: Float,
        var alpha: Float = 1f
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (danmaku in visibleDanmaku) {
            paint.alpha = (danmaku.alpha * 255).toInt()
            paint.color = danmaku.color
            canvas.drawText(danmaku.text, danmaku.x, danmaku.y, paint)
        }
    }

    fun setDanmakuList(list: List<DanmakuSegment>) {
        danmakuList.clear()

        list.forEach { segment ->
            val color = parseColor(segment.p)
            val position = parsePosition(segment.p)
            val text = segment.m

            danmakuList.add(
                DanmakuItem(
                    text = text,
                    color = color,
                    x = width.toFloat(),
                    y = position,
                    alpha = this.alpha
                )
            )
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            while (isRunning) {
                updateDanmaku()
                invalidate()
                delay(16) // ~60fps
            }
        }
    }

    fun stop() {
        isRunning = false
    }

    private fun updateDanmaku() {
        val moveSpeed = 5f * speed

        // Move existing danmaku
        val iterator = visibleDanmaku.iterator()
        while (iterator.hasNext()) {
            val danmaku = iterator.next()
            danmaku.x -= moveSpeed

            // Remove danmaku that has gone off screen
            if (danmaku.x < -paint.measureText(danmaku.text)) {
                iterator.remove()
            }
        }

        // Add new danmaku
        if (danmakuList.isNotEmpty() && Random.nextFloat() < 0.1f) {
            val index = Random.nextInt(danmakuList.size)
            val danmaku = danmakuList[index].copy(
                x = width.toFloat(),
                alpha = alpha
            )
            visibleDanmaku.add(danmaku)
        }
    }

    private fun parseColor(p: String): Int {
        return try {
            val parts = p.split(",")
            if (parts.size >= 3) {
                Color.rgb(parts[2].toInt(), parts[3].toInt(), parts[4].toInt())
            } else {
                Color.WHITE
            }
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    private fun parsePosition(p: String): Float {
        return try {
            val parts = p.split(",")
            if (parts.size >= 1) {
                parts[1].toFloat() * height / 10
            } else {
                height / 2f
            }
        } catch (e: Exception) {
            height / 2f
        }
    }

    fun setTextSize(size: Float) {
        textSize = size
        paint.textSize = size
    }

    override fun setAlpha(alpha: Float) {
        this.alpha = alpha
    }

    fun setSpeed(speed: Float) {
        this.speed = speed
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
        scope.cancel()
    }
}
