package com.scurab.glcompute.ext

import android.opengl.GLES31

fun Int.requirePositive(msg: String? = null): Int {
    require(this > 0) { msg ?: "Value:$this" }
    return this
}


fun Boolean.requireTrue(msg: String? = null): Boolean {
    require(this) { msg ?: "Value:$this" }
    return this
}

fun <T> T?.requireNotNull(msg: String? = null): T = requireNotNull(this) { msg ?: "Value is null" }

fun requireNoGlError() = checkGlError(true)

fun checkGlError(throwException: Boolean = false): Int {
    val err = GLES31.glGetError()
    if (err != 0) {
        println("Error:$err")
        if (throwException) {
            throw IllegalStateException("oGL error:$err")
        }
    }
    return err
}
