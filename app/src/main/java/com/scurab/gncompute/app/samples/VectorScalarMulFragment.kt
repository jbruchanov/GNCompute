package com.scurab.gncompute.app.samples

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.scurab.gncompute.app.R
import com.scurab.gncompute.app.databinding.FragmentScalarBinding
import com.scurab.gncompute.app.gncompute
import com.scurab.gncompute.app.tools.toReadableTime
import com.scurab.gncompute.app.tools.viewLifecycleLazy
import com.scurab.gncompute.app.util.NeonCalc
import com.scurab.gncompute.gl.SampleMulProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureNanoTime

class VectorScalarMulFragment : Fragment(R.layout.fragment_scalar) {

    companion object {
        private const val JAVA = "Java/Array"
        private const val NEON_ARRAY = "Neon/Array"
        private const val NEON_BUFFER = "Neon/Buffer"
        private const val GPU = "GPU"
    }

    private class Item(val name: String, val res: Int, val debounce: Long) {
        override fun toString() = name
    }

    private val binding by viewLifecycleLazy { FragmentScalarBinding.bind(requireView()) }
    private var program: SampleMulProgram? = null
    private val multiplier = 2.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
    }

    private fun init() {
        var groupSize = gncompute.computeConfig.workGroupSize.x
        groupSize = if (groupSize < 128) {
            throw IllegalStateException("Invalid groupSize:${groupSize}, glES says min 128 is guarenteed, maybe init issue?")
        } else {
            if (groupSize > 1024) Log.d("BitmapSSBO", "Group size:${groupSize} set to max 1024 due to simple sample code")
            if (groupSize % 128 != 0) {
                groupSize = 128
                Log.d("BitmapSSBO", "Group size:${groupSize} set to 128 as it's not divisible by 128 with no reminder")
            }
            //just for simplicity of the sample, this number must divide the bitmap w*h with 0 remainder
            groupSize.coerceAtMost(1024)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews()
    }

    private fun bindViews() = with(binding) {
        method.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listOf(JAVA, GPU, NEON_ARRAY, NEON_BUFFER))
        size.text = "Size:${1 shl slider.value.toInt()}"
        slider.addOnChangeListener { _, value, isFromUser ->
            if (isFromUser) {
                size.text = "Size:${1 shl value.toInt()}"
            }
        }

        compute.setOnClickListener {
            log.text = "Working..."
            compute.isEnabled = false
            lifecycleScope.launch(Dispatchers.Default) {
                val size = 1 shl binding.slider.value.toInt()
                when (val type = method.selectedItem.toString()) {
                    JAVA -> javaCalc(size)
                    GPU -> gpuCalc(size)
                    NEON_ARRAY -> neonArray(size)
                    NEON_BUFFER -> neonBuffer(size)
                    else -> {
                        withContext(Dispatchers.Main) {
                            compute.isEnabled = true
                            log.text = "Invalid type:$type"
                        }
                    }
                }
            }
        }
    }

    private suspend fun javaCalc(size: Int) {
        val input = FloatArray(size) { it.toFloat() }
        val time = measureNanoTime {
            for (i in input.indices) {
                input[i] *= multiplier
            }
        }
        withContext(Dispatchers.Main) {
            reportResult(JAVA, size, time, input, multiplier)
        }
    }

    private suspend fun neonBuffer(size: Int) {
        val input = FloatArray(size) { it.toFloat() }
        val buffer = ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(input)
        val time = NeonCalc.mul(buffer, multiplier)
        withContext(Dispatchers.Main) {
            reportResult(NEON_BUFFER, size, time, input, multiplier)
        }
    }

    private suspend fun neonArray(size: Int) {
        val input = FloatArray(size) { it.toFloat() }
        val time = NeonCalc.mul(input, multiplier)
        withContext(Dispatchers.Main) {
            reportResult(NEON_ARRAY, size, time, input, multiplier)
        }
    }

    private suspend fun gpuCalc(size: Int) {
        program?.unload(gncompute)
        //the size must be small to be in workGroupCount boundaries!
        val program = SampleMulProgram(size).also {
            program = it
            it.load(gncompute)
        }
        val result = program.execute(multiplier)
        withContext(Dispatchers.Main) {
            reportResult(GPU, size, program.measured, result, multiplier)
        }
    }

    private fun reportResult(
        name: String,
        size: Int,
        time: Long,
        result: FloatArray,
        coef: Float
    ) {
        binding.compute.isEnabled = true
        binding.log.text = """
            Name: $name
            Size: $size
            Coef: $coef
            Time: ${time.toReadableTime()}
            Result[1]: ${result[1]}
            ResultLast: ${result.lastOrNull()}
            ResultLastShouldBe: ${(result.size - 1) * coef}
        """.trimIndent()
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalScope.launch {
            program?.unload(gncompute)
        }
    }
}
