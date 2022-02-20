package com.scurab.glcompute.app.samples

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.scurab.glcompute.app.R
import com.scurab.glcompute.app.databinding.FragmentBrightnessTechBinding
import com.scurab.glcompute.app.tools.viewLifecycleLazy
import com.scurab.glcompute.app.util.BitmapBrightnessChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.roundToLong

class ImageItem(val name: String, val res: Int, val inSampleSize: Int) {
    override fun toString() = name
}

class ChangeBrightnessFragment : Fragment(R.layout.fragment_brightness_tech) {

    private val binding by viewLifecycleLazy { FragmentBrightnessTechBinding.bind(requireView()) }
    private val items = listOf(
        ImageItem("Image 512", R.drawable.test05k, 1),
        ImageItem("Image 1k", R.drawable.test1k, 1),
        ImageItem("Image 2k", R.drawable.test2k, 1),
        ImageItem("Image 4k", R.drawable.test4k, 2),
        ImageItem("Image 7k", R.drawable.test7k, 4),
    )
    private val times = mutableMapOf<ChangeBrightnessMethod, MutableList<Long>>()
    private val methods = listOf<ChangeBrightnessMethod>(
        ChangeBrightnessMethod.JavaIntArray,
        ChangeBrightnessMethod.JavaByteBuffer,
        ChangeBrightnessMethod.CIntArray,
        ChangeBrightnessMethod.CByteBuffer,
        ChangeBrightnessMethod.NeonIntArray,
        ChangeBrightnessMethod.NeonByteBuffer,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews()
    }

    private fun bindViews() = with(binding) {
        image.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
        method.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, methods)

        button.setOnClickListener {
            onChangeBrightness(image.selectedItem as ImageItem, method.selectedItem as ChangeBrightnessMethod, brightness.value.toInt())
        }

