package com.skogberglabs.pics.backend

import android.graphics.Bitmap
import android.os.Parcelable
import com.squareup.moshi.JsonAdapter
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
data class PicKeys(val keys: List<PicKey>) {
    companion object {
        val adapter = Json.moshi.adapter(PicKeys::class.java)
    }
}

@JsonClass(generateAdapter = true)
data class PicMeta(
    val key: PicKey,
    val added: Long,
    val url: FullUrl,
    val small: FullUrl,
    val medium: FullUrl,
    val large: FullUrl,
    val clientKey: String?
)

@JsonClass(generateAdapter = true)
data class Pics(val pics: List<PicMeta>) {
    companion object {
        val adapter: JsonAdapter<Pics> = Json.moshi.adapter(Pics::class.java)
    }
}
