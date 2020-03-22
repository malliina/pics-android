package com.skogberglabs.pics.backend

import com.skogberglabs.pics.backend.OkClient.Companion.Accept
import com.skogberglabs.pics.backend.OkClient.Companion.Authorization

class PicsOkClient(val http: OkClient) {
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

    suspend fun pics(limit: Int, offset: Int): Pics =
        http.getJson(
            urlTo("/pics?limit=$limit&offset=$offset"),
            requestHeaders(),
            Pics.adapter
        )

    private fun urlTo(path: String) = EnvConf.BackendUrl.append(path)

    private fun requestHeaders(more: Map<String, String> = emptyMap()) =
        defaultHeaders + more + (token?.let { mapOf(Authorization to "Bearer $it") } ?: emptyMap())
}
