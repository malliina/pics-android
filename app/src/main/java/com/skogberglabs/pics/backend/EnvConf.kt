package com.skogberglabs.pics.backend

class EnvConf {
    companion object {
        const val Host = "pics.malliina.com"
        val BackendUrl = FullUrl.https(Host, "")
    }
}
