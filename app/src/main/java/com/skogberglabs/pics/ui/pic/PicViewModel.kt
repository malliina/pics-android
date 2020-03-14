package com.skogberglabs.pics.ui.pic

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.skogberglabs.pics.PicsApp
import com.skogberglabs.pics.backend.PicMeta
import com.skogberglabs.pics.backend.PicSize
import kotlinx.coroutines.launch

class PicViewModel(val app: Application): AndroidViewModel(app) {
    private val picData = MutableLiveData<Bitmap>()
    val pic: LiveData<Bitmap> = picData

    fun load(pic: PicMeta, size: PicSize) {
        viewModelScope.launch {
            (app as PicsApp).pics.fetchBitmap(pic, size)?.let { bitmap ->
                picData.postValue(bitmap)
            }
        }
    }
}
