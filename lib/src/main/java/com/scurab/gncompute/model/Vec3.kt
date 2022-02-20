package com.scurab.gncompute.model

fun vec3(x: Int, y: Int, z: Int) = Vec3Int(x, y, z)

data class Vec3Int(val x: Int, val y: Int, val z: Int) {

    constructor(array: IntArray, offset: Int = 0) : this(array[offset], array[offset + 1], array[offset + 2])

    companion object {
        val ZERO = vec3(0, 0, 0)
    }
}
