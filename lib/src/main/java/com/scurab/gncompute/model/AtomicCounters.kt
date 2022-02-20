package com.scurab.gncompute.model

data class AtomicCounters(
    val maxBufferSize: Int,
    val maxComputeCounters: Int,
    val maxComputeCounterBuffers: Int,
) {
    companion object {
        val EMPTY = AtomicCounters(0, 0, 0)
    }
}
