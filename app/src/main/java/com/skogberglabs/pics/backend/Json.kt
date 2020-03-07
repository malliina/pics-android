package com.skogberglabs.pics.backend

import com.skogberglabs.pics.backend.Json.Companion.fail
import com.squareup.moshi.*

class Json {
    companion object {
        val instance = Json()
        val moshi: Moshi get() = instance.moshi

        fun fail(message: String): Nothing =
            throw JsonDataException(message)
    }

    val moshi: Moshi = Moshi.Builder()
        .add(PrimitiveAdapter())
        .build()
    val errorsAdapter: JsonAdapter<Errors> = moshi.adapter(Errors::class.java)
}

class PrimitiveAdapter {
    @FromJson
    fun email(s: String): Email = Email(s)

    @ToJson
    fun writeEmail(s: Email): String = s.email

    @FromJson
    fun picKey(s: String): PicKey = PicKey(s)

    @ToJson
    fun writePicKey(s: PicKey): String = s.key

    @FromJson
    fun id(s: String): IdToken = IdToken(s)

    @ToJson
    fun writeId(s: IdToken): String = s.token

    @FromJson
    fun url(url: String): FullUrl =
        FullUrl.build(url) ?: fail("Value '$url' cannot be converted to FullUrl")

    @ToJson
    fun writeUrl(url: FullUrl): String = url.url
}

fun <T> JsonAdapter<T>.read(json: String): T {
    return this.fromJson(json)
        ?: throw JsonDataException("Moshi returned null when reading '$json'.")
}

fun <T> JsonAdapter<T>.readUrl(json: String, url: FullUrl): T {
    return this.fromJson(json)
        ?: throw JsonDataException("Moshi returned null for response from '$url': '$json'.")
}
