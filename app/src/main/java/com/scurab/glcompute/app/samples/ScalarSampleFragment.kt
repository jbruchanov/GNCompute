package com.scurab.glcompute.app.samples

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.scurab.glcompute.app.R
import com.scurab.glcompute.app.databinding.FragmentScalarBinding
import com.scurab.glcompute.app.glCompute
import com.scurab.glcompute.app.tools.viewLifecycleLazy
import com.scurab.glcompute.sample.SampleMulProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScalarSampleFragment : Fragment(R.layout.fragment_scalar) {

    private class Item(val name: String, val res: Int, val debounce: Long) {
        override fun toString() = name
    }

    private val binding by viewLifecycleLazy { FragmentScalarBinding.bind(requireView()) }
    private var program: SampleMulProgram? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
    }

    private fun init() {
        var groupSize = glCompute.computeConfig.workGroupSize.x
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
        binding.size.text = "Size:${1 shl binding.slider.value.toInt()}"
        slider.addOnChangeListener { _, value, isFromUser ->
            if (isFromUser) {
                binding.size.text = "Size:${1 shl value.toInt()}"
            }
        }

        compute.setOnClickListener {
            binding.log.text = "Working..."
            lifecycleScope.launch(Dispatchers.Default) {
                program?.unload(glCompute)
                val size = 1 shl binding.slider.value.toInt()
                //the size must be small to be in workGroupCount boundaries!
                val program = SampleMulProgram(size).also {
                    this@ScalarSampleFragment.program = program
                    it.load(glCompute)
                }
                val coef = 2.5f
                val result = program.execute(coef)
                withContext(Dispatchers.Main) {
                    binding.log.text = """
                        Size:$size
                        Time:${program.measured / 1000} us"
                        Result[1]:${result[1]}
                        ResultLast:${result.lastOrNull()}
                        ResultLastShouldBe:${(result.size - 1) * coef}
                        """
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalScope.launch {
            program?.unload(glCompute)
        }
    }
}
