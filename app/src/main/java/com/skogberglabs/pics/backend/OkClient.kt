package com.skogberglabs.pics.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkClient {
    companion object {
        val default = OkClient()
    }

    private val client = OkHttpClient()

    suspend fun download(url: FullUrl, to: File): StorageSize? = withContext(Dispatchers.IO) {
        to.parentFile?.mkdirs()
        to.createNewFile()
        Timber.i("Downloading '$url' to '$to'...")
        val request = Request.Builder()
            .url(url.url)
            .build()
        val response = await(client.newCall(request))
        response.use { res ->
            if (res.code == 200) {
                val responseBody = res.body
                if (responseBody == null) {
                    Timber.w("Got no response body from '$url'.")
                }
                responseBody?.let { body ->
                    val sink = to.sink().buffer()
                    val size = sink.use { s ->
                        StorageSize(s.writeAll(body.source()))
                    }
                    Timber.i("Downloaded $size bytes from '$url' to '$to'.")
                    size
                }
            } else {
                Timber.w("Non-OK status code ${res.code} from '$url'.")
                null
            }
        }
    }

    private suspend fun await(call: Call) = suspendCancellableCoroutine<Response> { cont ->
        val url = call.request().url.toUrl().toString()
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Call to '$url' failed.")
                cont.resumeWithException(e)
            }
        })
    }
}
