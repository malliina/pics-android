package com.skogberglabs.pics.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.skogberglabs.pics.PicsApp

abstract class ResourceFragment(private val layoutResource: Int) : Fragment() {
    val app: PicsApp get() = requireActivity().application as PicsApp

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layoutResource, container, false)
    }
}
