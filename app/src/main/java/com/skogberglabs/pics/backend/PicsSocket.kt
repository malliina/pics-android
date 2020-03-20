package com.skogberglabs.pics.backend

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import okhttp3.*
import okio.ByteString
import timber.log.Timber

@JsonClass(generateAdapter = true)
data class ServerEvent(val event: String) {
    companion object {
        val added = ServerEvent("added")
        val removed = ServerEvent("removed")
        val ping = ServerEvent("ping")
        val welcome = ServerEvent("welcome")
        val adapter: JsonAdapter<ServerEvent> = Json.moshi.adapter(ServerEvent::class.java)
    }
}

interface PicsSocketDelegate {
    fun onOpened(url: HttpUrl)
    fun onPicsAdded(pics: List<PicMeta>)
    fun onPicsRemoved(keys: List<PicKey>)
    fun onClosed(url: HttpUrl)
    fun onFailure(t: Throwable, url: HttpUrl)
}

class PicsSocket(private val url: FullUrl, val delegate: PicsSocketDelegate) {
    companion object {
        fun build(delegate: PicsSocketDelegate): PicsSocket =
            PicsSocket(FullUrl("wss", "pics.malliina.com", "/sockets"), delegate)
    }

    val http = OkHttpClient()
    private var socket: WebSocket? = null

    fun open(token: IdToken?) {
        close()
        socket = null
        val builder = Request.Builder().url(url.url)
            .header(HttpClient.Accept, HttpClient.Json)
        token?.let { jwt ->
            builder.header(HttpClient.Authorization, "Bearer $jwt")
        }
        val req = builder.build()
        Timber.i("Connecting to ${url}...")
        socket = http.newWebSocket(req, object : WebSocketListener() {
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                delegate.onClosed(webSocket.request().url)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                delegate.onFailure(t, webSocket.request().url)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                ServerEvent.adapter.fromJson(text)?.let { event ->
                    when (event) {
                        ServerEvent.added -> {
                            Pics.adapter.fromJson(text)?.let {
                                delegate.onPicsAdded(it.pics)
                            }
                        }
                        ServerEvent.removed -> {
                            PicKeys.adapter.fromJson(text)?.let {
                                delegate.onPicsRemoved(it.keys)
                            }
                        }
                        ServerEvent.ping -> { }
                        ServerEvent.welcome -> { }
                        else -> {
                            Timber.d("Unknown event: '$text'.")
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.string(Charsets.UTF_8))
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                delegate.onOpened(webSocket.request().url)
            }
        })
    }

    fun send(message: String) = socket?.send(message)

    fun close() {
        socket?.close(1000, "User.")
        socket = null
    }
}
