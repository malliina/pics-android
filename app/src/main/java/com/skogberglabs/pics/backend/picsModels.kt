package com.skogberglabs.pics.backend

import android.graphics.Bitmap
import com.squareup.moshi.JsonClass

data class PicKey(val key: String): Primitive {
    override val value: String = key
    override fun toString(): String = key
}

@JsonClass(generateAdapter = true)
data class PicMeta(
    val key: PicKey,
    val added: Long,
    val url: FullUrl,
    val small: FullUrl,
    val medium: FullUrl,
    val large: FullUrl
)

@JsonClass(generateAdapter = true)
data class Pics(val pics: List<PicMeta>)

data class BitmapPic(val meta: PicMeta, val small: Bitmap)
