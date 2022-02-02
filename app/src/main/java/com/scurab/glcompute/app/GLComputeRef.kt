package com.scurab.glcompute.app

import com.scurab.glcompute.GLCompute
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val glCompute: GLCompute = GLCompute().also {
    GlobalScope.launch {
        it.start()
    }
}
