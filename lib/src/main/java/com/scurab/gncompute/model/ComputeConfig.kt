package com.scurab.gncompute.model

data class ComputeConfig(
    val workGroupSize: Vec3Int,
    val workGroupCount: Vec3Int,
    val workGroupInvocations: Int,
    val atomicCounters: AtomicCounters
) {

    companion object {
        val EMPTY = ComputeConfig(Vec3Int.ZERO, Vec3Int.ZERO, 0, AtomicCounters.EMPTY)
    }
}
