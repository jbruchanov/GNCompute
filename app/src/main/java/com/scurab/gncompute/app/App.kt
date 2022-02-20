package com.scurab.gncompute.app

import android.app.Application
import com.scurab.gncompute.GLCompute
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val gncompute: GLCompute = GLCompute().also {
    GlobalScope.launch {
        it.start()
    }
}

class App : Application() {
    init {
        //just touch it to init it
        gncompute.toString()
    }
}
