package com.skogberglabs.pics.backend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
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

data class PicSource(val file: File, val rotate: Boolean)

class PicService(appContext: Context, private val ok: OkClient) {
    private val localDir = appContext.cacheDir.resolve("local")
    private val smallDir = appContext.cacheDir.resolve("small")
    private val largeDir = appContext.cacheDir.resolve("large")

    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val oneMonth = 2678400000L
    }

    init {
        backgroundScope.launch { maintenance() }
    }

    fun localCopy(file: File): File = file.copyTo(localDir.resolve(file.name))

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
        val source = fetch(pic, size)
        val file = source.file
        val initial = BitmapFactory.decodeFile(file.absolutePath)
        if (source.rotate) rotateIfNecessary(file, initial) else initial
    }

    private fun rotateIfNecessary(photoPath: File, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(photoPath)
        val orientation: Int = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
            ExifInterface.ORIENTATION_NORMAL -> bitmap
            else -> bitmap
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    private suspend fun fetch(pic: PicMeta, size: PicSize): PicSource {
        val dir = if (size == PicSize.Small) smallDir else largeDir
        val destination = dir.resolve(pic.key.key)
        val localDestination = localDir.resolve(pic.key.key)
        return when {
            destination.exists() -> {
                Timber.d("Found $destination locally.")
                PicSource(destination, false)
            }
            localDestination.exists() -> {
                Timber.d("Found $localDestination locally.")
                PicSource(localDestination, true)
            }
            else -> {
                val url = if (size == PicSize.Small) pic.small else pic.large
                val bytes = ok.download(url, destination)
                Timber.i("Downloaded ${bytes.bytes} bytes from $url to $destination.")
                PicSource(destination, false)
            }
        }
    }
}