        image.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                kotlin.runCatching {
                    val imageItem = items[position]
                    val bo = BitmapFactory.Options().apply { inSampleSize = imageItem.inSampleSize }
                    binding.result.setImageBitmap(BitmapFactory.decodeResource(resources, imageItem.res, bo))
                    times.clear()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun onChangeBrightness(image: ImageItem, method: ChangeBrightnessMethod, diff: Int) {
        if (!method.isSupported) {
            Toast.makeText(requireContext(), "${method.name} is not supported by your arch!", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            val time = method.changeBrightness(binding, image, diff)
            if (time > 0) {
                val list = times.getOrPut(method) { mutableListOf() }
                list.add(time)
                binding.log.append("\nAverage time:${list.average().roundToLong().toReadableTime()}")
            }
        }
    }
}

data class Result(val result: Bitmap, val time: Long)

sealed class ChangeBrightnessMethod(val name: String) {

    override fun toString(): String = name

    open val isSupported = true

    private val workingDispatcher: CoroutineDispatcher = newFixedThreadPoolContext(1, "Default-ChangeBrightnessMethod")

    suspend fun changeBrightness(binding: FragmentBrightnessTechBinding, item: ImageItem, diff: Int): Long {
        var took = -1L
        with(binding) {
            log.text = ""
            log.append("Start: $name")
            button.isEnabled = false
            pbar.isVisible = true
            withContext(workingDispatcher) {
                val r = kotlin.runCatching { onChangeBrightness(binding.brightness.resources, item, diff) }
                Log.d("NeonByteBuffer", "Step6")
                withContext(Dispatchers.Main) {
                    Log.d("NeonByteBuffer", "Step7")
                    result.setImageBitmap(null)
                    if (r.isFailure) {
                        val exceptionOrNull = r.exceptionOrNull()!!
                        exceptionOrNull.printStackTrace()
                        log.append("\nFinished: $name\n${exceptionOrNull.stackTraceToString()}")
                    } else {
                        var (output, time) = r.getOrNull()!!
                        log.append("\nFinished: $name in ${time.toReadableTime()}")
                        Log.d("NeonByteBuffer", "Step8A")
                        output = if (item.inSampleSize > 1) {
                            Bitmap.createScaledBitmap(output, 1024, 1024, true)
                        } else output
                        Log.d("NeonByteBuffer", "Step8B")
                        result.setImageBitmap(output)
                        took = time
                    }
                    Log.d("NeonByteBuffer", "Step9")
                    button.isEnabled = true
                    pbar.isInvisible = true
                }
            }
        }
        return took
    }

    protected abstract fun onChangeBrightness(res: Resources, item: ImageItem, diff: Int): Result

    object JavaIntArray : ChangeBrightnessMethod("Java/IntArray") {
        override fun onChangeBrightness(res: Resources, item: ImageItem, diff: Int): Result {
            val bo = BitmapFactory.Options().apply { inMutable = true }
            val b = BitmapFactory.decodeResource(res, item.res, bo)
            val array = IntArray(b.width * b.height)
            b.getPixels(array, 0, b.width, 0, 0, b.width, b.height)
            val start = System.nanoTime()
            BitmapBrightnessChange.changeBrightness(array, diff)
            val time = System.nanoTime() - start
            b.setPixels(array, 0, b.width, 0, 0, b.width, b.height)
            return Result(b, time)
        }
    }

    object JavaByteBuffer : ChangeBrightnessMethod("Java/ByteBuffer") {
        override fun onChangeBrightness(res: Resources, item: ImageItem, diff: Int): Result {
            val bo = BitmapFactory.Options().apply { inMutable = true }
            val b = BitmapFactory.decodeResource(res, item.res, bo)
            val buffer = ByteBuffer.allocateDirect(b.byteCount)
            b.copyPixelsToBuffer(buffer)
            val start = System.nanoTime()
            BitmapBrightnessChange.changeBrightness(buffer, diff)
            val time = System.nanoTime() - start
            buffer.position(0)
            b.copyPixelsFromBuffer(buffer)
            return Result(b, time)
        }
    }

    object CIntArray : ChangeBrightnessMethod("C/IntArray") {
        override fun onChangeBrightness(res: Resources, item: ImageItem, diff: Int): Result {
            val bo = BitmapFactory.Options().apply { inMutable = true }
            val b = BitmapFactory.decodeResource(res, item.res, bo)
            val array = IntArray(b.width * b.height)
            b.getPixels(array, 0, b.width, 0, 0, b.width, b.height)
            val start = System.nanoTime()
            BitmapBrightnessChange.changeBrightnessCIntArray(array, diff)
            val time = System.nanoTime() - start
            b.setPixels(array, 0, b.width, 0, 0, b.width, b.height)
            return Result(b, time)
        }
    }

    object CByteBuffer : ChangeBrightnessMethod("C/ByteBufferArray") {
        override fun onChangeBrightness(res: Resources, item: ImageItem, diff: Int): Result {
            val bo = BitmapFactory.Options().apply { inMutable = true }
            val b = BitmapFactory.decodeResource(res, item.res, bo)
            val buffer = ByteBuffer.allocateDirect(b.byteCount)
            b.copyPixelsToBuffer(buffer)
            val start = System.nanoTime()
            BitmapBrightnessChange.changeBrightnessCBuffer(buffer, diff)
            val time = System.nanoTime() - start
            buffer.position(0)
            b.copyPixelsFromBuffer(buffer)
            return Result(b, time)
        }
    }

    object NeonIntArray : ChangeBrightnessMethod("C-Neon/IntArray") {
        override fun onChangeBrightness(res: Resources, item: ImageItem, diff: Int): Result {
            val bo = BitmapFactory.Options().apply { inMutable = true }
            val b = BitmapFactory.decodeResource(res, item.res, bo)
            val array = IntArray(b.width * b.height)
            b.getPixels(array, 0, b.width, 0, 0, b.width, b.height)
            val start = System.nanoTime()
            BitmapBrightnessChange.changeBrightnessNeon(array, diff)
            val end = System.nanoTime() - start
            b.setPixels(array, 0, b.width, 0, 0, b.width, b.height)
            return Result(b, end)
        }

        override val isSupported: Boolean get() = BitmapBrightnessChange.isNeonSupported
    }

    object NeonByteBuffer : ChangeBrightnessMethod("C-Neon/ByteBuffer") {
        override fun onChangeBrightness(res: Resources, item: ImageItem, diff: Int): Result {
            val bo = BitmapFactory.Options().apply { inMutable = true }
            val b = BitmapFactory.decodeResource(res, item.res, bo)
            val buffer = ByteBuffer.allocateDirect(b.byteCount)
            b.copyPixelsToBuffer(buffer)
            val start = System.nanoTime()
            BitmapBrightnessChange.changeBrightnessNeonBuffer(buffer, diff)
            val time = System.nanoTime() - start
            buffer.position(0)
            b.copyPixelsFromBuffer(buffer)
            return Result(b, time)
        }

        override val isSupported: Boolean get() = BitmapBrightnessChange.isNeonSupported
    }
}

fun Long.toReadableTime(): String {
    val units = listOf("ns", "us", "ms", "s")
    var counter = 0
    var semiValue = this
    var lastDiv = semiValue.toDouble()
    while (semiValue > 1000) {
        lastDiv = semiValue / 1000.0
        semiValue /= 1000
        counter++
    }
    val v = String.format("%.3f", lastDiv)
    return "$v ${units[counter]}"
}

val Int.f3: String get() = this.toString().padStart(3, ' ')

fun Int.toARGBComponents(): String {
    val a = this ushr 24
    var r = this ushr 16 and 0xFF
    var g = this ushr 8 and 0xFF
    var b = this ushr 0 and 0xFF
    return buildString {
        append("[")
        append("${a.f3},")
        append("${r.f3},")
        append("${g.f3},")
        append("${b.f3}")
        append("}")
    }
}
