package com.scurab.glcompute

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES10.glGetIntegerv
import android.opengl.GLES20
import android.opengl.GLES20.GL_NONE
import android.opengl.GLES20.glAttachShader
import android.opengl.GLES20.glDeleteProgram
import android.opengl.GLES20.glDeleteShader
import android.opengl.GLES20.glLinkProgram
import android.opengl.GLES20.glUseProgram
import android.opengl.GLES30
import android.opengl.GLES30.glGetIntegeri_v
import android.opengl.GLES31
import android.opengl.GLES31.GL_COMPUTE_SHADER
import android.opengl.GLES31.GL_MAX_ATOMIC_COUNTER_BUFFER_SIZE
import android.opengl.GLES31.GL_MAX_COMPUTE_ATOMIC_COUNTERS
import android.opengl.GLES31.GL_MAX_COMPUTE_ATOMIC_COUNTER_BUFFERS
import android.opengl.GLES31.GL_MAX_COMPUTE_WORK_GROUP_COUNT
import android.opengl.GLES31.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS
import android.opengl.GLES31.GL_MAX_COMPUTE_WORK_GROUP_SIZE
import com.scurab.glcompute.ext.requireNoGlError
import com.scurab.glcompute.ext.requirePositive
import com.scurab.glcompute.ext.requireTrue
import com.scurab.glcompute.ext.toVec3
import com.scurab.glcompute.model.AtomicCounters
import com.scurab.glcompute.model.ComputeConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.coroutines.CoroutineContext

/**
 * @param dispatcher - specific dispatcher to run the GLES on. Be sure it's SINGLE thread dispatcher!
 */
class GLCompute(private val dispatcher: CoroutineDispatcher = defaultDispatcher) : CoroutineScope {

    private var egl10: EGL10 = (EGLContext.getEGL() as EGL10)
    private var eglContext: EGLContext? = null
    private var eglDisplay: EGLDisplay? = null
    private var isStarted = false

    private val rootJob = Job()
    override val coroutineContext: CoroutineContext get() = dispatcher + rootJob
    var computeConfig: ComputeConfig = ComputeConfig.EMPTY; private set

    private fun requireNotStarted() = require(!isStarted) { "Already started" }
    private fun requireStarted() = require(isStarted) { "Not started" }

    suspend fun start(): GLCompute {
        requireNotStarted()
        isStarted = true
        withContext(coroutineContext) {
            val eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY).also {
                this@GLCompute.eglDisplay = it
            }

            egl10.eglInitialize(eglDisplay, intArrayOf(3, 1))
                .requireTrue("Couldn't init EGL")

            val attribList = intArrayOf(
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_NONE
            )

            val numConfigs = IntArray(1)
            egl10.eglChooseConfig(eglDisplay, attribList, null, 0, numConfigs)
                .requireTrue("eglChooseConfig")

            val configs = arrayOfNulls<EGLConfig>(numConfigs[0])
            egl10.eglChooseConfig(eglDisplay, attribList, configs, configs.size, numConfigs)
                .requireTrue("eglChooseConfig")

            val config = configs.first()

            val attribs = intArrayOf(
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL10.EGL_NONE
            )
            val eglContext = egl10.eglCreateContext(eglDisplay, config, EGL10.EGL_NO_CONTEXT, attribs)
                .also { this@GLCompute.eglContext = it }

            egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, eglContext).requireTrue("eglMakeCurrent")
            requireNoGlError()

            loadConfigs()
        }
        return this
    }

    private fun loadConfigs() {
        val workGroupSize = IntArray(3)
            .also { arr -> arr.indices.forEach { glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_SIZE, it, arr, it) } }
        val workGroupCount = IntArray(3)
            .also { arr -> arr.indices.forEach { glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, it, arr, it) } }
        val out = IntArray(10)

        glGetIntegerv(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, out, 0)
        glGetIntegerv(GL_MAX_ATOMIC_COUNTER_BUFFER_SIZE, out, 1)
        glGetIntegerv(GL_MAX_COMPUTE_ATOMIC_COUNTERS, out, 2)
        glGetIntegerv(GL_MAX_COMPUTE_ATOMIC_COUNTER_BUFFERS, out, 3)

        computeConfig = ComputeConfig(
            workGroupSize.toVec3(), workGroupCount.toVec3(), out[0],
            AtomicCounters(maxBufferSize = out[1], maxComputeCounters = out[2], maxComputeCounterBuffers = out[3])
        )
    }

    fun stop() {
        requireStarted()
        egl10.eglDestroyContext(eglDisplay, eglContext)

        eglDisplay = null
        eglContext = null
    }

    internal suspend fun loadProgram(program: GLProgram<*, *>): ProgramContext = withContext(coroutineContext) {
        val programRef = GLES31.glCreateProgram().requirePositive()
        val shaderRef = GLES31.glCreateShader(GL_COMPUTE_SHADER).requirePositive()

        GLES31.glShaderSource(shaderRef, program.shaderSourceCode)
        GLES31.glCompileShader(shaderRef)

        val iArray = IntArray(1)
        GLES31.glGetShaderiv(shaderRef, GLES30.GL_COMPILE_STATUS, iArray, 0)
        if (iArray[0] != GLES30.GL_TRUE) {
            val msg = GLES31.glGetShaderInfoLog(shaderRef)
            throw IllegalStateException("Unable to compile compute shader, msg:'$msg'")
        }

        glAttachShader(programRef, shaderRef)
        glLinkProgram(programRef)

        iArray[0] = GL_NONE
        // Check if there were any issues linking the shader.
        GLES20.glGetProgramiv(programRef, GLES20.GL_LINK_STATUS, iArray, 0)
        if (iArray[0] != GLES30.GL_TRUE) {
            val msg = GLES31.glGetProgramInfoLog(shaderRef)
            throw IllegalStateException("Unable to link compute shader, msg:'$msg'")
        }
        ProgramContext(programRef, shaderRef, this@GLCompute, computeConfig)
    }

    suspend fun unloadProgram(programContext: ProgramContext) = withContext(coroutineContext) {
        glUseProgram(programContext.programRef)
        requireNoGlError()
        glDeleteShader(programContext.shaderRef)
        requireNoGlError()
        glDeleteProgram(programContext.programRef)
        requireNoGlError()
    }

    companion object {

        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

        private val defaultDispatcher: CoroutineDispatcher = newFixedThreadPoolContext(1, "Default-GLCompute")

        fun isSupported(context: Context): Boolean {
            val configurationInfo = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo
            return configurationInfo.reqGlEsVersion >= 0x30001
        }
    }
}

data class ProgramContext(
    val programRef: Int,
    val shaderRef: Int,
    val computeScope: CoroutineScope,
    val computeConfig: ComputeConfig
)
