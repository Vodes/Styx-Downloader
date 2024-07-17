package moe.styx.downloader.rss.sabnzb

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import moe.styx.common.http.httpClient
import moe.styx.downloader.downloaderConfig
import moe.styx.downloader.utils.Log

class SABnzbdClient(private var url: String, val apiKey: String) {

    init {
        url = url.removeSuffix("/")
        if (!url.contains("/sabnzbd")) {
            url = "$url/sabnzbd/api"
        } else if (!url.endsWith("api")) {
            url = "$url/api"
        }
    }

    fun authenticate(): Boolean = runBlocking {
        val response = httpClient.get(url) {
            url {
                parameters.append("apikey", apiKey)
                parameters.append("mode", "set_config")
                parameters.append("section", "categories")
                parameters.append("name", "styx")
                parameters.append("dir", downloaderConfig.rssConfig.defaultNonSeedDir)
            }
        }
        val result = response.status.isSuccess()
        if (!result)
            Log.e("SABnzbdClient for: $url") { "Failed to authenticate and set category!" }
        return@runBlocking result
    }

    fun addNZBByURL(nzbURL: String): Boolean = runBlocking {
        val response = httpClient.get(url) {
            url {
                parameters.append("apikey", apiKey)
                parameters.append("mode", "addurl")
                parameters.append("name", nzbURL)
                parameters.append("cat", "styx")
            }
        }
        val result = response.status.isSuccess()
        if (!result) {
            val body = response.bodyAsText()
            Log.e("SABnzbdClient for: $url") { "Failed to add NZB!\n\n${body}" }
        }
        return@runBlocking result
    }
}