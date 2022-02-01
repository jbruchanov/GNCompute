package com.scurab.glcompute.util

data class MeasuredResult<T>(val data: T, val timeUs: Long)

inline fun <T> measure(buffer: MutableList<Long>? = null, block: () -> T): MeasuredResult<T> {
    val start = System.nanoTime()
    val data = block()
    return MeasuredResult(data, (System.nanoTime() - start) / 1000).also {
        buffer?.add(it.timeUs)
    }
}
