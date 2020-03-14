package com.skogberglabs.pics.backend

import android.graphics.Bitmap
import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize

@Parcelize
data class PicKey(val key: String) : Primitive, Parcelable {
    override val value: String get() = key
    override fun toString(): String = value

    companion object {
        val Key = "key"
    }
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
