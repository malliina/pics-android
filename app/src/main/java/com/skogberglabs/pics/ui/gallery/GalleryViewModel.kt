package com.skogberglabs.pics.ui.gallery

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.skogberglabs.pics.backend.*
import com.skogberglabs.pics.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import timber.log.Timber

data class PicsList(
    val pics: List<PicMeta>,
    val prependedCount: Int,
    val appendedCount: Int,
    val removedIndices: List<Int>,
    val backgroundUpdate: Boolean = false
)

class GalleryViewModel(app: Application) : AppViewModel(app) {
    private val data = MutableLiveData<Outcome<PicsList>>()
    val pics: LiveData<Outcome<PicsList>> = data

    private val socket: PicsSocket = PicsSocket.build(GalleryPicsDelegate())

    fun loadPics(limit: Int, offset: Int) {
        val until = offset + limit
        viewModelScope.launch {
            if (offset == 0) {
                data.value = Outcome.loading()
            }
            try {
                val items = picsApp.http.pics(limit, offset).pics
                val newList =
                    if (offset == 0) items
                    else (data.value?.data?.pics ?: emptyList()) + items
                data.value = Outcome.success(PicsList(newList, 0, items.size, emptyList()))
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

    fun onPicTaken(operation: PicOperation, user: UserInfo?) {
        val existing = data.value?.data?.pics ?: emptyList()
        Timber.i("Processing ${operation.file}...")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val localCopy = picsApp.pics.localCopy(operation.file)
                    Timber.i("Copied ${operation.file} to $localCopy bytes. Size ${localCopy.length()} bytes.")
                    val key = PicKey(localCopy.name)
                    val file = operation.file
                    val uri = picsApp.files.uriForfile(localCopy)
                    val url = FullUrl.build(uri.toString())!!
                    val local =
                        PicMeta(key, System.currentTimeMillis() / 1000, url, url, url, url, key.value)
                    val list = PicsList(listOf(local) + existing, 1, 0, emptyList(), false)
                    data.postValue(Outcome.success(list))
                    if ((user?.email == null && operation.email == null) || (user?.email == operation.email)) {
                        Timber.i("Got photo at $file of size ${file.length()} bytes. Uploading...")
                        UploadService.enqueue(picsApp.applicationContext, user)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to process image.")
                }
            }
        }
    }

    fun reconnect() {
        socket.open(picsApp.http.token)
    }

    fun disconnect() {
        socket.close()
    }

    inner class GalleryPicsDelegate : PicsSocketDelegate {
        override fun onOpened(url: HttpUrl) {
            Timber.i("Opened to '$url'.")
        }

        override fun onPicsAdded(pics: List<PicMeta>) {
            val existing = data.value?.data?.pics ?: emptyList()
            // Removes any existing keys with matching clientKeys, then prepends the provided pics
            val base =
                existing.filterNot { p -> pics.any { pic -> pic.clientKey == p.clientKey } }
            // If the provided pics already existed, performs a background update only
            val newCount = pics.size - (existing.size - base.size)
            val onlyExistingUpdated = newCount == 0
            val update = PicsList(pics + base, newCount, 0, emptyList(), onlyExistingUpdated)
            data.postValue(Outcome.success(update))
        }

        override fun onPicsRemoved(keys: List<PicKey>) {
            val old = data.value?.data?.pics ?: emptyList()
            val indices =
                old.withIndex().filter { p -> keys.contains(p.value.key) }.map { it.index }
            val remaining = old.filterIndexed { index, _ -> !indices.contains(index) }
            data.postValue(Outcome.success(PicsList(remaining, 0, 0, indices)))
        }

        override fun onClosed(url: HttpUrl) {
            Timber.i("Closed to '$url'.")
        }

        override fun onFailure(t: Throwable, url: HttpUrl) {
            Timber.i(t, "Failed to '$url'.")
        }
    }
}
