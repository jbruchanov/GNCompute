package com.scurab.glcompute.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.scurab.glcompute.GLCompute
import com.scurab.glcompute.SampleProgram
import com.scurab.glcompute.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Float.max
import java.lang.Float.min
import java.lang.Math.sin
import kotlin.system.measureNanoTime

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityMainBinding.inflate(LayoutInflater.from(this)).also {
            setContentView(it.root)
        }

        binding.button.isEnabled = false

        val glCompute = GLCompute()
        val program = SampleProgram()

        GlobalScope.launch {
            glCompute.start()
            program.load(glCompute)
            withContext(Dispatchers.Main) {
                binding.button.isEnabled = true
            }
        }

        binding.button.setOnClickListener {
            GlobalScope.launch {
                Log.d(TAG, "CPU ${measure { cpu(4096) }}us")
                Log.d(TAG, "GPU ${test(program, 4096)}us")

                val cpu = cpu(4096)
                val gpu = program.execute(4096)

                var min = Float.MAX_VALUE
                var max = Float.MIN_VALUE
                cpu.indices.forEach {
                    val diff = cpu[it] - gpu[it]
                    min = min(min, diff)
                    max = max(max, diff)
                }
                Log.d(TAG, "Diffs min:$min, max:$max")
            }
        }
    }

    fun cpu(size: Int) = FloatArray(size) { sin(it.toDouble()).toFloat() }

    private suspend fun test(program: SampleProgram, size: Int): Long {
        return measure {
            val result = program.execute(size)
            if (result.sum() == 0f) {
                Log.d(TAG, "GPU $size - FAILED result")
            }
        }
    }

    public inline fun measure(block: () -> Unit): Long = (measureNanoTime(block) / 1000.0).toLong()
}
