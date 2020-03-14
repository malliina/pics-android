package com.skogberglabs.pics.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.skogberglabs.pics.backend.Outcome
import com.skogberglabs.pics.backend.PicMeta
import com.skogberglabs.pics.backend.PicsHttpClient
import com.skogberglabs.pics.backend.SingleError
import kotlinx.coroutines.launch
import timber.log.Timber

class GalleryViewModel(val app: Application) : AndroidViewModel(app) {
    private val data = MutableLiveData<Outcome<List<PicMeta>>>()
    val pics: LiveData<Outcome<List<PicMeta>>> = data
    val http: PicsHttpClient get() = PicsHttpClient.get(app.applicationContext)

    private val positionData = MutableLiveData<Int>()
    val position: LiveData<Int> = positionData

    fun updatePosition(pos: Int) {
        positionData.postValue(pos)
    }

    fun loadPics(limit: Int, offset: Int) {
        val until = offset + limit
        viewModelScope.launch {
            if (offset == 0) {
                data.value = Outcome.loading()
            }
            try {
                val items = http.pics(limit, offset).pics
                val newList =
                    if (offset == 0) items
                    else (data.value?.data ?: emptyList()) + items
                data.value = Outcome.success(newList)
                Timber.i("Loaded pics from $offset until $until, got ${items.size} items.")
            } catch (e: Exception) {
                val noVisiblePictures = limit == 0
                if (noVisiblePictures) {
                    data.value = Outcome.error(SingleError.backend("Error."))
                } else {
                    Timber.e(e, "Failed to load pics from $offset until $until.")
                }
            }
        }
    }
}
