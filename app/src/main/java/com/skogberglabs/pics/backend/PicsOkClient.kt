package com.skogberglabs.pics.backend

import com.skogberglabs.pics.UserSettings
import com.skogberglabs.pics.backend.OkClient.Companion.Accept
import com.skogberglabs.pics.backend.OkClient.Companion.Authorization

class PicsOkClient(val http: OkClient, val cache: UserSettings) {
    companion object {
        const val CsrfHeaderName = "Csrf-Token"
        const val CsrfTokenNoCheck = "nocheck"
        const val PicsVersion10 = "application/vnd.pics.v10+json"

        val defaultHeaders = mapOf(
            Accept to PicsVersion10,
            CsrfHeaderName to CsrfTokenNoCheck
        )
    }

    var token: IdToken? = null

    suspend fun delete(pic: PicKey): Int = http.delete(urlTo("/pics/$pic"), requestHeaders())

    fun picsCached(limit: Int, offset: Int, user: Email?): Pics? {
        val url = picsUrl(limit, offset)
        return cache.loadPics(user, url)
    }

    suspend fun pics(limit: Int, offset: Int) = pics(picsUrl(limit, offset))


    private suspend fun pics(url: FullUrl): Pics =
        http.getJson(url, requestHeaders(), Pics.adapter)

    private fun picsUrl(limit: Int, offset: Int) = urlTo("/pics?limit=$limit&offset=$offset")

    private fun urlTo(path: String) = EnvConf.BackendUrl.append(path)

    private fun requestHeaders(more: Map<String, String> = emptyMap()) =
        defaultHeaders + more + (token?.let { mapOf(Authorization to "Bearer $it") } ?: emptyMap())
}
