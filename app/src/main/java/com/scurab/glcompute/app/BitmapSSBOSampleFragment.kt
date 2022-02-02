package com.scurab.glcompute.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.scurab.glcompute.app.databinding.FragmentBitmapSsboBinding
import com.scurab.glcompute.app.tools.viewLifecycleLazy
import com.scurab.glcompute.sample.SampleBitmapProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BitmapSSBOSampleFragment : Fragment(R.layout.fragment_bitmap_ssbo) {

    private class Item(val name: String, val res: Int, val debounce: Long) {
        override fun toString() = name
    }

    private val binding by viewLifecycleLazy { FragmentBitmapSsboBinding.bind(requireView()) }
    private lateinit var program: SampleBitmapProgram
    private var selectedBitmap: Bitmap? = null
    private var outputBitmap: Bitmap? = null
    private var sliderChannel = Channel<Int>()

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
        program = SampleBitmapProgram(groupSize)
        lifecycleScope.launch {
            program.load(glCompute)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews()
    }

    private fun bindViews() {
        binding.imagePicker.apply {
            val items = listOf(
                Item("Image 1k", R.drawable.test1k, 0),
                Item("Image 2k", R.drawable.test2k, 10),
                Item("Image 4k", R.drawable.test4k, 50),
                Item("Image 7k", R.drawable.test7k, 250),
            )

            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val item = items[position]
                    selectedBitmap?.recycle()
                    outputBitmap?.recycle()
                    val result = kotlin.runCatching {
                        selectedBitmap = BitmapFactory.decodeResource(resources, item.res).also {
                            outputBitmap = Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888)
                            setBitmap(it, 0, 0)
                        }
                    }
                    binding.slider.value = 0f
                    subscribeChannel(item.debounce)
                    if (result.isFailure) {
                        binding.image.setImageBitmap(null)
                        binding.log.text = result.exceptionOrNull()?.message ?: "Error no message"
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        binding.slider.addOnChangeListener { _, value, isFromUser ->
            if (isFromUser) {
                sliderChannel.trySend(value.toInt())
            }
        }
    }

    private fun setBitmap(bitmap: Bitmap, brightness: Int, measured: Long) {
        val start = System.nanoTime()
        val preview = if (bitmap.width > 2048) {
            //just downscale preview, android won't show 7k, and older devices have problem with 4k as well
            Bitmap.createScaledBitmap(bitmap, 1024, 1024, true)
        } else {
            bitmap
        }
        binding.image.setImageBitmap(preview)
        val loadTime = System.nanoTime() - start

        binding.log.text = """
                            ImageResolution: ${bitmap.width}x${bitmap.height}
                            Brightness: $brightness
                            GPUProcessTime: ${String.format("%.3f", measured / 1_000_000.0f)} ms
                            PreviewLoadTime: ${String.format("%.3f", loadTime / 1_000_000.0f)} ms
                        """.trimIndent()
    }

    private var sliderSubscription: Job? = null

    private fun subscribeChannel(debounce: Long) {
        sliderSubscription?.cancel()
        sliderChannel = Channel()
        sliderSubscription = lifecycleScope.launch(glCompute.coroutineContext) {
            sliderChannel.consumeAsFlow()
                .debounce(debounce)
                .collect { value ->
                    val selectedBitmap = selectedBitmap ?: return@collect
                    val outputBitmap = outputBitmap ?: return@collect

                    val result = kotlin.runCatching { program.execute(SampleBitmapProgram.Args(selectedBitmap, outputBitmap, value)) }

                    withContext(Dispatchers.Main) {
                        if (result.isFailure) {
                            binding.image.setImageBitmap(null)
                            binding.log.text = result.exceptionOrNull()?.message ?: "Error no message"
                        } else {
                            setBitmap(outputBitmap, value, program.measured)
                        }
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalScope.launch {
            program.unload(glCompute)
        }
    }
}
