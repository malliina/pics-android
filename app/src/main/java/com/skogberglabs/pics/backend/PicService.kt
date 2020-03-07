package com.skogberglabs.pics.backend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class PicService(appContext: Context, private val ok: OkClient = OkClient.default) {
    private val smallDir = appContext.cacheDir.resolve("small")

    suspend fun fetchBitmap(pic: PicMeta): Bitmap? = withContext(Dispatchers.IO) {
        fetch(pic)?.let {
            BitmapFactory.decodeFile(it.absolutePath)
        }
    }

    private suspend fun fetch(pic: PicMeta): File? {
        val destination = smallDir.resolve(pic.key.key)
        return if (destination.exists()) {
            destination
        } else {
            ok.download(pic.small, destination)?.let { size ->
                Timber.i("Downloaded ${pic.small} ${size.bytes} bytes to $destination.")
                destination
            }
        }
    }
}
