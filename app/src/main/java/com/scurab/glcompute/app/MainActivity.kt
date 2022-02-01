package com.scurab.glcompute.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.scurab.glcompute.GLCompute
import com.scurab.glcompute.app.databinding.ActivityMainBinding
import com.scurab.glcompute.sample.SampleSSBOProgram
import com.scurab.glcompute.util.measure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Math.sin

class MainActivity : AppCompatActivity() {
    private lateinit var glCompute: GLCompute

    private val TAG = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityMainBinding.inflate(LayoutInflater.from(this)).also {
            setContentView(it.root)
        }

        binding.button.isEnabled = false

        glCompute = GLCompute()
        val program = SampleSSBOProgram()

        GlobalScope.launch {
            glCompute.start()
            program.load(glCompute)
            withContext(Dispatchers.Main) {
                binding.button.isEnabled = true
            }
        }

        binding.button.setOnClickListener {
            GlobalScope.launch {
                val (data, time) = measure { program.execute(16) }
                Log.d(TAG, "Sum:${data}, took:${time}us")
            }
        }
    }

    fun cpu(size: Int) = FloatArray(size) { sin(it.toDouble()).toFloat() }
}
