package com.scurab.glcompute.ext

import java.nio.ByteBuffer
import java.nio.ByteOrder


fun ByteBuffer.copyToFloatArray(order: ByteOrder = ByteOrder.nativeOrder()) = order(order)
    .asFloatBuffer()
    .let { fb -> FloatArray(fb.capacity()) { fb.get(it) } }

fun ByteBuffer.copyToIntArray(order: ByteOrder = ByteOrder.nativeOrder()) = order(order)
    .asIntBuffer()
    .let { fb -> IntArray(fb.capacity()) { fb.get(it) } }
