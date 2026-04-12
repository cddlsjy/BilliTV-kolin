package com.xycz.bilibili_live.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.xycz.bilibili_live.domain.model.DanmakuMessage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Random

/**
 * 高性能自定义弹幕View
 * 使用SurfaceView进行独立线程渲染
 */
class DanmakuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private val displayMetrics = context.resources.displayMetrics
    private val density = displayMetrics.density
    private val screenWidth = displayMetrics.widthPixels
    private val screenHeight = displayMetrics.heightPixels

    // 弹幕配置
    var danmakuSize: Float = 16f * density
        set(value) {
            field = value
            textPaint.textSize = value
        }

    var danmakuAlpha: Float = 1f
    var showDanmaku: Boolean = true

    // 弹幕数据
    private val danmakuQueue = ConcurrentLinkedQueue<DanmakuMessage.ChatMessage>()
    private val danmakuList = mutableListOf<DanmakuItem>()

    // 轨道管理
    private val tracks = mutableListOf<DanmakuTrack>()
    private val maxTracks = 15
    private val trackHeight = (screenHeight / (maxTracks + 2)).toInt()

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = danmakuSize
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        textSize = danmakuSize
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.BLACK
    }

    private var drawThread: DrawThread? = null
    private var isRunning = false

    private val random = Random()

    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)

        // 初始化轨道
        for (i in 0 until maxTracks) {
            val y = trackHeight * (i + 1)
            tracks.add(DanmakuTrack(y))
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        drawThread = DrawThread().also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        drawThread?.join(500)
        drawThread = null
    }

    /**
     * 添加弹幕
     */
    fun addDanmaku(message: DanmakuMessage.ChatMessage) {
        if (!showDanmaku) return
        danmakuQueue.offer(message)
    }

    /**
     * 添加SuperChat
     */
    fun addSuperChat(message: DanmakuMessage.SuperChatMessage) {
        // SuperChat可以单独处理，这里简化处理
    }

    /**
     * 清空弹幕
     */
    fun clear() {
        synchronized(danmakuList) {
            danmakuList.clear()
        }
        danmakuQueue.clear()
        tracks.forEach {
            it.lastEndTime = 0
            it.lastEndX = 0f
        }
    }

    /**
     * 处理待添加的弹幕
     */
    private fun processQueue() {
        while (true) {
            val message = danmakuQueue.poll() ?: break
            addDanmakuInternal(message)
        }
    }

    /**
     * 内部添加弹幕
     */
    private fun addDanmakuInternal(message: DanmakuMessage.ChatMessage) {
        val textWidth = textPaint.measureText(message.message)
        val track = findAvailableTrack(textWidth)

        if (track != null) {
            val item = DanmakuItem(
                text = message.message,
                color = message.color,
                track = track,
                x = screenWidth.toFloat(),
                trackY = track.y,
                speed = (180 + random.nextInt(80)) * density, // 像素/秒
                width = textWidth
            )
            synchronized(danmakuList) {
                danmakuList.add(item)
            }
        }
    }

    /**
     * 查找可用轨道
     */
    private fun findAvailableTrack(width: Float): DanmakuTrack? {
        val currentTime = System.currentTimeMillis()
        return tracks.find { track ->
            track.lastEndTime + 500 < currentTime ||
                    track.lastEndX + width < screenWidth
        }
    }

    /**
     * 绘制线程
     */
    private inner class DrawThread : Thread("DanmakuDrawThread") {
        private var lastFrameTime = System.currentTimeMillis()

        override fun run() {
            while (isRunning && !isInterrupted) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastFrameTime) / 1000f
                lastFrameTime = currentTime

                // 处理待添加的弹幕
                processQueue()

                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    canvas?.let { drawFrame(it, deltaTime) }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    canvas?.let {
                        try {
                            holder.unlockCanvasAndPost(it)
                        } catch (e: Exception) {
                            // Surface可能被销毁
                        }
                    }
                }

                // 控制帧率 ~60fps
                try {
                    sleep(16)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    /**
     * 绘制单帧
     */
    private fun drawFrame(canvas: Canvas, deltaTime: Float) {
        // 清屏
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val currentTime = System.currentTimeMillis()

        synchronized(danmakuList) {
            val iterator = danmakuList.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()

                // 更新位置
                item.x -= item.speed * deltaTime

                // 移除离开屏幕的弹幕
                if (item.x + item.width < 0) {
                    val track = tracks.find { it.y == item.trackY }
                    track?.let {
                        it.lastEndTime = currentTime
                        it.lastEndX = item.x
                    }
                    iterator.remove()
                    continue
                }

                // 绘制
                val alpha = (danmakuAlpha * 255).toInt()

                // 描边
                strokePaint.alpha = alpha
                strokePaint.textSize = danmakuSize
                canvas.drawText(item.text, item.x, item.trackY.toFloat(), strokePaint)

                // 填充
                textPaint.style = Paint.Style.FILL
                textPaint.color = item.color
                textPaint.alpha = alpha
                textPaint.textSize = danmakuSize
                canvas.drawText(item.text, item.x, item.trackY.toFloat(), textPaint)
            }
        }
    }

    /**
     * 弹幕数据项
     */
    data class DanmakuItem(
        val text: String,
        val color: Int,
        val track: DanmakuTrack,
        var x: Float,
        val trackY: Int,
        val speed: Float,
        val width: Float
    )

    /**
     * 弹幕轨道
     */
    class DanmakuTrack(val y: Int) {
        var lastEndTime: Long = 0
        var lastEndX: Float = 0f
    }
}
