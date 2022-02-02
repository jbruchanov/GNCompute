package com.scurab.glcompute.app.tools

import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.scurab.glcompute.app.R

fun Fragment.replaceFragment(fragment: Fragment) {
    requireActivity().supportFragmentManager.commit {
        addToBackStack(null)
        replace(R.id.fragment_container, fragment)
    }
}
