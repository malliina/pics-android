package com.skogberglabs.pics.ui.camera

import android.content.Context
import android.os.Environment
import com.skogberglabs.pics.backend.Email
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.*

class SimpleCamera(appContext: Context) {
    companion object {
        private const val AllowedChars = "0123456789qwertyuiopasdfghjklzxcvbnm"
        private val hexChars = "0123456789ABCDEF".toCharArray()
    }

    private val baseDir: File = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
    private val localDir = baseDir.resolve("local")
    private val stagingDir = baseDir.resolve("staging")
    private val uploadingDir = baseDir.resolve("uploading")

    init {
        localDir.mkdirs()
        stagingDir.mkdirs()
        uploadingDir.mkdirs()
    }

    fun createImageFile(user: Email?): File? {
        val name = "${randomString(7)}.jpg"
        val dir = stagingDir.resolve(userFolder(user))
        dir.mkdirs()
        val picFile = dir.resolve(name)
        val success = picFile.createNewFile()
        if (!success) {
            Timber.w("Failed to create file $picFile.")
            return null
        }
        return picFile
    }

    fun stagingDirectory(user: Email?) = stagingDir.resolve(userFolder(user))
    fun uploadingDirectory(user: Email?) = uploadingDir.resolve(userFolder(user))

    private fun userFolder(user: Email?) = user?.let { toSha1Hex(it.value).take(8) } ?: "anonymous"

    // https://stackoverflow.com/a/12116194
    private fun randomString(length: Int): String {
        val random = Random()
        val sb = StringBuilder(length)
        for (i in 0 until length)
            sb.append(AllowedChars[random.nextInt(AllowedChars.length)])
        return sb.toString()
    }

    // https://www.javacodemonk.com/md5-and-sha256-in-java-kotlin-and-android-96ed9628
    private fun toSha1Hex(str: String): String {
        val data = MessageDigest
            .getInstance("SHA-1")
            .digest(str.toByteArray(Charsets.UTF_8))
        val r = StringBuilder(data.size * 2)
        data.forEach { b ->
            val i = b.toInt()
            r.append(hexChars[i shr 4 and 0xF])
            r.append(hexChars[i and 0xF])
        }
        return r.toString().toLowerCase(Locale.ROOT)
    }
}
