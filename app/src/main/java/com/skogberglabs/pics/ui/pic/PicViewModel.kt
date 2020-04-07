package com.skogberglabs.pics.ui.pic

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.skogberglabs.pics.backend.*
import com.skogberglabs.pics.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PicBitmap(val pic: PicMeta, val bitmap: Bitmap, val file: File)

class PicViewModel(app: Application) : AppViewModel(app) {
    private val source: PicService get() = picsApp.pics
    private val picData = MutableLiveData<PicBitmap>()
    val pic: LiveData<PicBitmap> = picData

    fun load(pic: PicMeta, size: PicSize) {
        viewModelScope.launch {
            // Use a local image, if available, initially
            val local = source.fetchBitmapLocal(pic)
            local?.let { bitmap -> picData.postValue(PicBitmap(pic, bitmap.bitmap, bitmap.file)) }
            // If no local image is available, or only a small image is, then download a larger version
            val downloadBigger =
                local == null || local.takeIf { pic -> pic.size == PicSize.Small } != null
            if (downloadBigger) {
                source.fetchBitmap(pic, size)?.let { bitmap ->
                    picData.postValue(PicBitmap(pic, bitmap.bitmap, bitmap.file))
                }
            }
        }
    }

    suspend fun delete(pic: PicKey): Int = withContext(Dispatchers.IO) {
        picsApp.http.delete(pic)
    }
}
