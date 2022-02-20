package com.scurab.gncompute.app.util

import java.nio.ByteBuffer

object NeonCalc {
    init {
        System.loadLibrary("gncompute")
    }

    fun mul(array: FloatArray, multiplier: Float): Long {
        return _mulArray(array, multiplier)
    }

    private external fun _mulArray(array: FloatArray, multiplier: Float): Long

    fun mul(buffer: ByteBuffer, multiplier: Float): Long {
        return _mulBuffer(buffer, multiplier)
    }

    private external fun _mulBuffer(array: ByteBuffer, multiplier: Float): Long
}
