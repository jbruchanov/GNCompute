package com.scurab.glcompute.app

import android.app.Application
import com.scurab.glcompute.GLCompute
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val glCompute: GLCompute = GLCompute().also {
    GlobalScope.launch {
        it.start()
    }
}

class App : Application() {
    init {
        //just touch it to init it
        glCompute.toString()
    }
}
