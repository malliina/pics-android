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
    val insertedIndices: List<Int>,
    val removedIndices: List<Int>,
    val backgroundUpdate: Boolean = false
)

class GalleryViewModel(app: Application) : AppViewModel(app) {
    private val data = MutableLiveData<Outcome<PicsList>>()
    private val existing: List<PicMeta> get() = data.value?.data?.pics ?: emptyList()
    val pics: LiveData<Outcome<PicsList>> = data
    val http: PicsOkClient get() = picsApp.http

    private val socket: PicsSocket = PicsSocket.build(GalleryPicsDelegate())

    private var initial: Boolean = true
    private var latestUser: UserInfo? = null

    fun updateUser(user: UserInfo?) {
        val email = user?.email
        val isChanged = latestUser?.email != email
        Timber.i("Was ${latestUser?.email}, is $email, changed $isChanged, initial $initial")
        latestUser = user
        picsApp.http.token = user?.idToken
        if (isChanged || initial) {
            loadPics(100, 0, initial, email)
        }
        initial = false
    }

    fun loadPics(limit: Int, offset: Int) = loadPics(limit, offset, false, latestUser?.email)

    private fun loadPics(limit: Int, offset: Int, isInitial: Boolean, user: Email?) {
        val until = offset + limit
        val current = existing
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (offset == 0) {
                    data.postValue(Outcome.loading())
                }
                val cachedList = if (isInitial) {
                    val cache = http.picsCached(limit, offset, user)
                    if (cache != null) {
                        Timber.i("Got ${cache.pics.size} pics from cache.")
                        val list = PicsList(
                            cache.pics,
                            0,
                            if (offset == 0) 0 else cache.pics.size,
                            emptyList(),
                            emptyList()
                        )
                        data.postValue(Outcome.success(list))
                        list
                    } else {
                        null
                    }
                } else {
                    null
                }
                try {
                    val items = http.pics(limit, offset).pics
                    val newList: List<PicMeta> = if (offset == 0) items else current + items
                    if (cachedList == null) {
                        val list = PicsList(
                            newList,
                            0,
                            if (offset == 0) 0 else items.size,
                            emptyList(),
                            emptyList()
                        )
                        data.postValue(Outcome.success(list))
                    } else {
                        merge(cachedList.pics, newList)
                    }
                    http.savePics(limit, offset, user, Pics(newList))
                    Timber.i("Loaded ${items.size} items from $offset until $until")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load pics from $offset until $until.")
                    val noVisiblePictures = current.isEmpty() && (cachedList?.pics ?: emptyList()).isEmpty()
                    if (noVisiblePictures) {
                        data.postValue(Outcome.error(SingleError.backend("Error.")))
                    }
                }
            }
        }
    }

    private fun merge(old: List<PicMeta>, newer: List<PicMeta>) {
        // Pics in new list but not in old
        val inserts = newer.foldIndexed(emptyList<Int>()) { index, acc, meta ->
            if (old.any { oldMeta -> oldMeta.key == meta.key }) acc
            else acc + listOf(index)
        }
        // Pics in old but not in new
        val removes = old.foldIndexed(emptyList<Int>()) { index, acc, meta ->
            if (newer.any { newMeta -> newMeta.key == meta.key }) acc
            else acc + listOf(index)
        }
        if (inserts.isNotEmpty() || removes.isNotEmpty()) {
            Timber.i("Got ${inserts.size} inserts and ${removes.size} removals, updating.")
            data.postValue(Outcome.success(PicsList(newer, 0, 0, inserts, removes)))
        } else {
            Timber.i("Cache is in sync with remote, no updates.")
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
                    // Uses a local copy which is not dependent on staging/upload processes
                    val localCopy = picsApp.pics.localCopy(operation.file)
                    Timber.i("Copied ${operation.file} to $localCopy bytes. Size ${localCopy.length()} bytes.")
                    val key = PicKey(localCopy.name)
                    val file = operation.file
                    val url = picsApp.files.urlForFile(localCopy)
                    val local = PicMeta(
                        key,
                        System.currentTimeMillis() / 1000,
                        url,
                        url,
                        url,
                        url,
                        key.value
                    )
                    val list = PicsList(listOf(local) + current, 1, 0, emptyList(), emptyList(), false)
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
        socket.open(http.token)
    }

    fun disconnect() {
        socket.close()
    }

    inner class GalleryPicsDelegate : PicsSocketDelegate {
        override fun onOpened(url: HttpUrl) {
            Timber.i("Opened '$url'.")
        }

        override fun onPicsAdded(pics: List<PicMeta>) {
            val current = existing
            // Deduplication: Removes any existing keys with matching clientKeys, then prepends the provided pics
            val base =
                current.filterNot { p -> pics.any { pic -> pic.clientKey == p.clientKey } }
            // If the provided pics already existed, performs a background update only
            val newCount = pics.size - (current.size - base.size)
            val onlyExistingUpdated = newCount == 0
            val update = PicsList(pics + base, newCount, 0, emptyList(), emptyList(), onlyExistingUpdated)
            data.postValue(Outcome.success(update))
        }

        override fun onPicsRemoved(keys: List<PicKey>) {
            val old = existing
            val removedIndices =
                old.withIndex().filter { p -> keys.contains(p.value.key) }.map { it.index }
            val remaining = old.filterIndexed { index, _ -> !removedIndices.contains(index) }
            data.postValue(Outcome.success(PicsList(remaining, 0, 0, emptyList(), removedIndices)))
        }

        override fun onClosed(url: HttpUrl) {
            Timber.i("Closed to '$url'.")
        }

        override fun onFailure(t: Throwable, url: HttpUrl) {
            Timber.i(t, "Failed to '$url'.")
        }
    }
}
