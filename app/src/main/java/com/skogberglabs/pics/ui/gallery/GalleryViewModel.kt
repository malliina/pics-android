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
    private val existing: List<PicMeta> get() = data.value?.data?.pics ?: emptyList()
    val pics: LiveData<Outcome<PicsList>> = data

    private val socket: PicsSocket = PicsSocket.build(GalleryPicsDelegate())

    private var initial: Boolean = true
    private var latestUser: UserInfo? = null

    fun updateUser(user: UserInfo?) {
        val isChanged = latestUser?.email != user?.email
        Timber.i("Was ${latestUser?.email}, is ${user?.email}, changed $isChanged, initial $initial")
        latestUser = user
        picsApp.http.token = user?.idToken
        if (isChanged || initial) {
            loadPics(100, 0)
        }
        initial = false
    }

    fun loadPics(limit: Int, offset: Int) {
        val until = offset + limit
        val current = existing
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (offset == 0) {
                    data.postValue(Outcome.loading())
                }
                try {
                    val items = picsApp.http.pics(limit, offset).pics
                    val newList: List<PicMeta> =
                        if (offset == 0) items
                        else current + items
                    val list = PicsList(newList, 0, if (offset == 0) 0 else items.size, emptyList())
                    data.postValue(Outcome.success(list))
                    Timber.i("Loaded pics from $offset until $until, got ${items.size} items.")
                } catch (e: Exception) {
                    val noVisiblePictures = limit == 0
                    if (noVisiblePictures) {
                        data.postValue(Outcome.error(SingleError.backend("Error.")))
                    } else {
                        Timber.e(e, "Failed to load pics from $offset until $until.")
                    }
                }
            }
        }
    }

    fun onPicTaken(operation: PicOperation) {
        val current = existing
        val email = operation.email
        val describeUser = email ?: "anon"
        Timber.i("Processing ${operation.file} by $describeUser...")
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
                        PicMeta(
                            key,
                            System.currentTimeMillis() / 1000,
                            url,
                            url,
                            url,
                            url,
                            key.value
                        )
                    val list = PicsList(listOf(local) + current, 1, 0, emptyList(), false)
                    data.postValue(Outcome.success(list))
                    Timber.i("Got photo at $file of size ${file.length()} bytes. Uploading...")
                    UploadService.enqueue(picsApp.applicationContext, operation.user)
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
            val current = existing
            // Removes any existing keys with matching clientKeys, then prepends the provided pics
            val base =
                current.filterNot { p -> pics.any { pic -> pic.clientKey == p.clientKey } }
            // If the provided pics already existed, performs a background update only
            val newCount = pics.size - (current.size - base.size)
            val onlyExistingUpdated = newCount == 0
            val update = PicsList(pics + base, newCount, 0, emptyList(), onlyExistingUpdated)
            data.postValue(Outcome.success(update))
        }

        override fun onPicsRemoved(keys: List<PicKey>) {
            val old = existing
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
