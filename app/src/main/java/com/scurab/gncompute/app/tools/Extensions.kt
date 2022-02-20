package com.scurab.gncompute.app.tools

fun Long.toReadableTime(): String {
    val units = listOf("ns", "us", "ms", "s")
    var counter = 0
    var semiValue = this
    var lastDiv = semiValue.toDouble()
    while (semiValue > 1000) {
        lastDiv = semiValue / 1000.0
        semiValue /= 1000
        counter++
    }
    val v = String.format(if (counter == 0) "%s" else "%.3f", lastDiv)
    return "$v ${units[counter]}"
}

val Int.f3: String get() = this.toString().padStart(3, ' ')

fun Int.toARGBComponents(): String {
    val a = this ushr 24
    var r = this ushr 16 and 0xFF
    var g = this ushr 8 and 0xFF
    var b = this ushr 0 and 0xFF
    return buildString {
        append("[")
        append("${a.f3},")
        append("${r.f3},")
        append("${g.f3},")
        append("${b.f3}")
        append("}")
    }
}
