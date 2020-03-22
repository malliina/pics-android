package com.skogberglabs.pics.ui.pic

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.skogberglabs.pics.backend.PicKey
import com.skogberglabs.pics.backend.PicMeta
import com.skogberglabs.pics.backend.PicSize
import com.skogberglabs.pics.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PicBitmap(val pic: PicMeta, val bitmap: Bitmap)

class PicViewModel(app: Application): AppViewModel(app) {
    private val picData = MutableLiveData<PicBitmap>()
    val pic: LiveData<PicBitmap> = picData

    fun load(pic: PicMeta, size: PicSize) {
        viewModelScope.launch {
            picsApp.pics.fetchBitmap(pic, size)?.let { bitmap ->
                picData.postValue(PicBitmap(pic, bitmap))
            }
        }
    }

    suspend fun delete(pic: PicKey): Int = withContext(Dispatchers.IO) {
        picsApp.http.delete(pic)
    }
}
