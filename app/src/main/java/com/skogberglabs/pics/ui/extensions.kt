package com.skogberglabs.pics.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.skogberglabs.pics.ui.gallery.SpacingDecorator

fun <T, U> LiveData<T>.map(f: (t: T) -> U): LiveData<U> = Transformations.map(this, f)
fun <T> LiveData<T>.distinctUntilChanged(): LiveData<T> = Transformations.distinctUntilChanged(this)

fun <VH : RecyclerView.ViewHolder> RecyclerView.init(
    layout: GridLayoutManager,
    vhAdapter: RecyclerView.Adapter<VH>
) {
    setHasFixedSize(false)
    layoutManager = layout
    adapter = vhAdapter
    addItemDecoration(SpacingDecorator(20, layout.spanCount))
}
