package com.scurab.glcompute

import android.opengl.GLES20
import android.opengl.GLES20.glGenBuffers
import android.opengl.GLES30
import android.opengl.GLES30.glMapBufferRange
import android.opengl.GLES30.glUnmapBuffer
import android.opengl.GLES31.GL_SHADER_STORAGE_BARRIER_BIT
import android.opengl.GLES31.GL_SHADER_STORAGE_BUFFER
import android.opengl.GLES31.glBindBuffer
import android.opengl.GLES31.glBindBufferBase
import android.opengl.GLES31.glBufferData
import android.opengl.GLES31.glDispatchCompute
import android.opengl.GLES31.glMemoryBarrier
import android.opengl.GLES31.glUseProgram
import com.scurab.glcompute.ext.copyToFloatArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

interface GLProgram<I, O> {
    val shaderSourceCode: String

    suspend fun execute(args: I): O
}

abstract class BaseGLProgram<I, O> : GLProgram<I, O> {
    private var ref: LoadResult? = null

    val programRef: Int get() = requireNotNull(ref) { "$this is not loaded" }.programRef
    val computeScope: CoroutineScope get() = requireNotNull(ref) { "$this is not loaded" }.computeScope

    suspend fun load(glCompute: GLCompute) {
        require(ref == null) { "Already loaded" }
        ref = glCompute.loadProgram(this)
    }
}

class SampleProgram : BaseGLProgram<Int, FloatArray>() {
    override val shaderSourceCode: String = """
        #version 310 es
        layout(local_size_x = 1) in;
        layout(std430, binding = 0) buffer Output {
            writeonly highp float data[];
        } outputData;
        
        void main() {
            uint ident = gl_GlobalInvocationID.x;
            outputData.data[ident] = sin(float(ident));
        }
    """.trimIndent()

    override suspend fun execute(args: Int): FloatArray = withContext(computeScope.coroutineContext) {
        val size = 4096
        val iArray = intArrayOf(0)
        glGenBuffers(1, iArray, 0)
        val buffer = iArray[0]
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer)
        glBufferData(GL_SHADER_STORAGE_BUFFER, size * Float.SIZE_BYTES, null, GLES20.GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, buffer);

        requireNoGlError()
        glUseProgram(programRef)
        glDispatchCompute(args, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)
        requireNoGlError()

        val buff = (glMapBufferRange(GL_SHADER_STORAGE_BUFFER, 0, size * Float.SIZE_BYTES, GLES30.GL_MAP_READ_BIT) as? ByteBuffer).requireNotNull()
        val result = buff.copyToFloatArray()
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER).requireTrue("Unable to release ogl storage buffer")
        result
    }
}
