package com.skogberglabs.pics.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.skogberglabs.pics.PicsApp
import com.skogberglabs.pics.R
import com.skogberglabs.pics.backend.PicMeta
import com.skogberglabs.pics.backend.PicSize
import kotlinx.android.synthetic.main.image_item.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface PicClickDelegate {
    fun onPicClicked(pic: PicMeta, position: Int)
}

class PicsAdapter(
    initial: List<PicMeta>,
    app: PicsApp,
    private val delegate: PicClickDelegate
) : BasicAdapter<PicMeta>(initial, R.layout.image_item) {
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val service = app.pics

    override fun onBindViewHolder(holder: PicHolder, position: Int) {
        val layout = holder.layout
        val pic = list[position]
        install(pic, layout.thumbnail_view)
        layout.setOnClickListener {
            delegate.onPicClicked(pic, position)
        }
    }

    private fun install(pic: PicMeta, to: ImageView) {
        mainScope.launch {
            service.fetchBitmap(pic, PicSize.Small)?.let { bitmapFile ->
                to.setImageBitmap(bitmapFile.bitmap)
                to.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
    }
}

abstract class BasicAdapter<T>(
    var list: List<T>,
    private val itemResource: Int
) : RecyclerView.Adapter<BasicAdapter.PicHolder>() {
    class PicHolder(val layout: ConstraintLayout) : RecyclerView.ViewHolder(layout)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PicHolder {
        val layout = LayoutInflater.from(parent.context).inflate(
            itemResource,
            parent,
            false
        ) as ConstraintLayout
        return PicHolder(layout)
    }

    override fun getItemCount(): Int = list.size
}
