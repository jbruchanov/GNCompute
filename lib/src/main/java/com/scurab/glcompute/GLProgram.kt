package com.scurab.glcompute

import com.scurab.glcompute.model.ComputeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/*
https://plugins.jetbrains.com/plugin/6993-glsl-support
 */

interface GLProgram<I, O> {
    val shaderSourceCode: String

    suspend fun execute(args: I): O

    companion object {

        fun loadShader(name: String): String {
            val url = requireNotNull(this::class.java.getResource("/assets/$name")) { "Unable to find '$name'" }
            return url.openStream().use {
                it.bufferedReader().readText()
            }
        }

        suspend fun GLProgram<Unit, Unit>.execute() = execute(Unit)
    }
}

abstract class BaseGLProgram<I, O> : GLProgram<I, O> {
    private var ref: ProgramContext? = null

    val programRef: Int get() = requireRef().programRef
    val computeScope: CoroutineScope get() = requireRef().computeScope
    val computeConfig: ComputeConfig get() = requireRef().computeConfig

    private fun requireRef() = requireNotNull(ref) { "$this is not loaded" }

    var measured: Long = 0; private set

    suspend fun load(glCompute: GLCompute) {
        require(ref == null) { "Already loaded" }
        ref = glCompute.loadProgram(this)
    }

    suspend fun unload(glCompute: GLCompute) {
        ref?.let { glCompute.unloadProgram(it) }
        ref = null
    }

    final override suspend fun execute(args: I): O = withContext(computeScope.coroutineContext) {
        val start = System.nanoTime()
        val r = onExecute(args)
        measured = System.nanoTime() - start
        r
    }

    protected abstract suspend fun onExecute(args: I): O
}
