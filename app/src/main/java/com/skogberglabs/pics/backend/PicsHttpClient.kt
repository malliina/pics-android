package com.skogberglabs.pics.backend

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType

class PicsHttpClient(val http: HttpClient) {
    companion object {
        private val picsVersion10 = "application/vnd.pics.v10+json".toMediaType()

        @Volatile
        private var instance: PicsHttpClient? = null

        fun get(context: Context): PicsHttpClient = instance ?: synchronized(this) {
            instance
                ?: PicsHttpClient(HttpClient(context)).also {
                    instance = it
                }
        }
    }

    fun updateToken(token: IdToken?) {
        http.token = token
    }

    suspend fun pics(limit: Int, offset: Int): Pics =
        http.getJson(
            FullUrl.https("pics.malliina.com", "/pics?limit=$limit&offset=$offset"),
            Pics.adapter
        )
}
