package com.xycz.bilibili_live.service.danmaku

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayOutputStream

/**
 * Brotli解压工具
 * B站弹幕使用Brotli压缩
 */
object BrotliDecoder {

    /**
     * 解压Brotli数据
     */
    fun decompress(data: ByteArray): ByteArray {
        return try {
            BrotliInputStream(data.inputStream()).use { bis ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var len: Int
                while (bis.read(buffer).also { len = it } != -1) {
                    output.write(buffer, 0, len)
                }
                output.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            data // 返回原始数据
        }
    }
}
