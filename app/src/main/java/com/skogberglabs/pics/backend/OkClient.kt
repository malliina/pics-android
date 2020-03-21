package com.skogberglabs.pics.backend

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class HttpException(message: String, val request: Request) : Exception(message) {
    val httpUrl = request.url
    val url = FullUrl.build(request.url.toString())
}

class StatusException(val code: Int, request: Request) :
    HttpException("Invalid status code $code.", request)

class BodyException(request: Request) : HttpException("Invalid HTTP response body.", request)

class OkClient {
    companion object {
        val default = OkClient()
        val MediaTypeJson = "application/json".toMediaType()
    }

    private val client = OkHttpClient()

    suspend fun <T> getJson(url: FullUrl, adapter: JsonAdapter<T>): T? =
        executeJson(newRequest(url).build(), adapter)

    suspend fun delete(url: FullUrl) = withContext(Dispatchers.IO) {
        val request = newRequest(url).delete().build()
        executeNoContent(request)
    }

    suspend fun <Req, Res> postJson(
        url: FullUrl,
        body: Req,
        writer: JsonAdapter<Req>,
        reader: JsonAdapter<Res>
    ): Res {
        val requestBody = writer.toJson(body).toRequestBody(MediaTypeJson)
        return executeJson(newRequest(url).post(requestBody).build(), reader)
    }

    suspend fun postFile(file: File, to: FullUrl, headers: Map<String, String>): Response =
        withContext(Dispatchers.IO) {
            Timber.i("POSTing ${file.length()} bytes from '$file' to '$to'...")
            val builder = newRequest(to).post(file.asRequestBody())
            for ((k, v) in headers) {
                builder.header(k, v)
            }
            val request = builder.build()
            execute(request) { response ->
                Timber.i("Uploaded '$file' to '$to'.")
                response
            }
        }

    suspend fun download(url: FullUrl, to: File): StorageSize? = withContext(Dispatchers.IO) {
        to.parentFile?.mkdirs()
        to.createNewFile()
        Timber.i("Downloading '$url' to '$to'...")
        val request = newRequest(url).build()
        execute(request) { response ->
            if (response.code == 200) {
                val responseBody = response.body
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
                Timber.w("Non-OK status code ${response.code} from '$url'.")
                null
            }
        }
    }

    private fun newRequest(url: FullUrl) = Request.Builder().url(url.url)

    private suspend fun executeNoContent(request: Request) = execute(request) { r -> r.code }

    private suspend fun <T> executeJson(request: Request, adapter: JsonAdapter<T>): T =
        execute(request) { response ->
            val body = response.body
            if (body != null) {
                adapter.fromJson(body.source())
                    ?: throw JsonDataException("Moshi returned null for response body from '${request.url}'.")
            } else {
                throw BodyException(request)
            }
        }

    private suspend fun <T> execute(request: Request, consume: (r: Response) -> T): T {
        val response = await(client.newCall(request))
        return response.use {
            if (response.isSuccessful) {
                consume(response)
            } else {
                val body = response.body
                if (body != null) {
                    val errors = Json.instance.errorsAdapter.fromJson(body.source())
                    val isTokenExpired =
                        errors?.let { err -> err.errors.any { e -> e.key == "token_expired" } }
                            ?: false
                    if (isTokenExpired) {

                    }
                }
                throw StatusException(response.code, request)
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
