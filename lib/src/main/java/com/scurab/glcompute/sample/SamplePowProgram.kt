package com.scurab.glcompute.sample

import android.opengl.GLES31
import android.util.Log
import com.scurab.glcompute.BaseGLProgram
import com.scurab.glcompute.GLProgram.Companion.loadShader
import com.scurab.glcompute.ext.copyToFloatArray
import com.scurab.glcompute.model.ComputeConfig
import com.scurab.glcompute.requireNoGlError
import com.scurab.glcompute.requireNotNull
import com.scurab.glcompute.requireNotZero
import com.scurab.glcompute.requireTrue
import com.scurab.glcompute.util.measure
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SamplePowProgram(private val sampleDataSize: Int = 4096) : BaseGLProgram<Float, FloatArray>() {

    private val TAG = "SIOProgram"
    private val groupSize = 128
    private val memSampleDataSize = sampleDataSize * Float.SIZE_BYTES

    override val shaderSourceCode: String = loadShader("SamplePowProgram.glsl")
        .replace("#define GROUP_SIZE -1", "#define GROUP_SIZE $groupSize")

    private val input = ByteBuffer
        .allocate(memSampleDataSize)
        .order(ByteOrder.nativeOrder())

    init {
        //fill some random data into input
        input.asFloatBuffer().also { bb ->
            (0 until bb.capacity()).forEach {
                bb.put(it, it.toFloat())
            }
        }
    }

    override suspend fun onExecute(args: Float): FloatArray {
        val config: ComputeConfig = computeConfig
        val groupX = sampleDataSize / groupSize
        require(groupSize <= computeConfig.workGroupSize.x) { "GROUP_SIZE:${groupSize} is higher than OGL supported count:${computeConfig.workGroupSize.x}" }
        require(sampleDataSize % groupSize == 0) { "size:${sampleDataSize} % GROUP_SIZE:${groupSize} = ${sampleDataSize % groupSize}, this must be 0!" }
        require(groupX <= computeConfig.workGroupCount.x) { "groupX:${groupX} !<= computeConfig.workGroupCount.x:${computeConfig.workGroupCount.x}" }

        val iArray = IntArray(5)
        //create 2 buffers
        GLES31.glGenBuffers(2, iArray, 0)

        //use program
        val (_, m0) = measure {
            GLES31.glUseProgram(programRef)
            requireNoGlError()
        }

        //load data into SSBO
        val (bufferData, m1) = measure {
            val bufferData = iArray[0]
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, bufferData)
            GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, input.capacity(), input, GLES31.GL_STATIC_DRAW)
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0 /*glsl layout(binding) */, bufferData)
            requireNoGlError()
            bufferData
        }

        //allocate data out
        val (expLocation, m2) = measure {
            val expLocation = GLES31.glGetUniformLocation(programRef, "exponent").requireNotZero("Missing 'exponent' uniform in kernel")
            GLES31.glUniform1f(expLocation, args)
            requireNoGlError()
            expLocation
        }

        //run it
        val (_, m3) = measure {
            GLES31.glDispatchCompute(groupX, 1, 1)
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
            requireNoGlError()
        }

        //get data back
        val (result, m4) = measure {
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, bufferData)
            val buff = (GLES31.glMapBufferRange(GLES31.GL_SHADER_STORAGE_BUFFER, 0, memSampleDataSize, GLES31.GL_MAP_READ_BIT) as? ByteBuffer).requireNotNull()
            val result = buff.copyToFloatArray()
            GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER).requireTrue("Unable to release ogl storage buffer")
            result
        }

        measure {
            //release
            GLES31.glDeleteBuffers(1, iArray, 0)
        }

        val blockSize = 256
        if (result.size < 32) {
            Log.d(TAG, "Data:${result.joinToString()}")
        } else {
            (0 until (result.size / blockSize).coerceAtLeast(1)).forEach { i ->
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
        val times = arrayOf(m0, m1, m2, m3, m4)
        Log.d(TAG, "Steps(groupSize=${groupSize}, Times=[${times.joinToString()}] => ${times.sum()} [us]\nconfig:$config")
        return result
    }
}
