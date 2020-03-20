package com.skogberglabs.pics.backend

import com.android.volley.NetworkResponse
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.squareup.moshi.JsonClass
import org.json.JSONException
import timber.log.Timber
import java.nio.charset.Charset
import java.util.regex.Pattern

interface Primitive {
    val value: String
}

data class Email(val email: String) : Primitive {
    override val value: String get() = email
    override fun toString(): String = email
}

data class IdToken(val token: String) : Primitive {
    override val value: String get() = token
    override fun toString(): String = token
}

data class UserInfo(val email: Email, val idToken: IdToken) {
//    companion object {
//        fun fromAws(token: Token): UserInfo =
//            UserInfo(Email(token.getClaim("email")), IdToken(token.tokenString))
//    }
}

data class StorageSize(val bytes: Long) {
    override fun toString(): String = "$bytes"
}

inline val Long.bytes: StorageSize
    get() = StorageSize(this)

data class FullUrl(val proto: String, val hostAndPort: String, val uri: String) {
    private val host = hostAndPort.takeWhile { c -> c != ':' }
    private val protoAndHost = "$proto://$hostAndPort"
    val url = "$protoAndHost$uri"

    fun append(more: String) = copy(uri = this.uri + more)

    override fun toString(): String = url

    companion object {
        private val pattern = Pattern.compile("(.+)://([^/]+)(/?.*)")

        fun https(domain: String, uri: String): FullUrl = FullUrl("https", dropHttps(domain), uri)
        fun http(domain: String, uri: String): FullUrl = FullUrl("http", dropHttps(domain), uri)
        fun host(domain: String): FullUrl = FullUrl("https", dropHttps(domain), "")
        fun ws(domain: String, uri: String): FullUrl = FullUrl("ws", domain, uri)
        fun wss(domain: String, uri: String): FullUrl = FullUrl("wss", domain, uri)

        fun parse(input: String): FullUrl {
            return build(input)
                ?: throw JSONException("Value $input cannot be converted to FullUrl")
        }

        fun build(input: String): FullUrl? {
            val m = pattern.matcher(input)
            return if (m.find() && m.groupCount() == 3) {
                m.group(1)?.let { proto ->
                    m.group(2)?.let { host -> m.group(3)?.let { uri -> FullUrl(proto, host, uri) } }
                }
            } else {
                null
            }
        }

        private fun dropHttps(domain: String): String {
            val prefix = "https://"
            return if (domain.startsWith(prefix)) domain.drop(prefix.length) else domain
        }
    }
}

@JsonClass(generateAdapter = true)
data class SingleError(val key: String, val message: String) {
    companion object {
        fun backend(message: String) = SingleError("backend", message)
    }
}

@JsonClass(generateAdapter = true)
data class Errors(val errors: List<SingleError>) {
    companion object {
        fun input(message: String) = single("input", message)
        fun single(key: String, message: String): Errors = Errors(listOf(SingleError(key, message)))
    }
}

data class ResponseException(val error: VolleyError, val req: RequestConf) :
    Exception("Invalid response", error.cause) {
    private val url = req.url
    private val response: NetworkResponse? = error.networkResponse

    fun errors(): Errors {
        return if (response != null) {
            val response = response
            try {
                val charset =
                    Charset.forName(HttpHeaderParser.parseCharset(response.headers, "UTF-8"))
                val str = String(response.data, charset)
                Json.instance.errorsAdapter.read(str)
            } catch (e: Exception) {
                val msg = "Unable to parse response from '$url'."
                Timber.e(e, msg)
                Errors.input(msg)
            }
        } else {
            Errors.single("network", "Network error from '$url'.")
        }
    }

    fun isTokenExpired(): Boolean = errors().errors.any { e -> e.key == "token_expired" }
}

enum class Status {
    Success,
    Error,
    Loading
}

data class Outcome<out T>(val status: Status, val data: T?, val error: SingleError?) {
    companion object {
        fun <T> success(t: T): Outcome<T> = Outcome(Status.Success, t, null)
        fun error(err: SingleError): Outcome<Nothing> = Outcome(Status.Error, null, err)
        fun loading(): Outcome<Nothing> = Outcome(Status.Loading, null, null)
    }

    // Not good due to Loading status
    fun <U> map(f: (t: T) -> U): Outcome<U> = when (status) {
        Status.Success -> Outcome.success(data?.let(f)!!)
        Status.Error -> error(error!!)
        Status.Loading -> loading()
    }
}
