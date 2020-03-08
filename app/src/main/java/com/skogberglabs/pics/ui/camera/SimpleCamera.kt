package com.skogberglabs.pics.ui.camera

import android.content.Context
import android.os.Environment
import timber.log.Timber
import java.io.File
import java.util.*

class SimpleCamera(appContext: Context) {
    companion object {
        private const val AllowedChars = "0123456789qwertyuiopasdfghjklzxcvbnm"
    }

    private val baseDir: File = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
    private val stagingDir = baseDir.resolve("staging")
    private val uploadingDir = baseDir.resolve("uploading")

    init {
        stagingDir.mkdirs()
        uploadingDir.mkdirs()
    }

    fun createImageFile(): File {
        val name = "${randomString(7)}.jpg"
        val picFile = stagingDir.resolve(name)
        val success = picFile.createNewFile()
        if (!success) {
            Timber.w("Failed to create file $picFile.")
        }
        return picFile
    }

    // https://stackoverflow.com/a/12116194
    private fun randomString(length: Int): String {
        val random = Random()
        val sb = StringBuilder(length)
        for (i in 0 until length)
            sb.append(AllowedChars[random.nextInt(AllowedChars.length)])
        return sb.toString()
    }
}
