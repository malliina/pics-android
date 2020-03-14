package com.skogberglabs.pics.backend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

enum class PicSize {
    Small,
    Large
}

class PicService(appContext: Context, private val ok: OkClient = OkClient.default) {
    private val smallDir = appContext.cacheDir.resolve("small")
    private val largeDir = appContext.cacheDir.resolve("large")

    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val oneMonth = 2678400000L
    }

    init {
        backgroundScope.launch { maintenance() }
    }

    private suspend fun maintenance() = withContext(Dispatchers.IO) {
        val larges = largeDir.listFiles() ?: emptyArray()
        val smalls = smallDir.listFiles() ?: emptyArray()
        // Deletes local images older than one month
        (larges + smalls).filter { f -> f.lastModified() + oneMonth < System.currentTimeMillis() }
            .forEach { file ->
                Timber.i("Deleting $file.")
                file.delete()
            }
    }

    suspend fun fetchBitmap(pic: PicMeta, size: PicSize): Bitmap? = withContext(Dispatchers.IO) {
        fetch(pic, size)?.let {
            BitmapFactory.decodeFile(it.absolutePath)
        }
    }

    private suspend fun fetch(pic: PicMeta, size: PicSize): File? {
        val dir = if (size == PicSize.Small) smallDir else largeDir
        val destination = dir.resolve(pic.key.key)
        return if (destination.exists()) {
            Timber.d("Found $destination locally.")
            destination
        } else {
            val url = if (size == PicSize.Small) pic.small else pic.large
            ok.download(url, destination)?.let { bytes ->
                Timber.i("Downloaded ${bytes.bytes} bytes from $url to $destination.")
                destination
            }
        }
    }
}
