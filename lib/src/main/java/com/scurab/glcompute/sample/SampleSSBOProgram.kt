package com.scurab.glcompute.sample

import android.opengl.GLES31
import android.util.Log
import com.scurab.glcompute.BaseGLProgram
import com.scurab.glcompute.GLProgram.Companion.loadShader
import com.scurab.glcompute.ext.copyToFloatArray
import com.scurab.glcompute.model.ComputeConfig
import com.scurab.glcompute.requireNoGlError
import com.scurab.glcompute.requireNotNull
import com.scurab.glcompute.requireTrue
import com.scurab.glcompute.util.measure
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SampleSSBOProgram(private val sampleDataSize: Int = 4096) : BaseGLProgram<Int, FloatArray>() {

    private val TAG = "SIOProgram"
    private val groupSize = 128
    private val memSampleDataSize = sampleDataSize * Float.SIZE_BYTES

    override val shaderSourceCode: String = loadShader("SampleSSBOProgram.glsl")
        .replace("#define GROUP_SIZE -1", "#define GROUP_SIZE $groupSize")

    private val input = ByteBuffer
        .allocate(memSampleDataSize)
        .order(ByteOrder.nativeOrder())

    init {
        //fill some random data into input
        input.asFloatBuffer().also { bb ->
            (0 until bb.capacity()).forEach {
                val sign = if (it % 2 == 0) 1 else -1
                bb.put(it, it.toFloat() * sign)
            }
        }
    }

    override suspend fun onExecute(args: Int): FloatArray {
        val config: ComputeConfig = computeConfig
        val groupX = sampleDataSize / groupSize
        require(groupSize <= computeConfig.workGroupSize.x) { "GROUP_SIZE:${groupSize} is higher than OGL supported count:${computeConfig.workGroupSize.x}" }
        require(sampleDataSize % groupSize == 0) { "size:${sampleDataSize} % GROUP_SIZE:${groupSize} = ${sampleDataSize % groupSize}, this must be 0!" }
        require(groupX <= computeConfig.workGroupCount.x) { "groupX:${groupX} !<= computeConfig.workGroupCount.x:${computeConfig.workGroupCount.x}" }

        val iArray = IntArray(5)
        //create 2 buffers
        GLES31.glGenBuffers(2, iArray, 0)

        //load data into SSBO
        val (_, m11) = measure {
            val bufferIn = iArray[0]
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, bufferIn)
            GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, input.capacity(), input, GLES31.GL_STATIC_DRAW)
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0 /*glsl layout(binding) */, bufferIn)
            requireNoGlError()
            bufferIn
        }

        //allocate data out
        val (bufferOut, m12) = measure {
            val buffer = iArray[1]
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, buffer)
            GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, memSampleDataSize, null, GLES31.GL_STATIC_DRAW)
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1 /*glsl layout(binding) */, buffer)
            requireNoGlError()
            buffer
        }

        //use program
        val (_, m2) = measure {
            GLES31.glUseProgram(programRef)
            requireNoGlError()
        }

        //run it
        val (_, m3) = measure {
            GLES31.glDispatchCompute(groupX, 1, 1)
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
            requireNoGlError()
        }

        //get data back
        val (result, m4) = measure {
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, bufferOut)
            val buff = (GLES31.glMapBufferRange(GLES31.GL_SHADER_STORAGE_BUFFER, 0, memSampleDataSize, GLES31.GL_MAP_READ_BIT) as? ByteBuffer).requireNotNull()
            val result = buff.copyToFloatArray()
            GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER).requireTrue("Unable to release ogl storage buffer")
            result
        }

        measure {
            //release
            GLES31.glDeleteBuffers(2, iArray, 0)
        }

        val blockSize = 256
        if (result.size < 32) {
            Log.d(TAG, "Data:${result.joinToString()}")
        } else {
            (0 until (result.size / blockSize)).forEach { i ->
                val log = result
                    .drop(i * blockSize)
                    .take(blockSize)
                    .windowed(32, 32)
                    .joinToString("\n") { it.joinToString() }

                Log.d(
                    TAG, "Data[${i * blockSize} -> ${(i + 1) * blockSize}]:\n$log"
                )
            }
        }
        Log.d(TAG, "Steps(groupSize=${groupSize}, Times=[$m11, $m12, $m2, $m3, $m4] => ${m11 + m12 + m2 + m3 + m4} [us]\nconfig:$config")
        return result
    }
}
