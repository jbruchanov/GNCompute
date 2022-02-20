package com.scurab.gncompute.ext

import com.scurab.gncompute.model.Vec3Int

fun IntArray.toVec3(offset: Int = 0): Vec3Int {
    require(this.size >= 3) { "Invalid array size, must be at least 3, was:${this.size}" }
    return Vec3Int(this, offset)
}
