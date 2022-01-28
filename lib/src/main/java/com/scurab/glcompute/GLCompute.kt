package com.scurab.glcompute

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES20.GL_NONE
import android.opengl.GLES20.glAttachShader
import android.opengl.GLES20.glLinkProgram
import android.opengl.GLES30
import android.opengl.GLES31
import android.opengl.GLES31.GL_COMPUTE_SHADER
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

class GLCompute(private val dispatcher: CoroutineDispatcher = defaultDispatcher) : CoroutineScope {

    private var egl10: EGL10 = (EGLContext.getEGL() as EGL10)
    private var eglContext: EGLContext? = null
    private var eglDisplay: EGLDisplay? = null
    private val isStarted = false

    private val rootJob = Job()
    override val coroutineContext: CoroutineContext get() = dispatcher + rootJob

    suspend fun start(): GLCompute {
        require(!isStarted) { "Already started" }

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
        }
        return this
    }

    fun stop() {
        require(isStarted) { "Not started" }
        egl10.eglDestroyContext(eglDisplay, eglContext)

        eglDisplay = null
        eglContext = null
    }

    internal suspend fun loadProgram(program: GLProgram<*, *>): LoadResult = withContext(coroutineContext) {
        val programRef = GLES31.glCreateProgram().requireNotZero()
        val shaderRef = GLES31.glCreateShader(GL_COMPUTE_SHADER).requireNotZero()

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
        LoadResult(programRef, shaderRef, this@GLCompute)
    }

    companion object {

        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

        private val defaultDispatcher: CoroutineDispatcher = newFixedThreadPoolContext(1, "Default-GLCompute")

        fun isSupported(context: Context) {
            val configurationInfo = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo
            require(configurationInfo.reqGlEsVersion >= 0x30001) { "Unsupported OGL version:${configurationInfo.reqGlEsVersion}, must be at least 3.1" }
        }
    }
}

fun Int.requireNotZero(msg: String? = null): Int {
    require(this != 0) { msg ?: "Value:$this" }
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

data class LoadResult(
    val programRef: Int,
    val shaderRef: Int,
    val computeScope: CoroutineScope
)
