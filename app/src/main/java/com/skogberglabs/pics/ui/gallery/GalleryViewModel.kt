package com.skogberglabs.pics.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.skogberglabs.pics.backend.*
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import timber.log.Timber

data class PicsList(val pics: List<PicMeta>, val prependedCount: Int, val removedIndices: List<Int>)

class GalleryViewModel(val app: Application) : AndroidViewModel(app) {
    private val data = MutableLiveData<Outcome<PicsList>>()
    val pics: LiveData<Outcome<PicsList>> = data
    val http: PicsHttpClient get() = PicsHttpClient.get(app.applicationContext)

    private val socket: PicsSocket = PicsSocket.build(GalleryPicsDelegate())

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
                    else (data.value?.data?.pics ?: emptyList()) + items
                data.value = Outcome.success(PicsList(newList, 0, emptyList()))
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

    fun reconnect() {
        socket.open(http.http.token)
    }

    fun disconnect() {
        socket.close()
    }

    inner class GalleryPicsDelegate : PicsSocketDelegate {
        override fun onOpened(url: HttpUrl) {
            Timber.i("Opened to '$url'.")
        }

        override fun onPicsAdded(pics: List<PicMeta>) {
            val update =
                PicsList(pics + (data.value?.data?.pics ?: emptyList()), pics.size, emptyList())
            data.postValue(Outcome.success(update))
        }

        override fun onPicsRemoved(keys: List<PicKey>) {
            val old = data.value?.data?.pics ?: emptyList()
            val indices =
                old.withIndex().filter { p -> keys.contains(p.value.key) }.map { it.index }
            val remaining = old.filterIndexed { index, _ -> !indices.contains(index) }
            data.postValue(Outcome.success(PicsList(remaining, 0, indices)))
        }

        override fun onClosed(url: HttpUrl) {
            Timber.i("Closed to '$url'.")
        }

        override fun onFailure(t: Throwable, url: HttpUrl) {
            Timber.i(t, "Failed to '$url'.")
        }
    }
}
