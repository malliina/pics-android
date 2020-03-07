package com.skogberglabs.pics.ui

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.materialprogressbar.MaterialProgressBar

class Controls(val progress: MaterialProgressBar?, val list: RecyclerView, val feedback: TextView) {
    fun enableLoading() {
        progress?.let { it.visibility = View.VISIBLE }
        list.visibility = View.GONE
        feedback.visibility = View.GONE
    }

    fun showList() {
        progress?.let { it.visibility = View.GONE }
        list.visibility = View.VISIBLE
        feedback.visibility = View.GONE
    }

    fun display(message: String) {
        progress?.let { it.visibility = View.GONE }
        list.visibility = View.GONE
        feedback.visibility = View.VISIBLE
        feedback.text = message
    }
}
