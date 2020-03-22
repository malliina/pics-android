package com.skogberglabs.pics

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class PicFiles(private val appContext: Context) {
    fun uriForfile(file: File): Uri = FileProvider.getUriForFile(
        appContext,
        "com.skogberglabs.pics.fileprovider",
        file
    )
}
