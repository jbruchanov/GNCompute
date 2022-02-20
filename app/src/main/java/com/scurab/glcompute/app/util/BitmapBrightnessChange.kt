package com.scurab.glcompute.app.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureNanoTime

object BitmapBrightnessChange {

    init {
        System.loadLibrary("glcompute")
    }

    val isNeonSupported: Boolean = _isNeonSupported()

    private external fun _isNeonSupported(): Boolean

    fun changeBrightness(bitmap: IntArray, diff: Int) {
        bitmap.forEachIndexed { index, i ->
            bitmap[index] = i.changeBrightness(diff)
        }
    }

    fun changeBrightness(buff: ByteBuffer, diff: Int) {
        buff.position(0)
        val ibuff = buff.order(ByteOrder.nativeOrder()).asIntBuffer()
        for (i in 0 until ibuff.capacity()) {
            ibuff.put(i, ibuff[i].changeBrightness(diff))
        }
    }

    fun changeBrightnessCIntArray(bitmap: IntArray, diff: Int): Long {
        return measureNanoTime {
            _changeBrightnessCIntArray(bitmap, diff)
        }
    }

    private external fun _changeBrightnessCIntArray(bitmap: IntArray, diff: Int): Long

    fun changeBrightnessCBuffer(bitmap: ByteBuffer, diff: Int): Long {
        return measureNanoTime {
            _changeBrightnessCBuffer(bitmap, diff)
        }
    }

    private external fun _changeBrightnessCBuffer(bitmap: ByteBuffer, diff: Int): Long

    fun changeBrightnessNeon(bitmap: IntArray, diff: Int): Long {
        require(bitmap.size % 4 == 0) { "Invalid bitmap size, bitmap.size:[${bitmap.size}, must be divisible by 4!" }
        return measureNanoTime {
            _changeBrightnessNeon(bitmap, diff)
        }
    }

    private external fun _changeBrightnessNeon(bitmap: IntArray, diff: Int): Long

    fun changeBrightnessNeonBuffer(buffer: ByteBuffer, diff: Int): Long {
        require(buffer.capacity() % 4 == 0) { "Invalid bitmap size, buffer.capacity():${buffer.capacity()}, must be divisible by 4!" }
        return measureNanoTime {
            _changeBrightnessNeonBuffer(buffer, diff)
        }
    }

    private external fun _changeBrightnessNeonBuffer(bitmap: ByteBuffer, diff: Int): Long
}


private fun Int.changeBrightness(diff: Int): Int {
    val a = this ushr 24
    var r = this ushr 16 and 0xFF
    var g = this ushr 8 and 0xFF
    var b = this ushr 0 and 0xFF

    r = (r + diff).coerceIn(0, 255)
    g = (g + diff).coerceIn(0, 255)
    b = (b + diff).coerceIn(0, 255)

    return (a shl 24) + (r shl 16) + (g shl 8) + b
}
