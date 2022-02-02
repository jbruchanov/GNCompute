package com.scurab.glcompute.sample

import android.graphics.Bitmap
import android.opengl.GLES31
import android.util.Log
import com.scurab.glcompute.BaseGLProgram
import com.scurab.glcompute.GLProgram.Companion.loadShader
import com.scurab.glcompute.ext.requireNoGlError
import com.scurab.glcompute.ext.requireNotNull
import com.scurab.glcompute.ext.requirePositive
import com.scurab.glcompute.ext.requireTrue
import com.scurab.glcompute.model.ComputeConfig
import com.scurab.glcompute.util.measure
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SampleBitmapProgram(private val groupSize: Int = 1024) : BaseGLProgram<SampleBitmapProgram.Args, Unit>() {

    class Args(val inBitmap: Bitmap, val outBitmap: Bitmap, val brightnessDiff: Int)

    private val TAG = "SIOProgram"

    override val shaderSourceCode: String = loadShader("SampleBitmapProgram.glsl")
        .replace("#define GROUP_SIZE -1", "#define GROUP_SIZE $groupSize")

    override suspend fun onExecute(args: Args) {
        require((args.inBitmap.width * args.inBitmap.height) % groupSize == 0) {
            "This sample is just designed to work with bitmap of size width:${args.inBitmap.width} x height:${args.inBitmap.height} is divisible by $groupSize with 0 remainder "
        }
        val config: ComputeConfig = computeConfig
        val input = ByteBuffer
            .allocateDirect(args.inBitmap.byteCount)
            .order(ByteOrder.nativeOrder())
        args.inBitmap.copyPixelsToBuffer(input)
        input.position(0)

        val dataSize = args.inBitmap.byteCount

        val iArray = IntArray(5)
        //create 2 buffers
        GLES31.glGenBuffers(1, iArray, 0)

        val measures = mutableListOf<Long>()

        //use program
        measure(measures) {
            GLES31.glUseProgram(programRef)
            requireNoGlError()
        }

        //load data into SSBO
        val (bufferData) = measure(measures) {
            val bufferData = iArray[0]
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, bufferData)
            GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, input.capacity(), input, GLES31.GL_STATIC_DRAW)
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0 /*glsl layout(binding) */, bufferData)
            requireNoGlError()
            bufferData
        }

        //allocate data out
        measure(measures) {
            val expLocation = GLES31.glGetUniformLocation(programRef, "scalar").requirePositive("Missing 'exponent' uniform in kernel")
            GLES31.glUniform1i(expLocation, args.brightnessDiff)
            requireNoGlError()
            expLocation
        }

        //run it
        measure(measures) {
            val groupX = (dataSize / Int.SIZE_BYTES) / groupSize
            GLES31.glDispatchCompute(groupX, 1, 1)
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
            requireNoGlError()
        }

        //get data back
        measure(measures) {
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, bufferData)
            val buff = (GLES31.glMapBufferRange(GLES31.GL_SHADER_STORAGE_BUFFER, 0, input.capacity(), GLES31.GL_MAP_READ_BIT) as? ByteBuffer).requireNotNull()
            args.outBitmap.copyPixelsFromBuffer(buff)
            GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER).requireTrue("Unable to release ogl storage buffer")
        }

        measure(measures) {
            //release
            GLES31.glDeleteBuffers(1, iArray, 0)
        }

        Log.d(TAG, "Steps(groupSize=${groupSize}, Times=[${measures.joinToString()}] => ${measures.sum()} [us]\nconfig:$config")
    }
}
