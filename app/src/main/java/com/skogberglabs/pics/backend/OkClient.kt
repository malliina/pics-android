package com.skogberglabs.pics.backend

import android.content.Context
import com.skogberglabs.pics.auth.Google
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class HttpException(message: String, val request: Request) : Exception(message) {
    val httpUrl = request.url
    val url = FullUrl.build(request.url.toString())
}

open class StatusException(val code: Int, request: Request) :
    HttpException("Invalid status code $code.", request)

class ErrorsException(val errors: Errors, code: Int, request: Request) :
    StatusException(code, request) {
    val isTokenExpired: Boolean get() = errors.errors.any { e -> e.key == "token_expired" }
}

class BodyException(request: Request) : HttpException("Invalid HTTP response body.", request)

interface TokenSource {
    suspend fun fetchToken(): IdToken?

    companion object {
        val empty = object : TokenSource {
            override suspend fun fetchToken(): IdToken? = null
        }
    }
}

class GoogleTokenSource(appContext: Context) : TokenSource {
    private val google = Google.instance.client(appContext)

    override suspend fun fetchToken(): IdToken? = try {
        Google.instance.signInSilently(google).idToken
    } catch (e: Exception) {
        Timber.w(e, "Failed to fetch token")
        null
    }
}

class OkClient(private val tokenSource: TokenSource) {
    companion object {
        val MediaTypeJson = "application/json".toMediaType()

        const val Accept = "Accept"
        const val Authorization = "Authorization"
    }

    private val client = OkHttpClient()

    suspend fun <T> getJson(
        url: FullUrl,
        headers: Map<String, String>,
        adapter: JsonAdapter<T>
    ): T = executeJson(newRequest(url, headers).build(), adapter)

    suspend fun delete(url: FullUrl, headers: Map<String, String>) = withContext(Dispatchers.IO) {
        val request = newRequest(url, headers).delete().build()
        executeNoContent(request)
    }

    suspend fun <Req, Res> postJson(
        url: FullUrl,
        body: Req,
        headers: Map<String, String>,
        writer: JsonAdapter<Req>,
        reader: JsonAdapter<Res>
    ): Res {
        val requestBody = writer.toJson(body).toRequestBody(MediaTypeJson)
        return executeJson(newRequest(url, headers).post(requestBody).build(), reader)
    }

    suspend fun postFile(file: File, to: FullUrl, headers: Map<String, String>): Response =
        withContext(Dispatchers.IO) {
            Timber.i("POSTing ${file.length()} bytes from '$file' to '$to'...")
            val builder = newRequest(to, headers).post(file.asRequestBody())
            execute(builder.build()) { response ->
                Timber.i("Uploaded '$file' to '$to'.")
                response
            }
        }

    suspend fun download(url: FullUrl, to: File): StorageSize? = withContext(Dispatchers.IO) {
        to.parentFile?.mkdirs()
        to.createNewFile()
        Timber.i("Downloading '$url' to '$to'...")
        val request = newRequest(url, emptyMap()).build()
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

    private fun newRequest(url: FullUrl, headers: Map<String, String>): Request.Builder {
        val builder = Request.Builder().url(url.url)
        for ((k, v) in headers) {
            builder.header(k, v)
        }
        return builder
    }

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

    private suspend fun <T> execute(request: Request, consume: (r: Response) -> T): T =
        try {
            executeOnce(request, consume)
        } catch (e: ErrorsException) {
            if (e.isTokenExpired) {
                val newToken = tokenSource.fetchToken()
                if (newToken != null) {
                    val newAttempt =
                        request.newBuilder().header(Authorization, "Bearer $newToken").build()
                    executeOnce(newAttempt, consume)
                } else {
                    Timber.w("Token expired and unable to renew token. Failing request to '${request.url}'.")
                    throw e
                }
            } else {
                throw e
            }
        }

    private suspend fun <T> executeOnce(request: Request, consume: (r: Response) -> T): T {
        val response = await(client.newCall(request))
        return response.use {
            if (response.isSuccessful) {
                consume(response)
            } else {
                val body = response.body
                if (body != null) {
                    Json.instance.errorsAdapter.fromJson(body.source())?.let { errors ->
                        throw ErrorsException(errors, response.code, request)
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
