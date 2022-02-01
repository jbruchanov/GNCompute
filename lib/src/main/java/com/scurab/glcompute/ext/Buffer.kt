package com.scurab.glcompute.ext

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer


fun ByteBuffer.copyToFloatArray(order: ByteOrder = ByteOrder.nativeOrder()) = FloatBuffer
    .allocate(capacity() / Float.SIZE_BYTES)
    .put(order(order).asFloatBuffer())
    .array()

fun ByteBuffer.copyToIntArray(order: ByteOrder = ByteOrder.nativeOrder()) = IntBuffer
    .allocate(capacity() / Int.SIZE_BYTES)
    .put(order(order).asIntBuffer())
    .array()
