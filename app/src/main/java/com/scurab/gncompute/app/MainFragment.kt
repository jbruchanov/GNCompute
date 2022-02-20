package com.scurab.gncompute.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.scurab.gncompute.GLCompute
import com.scurab.gncompute.app.databinding.FragmentMainBinding
import com.scurab.gncompute.app.samples.BitmapSSBOSampleFragment
import com.scurab.gncompute.app.samples.ChangeBrightnessFragment
import com.scurab.gncompute.app.samples.VectorScalarMulFragment
import com.scurab.gncompute.app.tools.replaceFragment
import com.scurab.gncompute.app.tools.viewLifecycleLazy

class MainFragment : Fragment(R.layout.fragment_main) {

    private val binding by viewLifecycleLazy { FragmentMainBinding.bind(requireView()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!GLCompute.isSupported(requireContext())) {
            AlertDialog.Builder(requireContext())
                .setMessage("Device doesn't have support for OpenGL ES 3.1!")
                .setPositiveButton("Close") { _, _ -> requireActivity().finish() }
                .show()
            return
        }

        with(binding) {
            bitmapComputeShader.setOnClickListener { replaceFragment(BitmapSSBOSampleFragment()) }
            bitmapAll.setOnClickListener { replaceFragment(ChangeBrightnessFragment()) }
            scalarMul.setOnClickListener { replaceFragment(VectorScalarMulFragment()) }
        }
    }
}
