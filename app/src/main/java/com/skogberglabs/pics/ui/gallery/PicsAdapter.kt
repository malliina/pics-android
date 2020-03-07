package com.skogberglabs.pics.ui.gallery

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.skogberglabs.pics.R
import com.skogberglabs.pics.backend.PicMeta
import com.skogberglabs.pics.backend.PicService
import kotlinx.android.synthetic.main.image_item.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface PicDelegate {
    fun onPic(pic: PicMeta)
}

class PicsAdapter(
    initial: List<PicMeta>,
    context: Context,
    private val delegate: PicDelegate
) : BasicAdapter<PicMeta>(initial, R.layout.image_item) {
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val service = PicService(context)

    override fun onBindViewHolder(holder: PicHolder, position: Int) {
        val layout = holder.layout
        val pic = list[position]
        install(pic, layout.thumbnail_view)
        layout.setOnClickListener {
            delegate.onPic(pic)
        }
    }

    private fun install(pic: PicMeta, to: ImageView) {
        mainScope.launch {
            service.fetchBitmap(pic)?.let { bitmap ->
                to.setImageBitmap(bitmap)
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
