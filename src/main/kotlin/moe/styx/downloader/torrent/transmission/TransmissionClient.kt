package moe.styx.downloader.torrent.transmission

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import moe.styx.common.extension.currentUnixSeconds
import moe.styx.common.http.httpClient
import moe.styx.downloader.torrent.Torrent
import moe.styx.downloader.torrent.TorrentClient
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.setGenericJsonBody

class TransmissionClient(private var url: String, user: String, pass: String) : TorrentClient(user, pass) {
    private var sessionID: String? = null
    private val sessionIDToken = "X-Transmission-Session-Id"
    private val json = Json(moe.styx.common.json) {
        encodeDefaults = true
    }

    init {
        url = url.removeSuffix("/")
        if (!url.contains("/transmission")) {
            url = "$url/transmission/rpc"
        } else if (!url.endsWith("rpc")) {
            url = "$url/rpc"
        }
    }

    override fun authenticate(): Boolean = runBlocking {
        try {
            var response = httpClient.post(url) {
                setGenericJsonBody(mapOf("method" to "session-get"))
                setTransmissionHeaders()
            }
            if (response.status !in arrayOf(
                    HttpStatusCode.OK,
                    HttpStatusCode.Unauthorized
                ) && response.headers.contains(sessionIDToken)
            ) {
                sessionID = response.headers[sessionIDToken]
                response = httpClient.post(url) {
                    setGenericJsonBody(mapOf("method" to "session-get"))
                    setTransmissionHeaders()
                }
            }
            return@runBlocking response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Log.e("TransmissionClient for: $url", ex) { "Failed to authenticate!" }
        }
        return@runBlocking false
    }

    override fun listTorrents(): List<Torrent> = runBlocking {
        val torrents = mutableListOf<Torrent>()
        val response = attemptRequest {
            httpClient.post(url) {
                setBody(json.encodeToString(Transmission.ListRequest()))
                contentType(ContentType.Application.Json)
                setTransmissionHeaders()
            }
        }
        if (response.status.isSuccess()) {
            val body: JsonObject = json.decodeFromString(response.bodyAsText())
            torrents.addAll(
                body["arguments"]?.jsonObject?.get("torrents")?.jsonArray?.map { element ->
                    val torrentObject = element.jsonObject
                    Log.i { torrentObject.toString() }
                    val isFinished =
                        (torrentObject["percentDone"]?.jsonPrimitive?.double ?: 0.0) >= 1.0 &&
                                torrentObject["error"]?.jsonPrimitive?.intOrNull == 0 &&
                                torrentObject["status"]?.jsonPrimitive?.intOrNull in arrayOf(0, 6)
                    Torrent(
                        torrentObject["name"]?.jsonPrimitive?.content!!,
                        torrentObject["hashString"]?.jsonPrimitive?.content!!,
                        torrentObject["addedDate"]?.jsonPrimitive?.long!!,
                        torrentObject["labels"]!!.jsonArray.map { it.jsonPrimitive.content },
                        if (isFinished) listOf("complete") else emptyList()
                    )
                } ?: emptyList()
            )
        }
        return@runBlocking torrents.sortedByDescending { it.added }.toList()
    }

    override fun addTorrentByURL(torrentURL: String, destinationDir: String, start: Boolean): Torrent? = runBlocking {
        var responseText = ""
        try {
            val requestBody = json.encodeToString(
                Transmission.AddRequest(
                    torrent = Transmission.AddRequestTorrent(
                        torrentURL, destinationDir, !start
                    )
                )
            )
            val response = attemptRequest {
                httpClient.post(url) {
                    setBody(requestBody)
                    contentType(ContentType.Application.Json)
                    setTransmissionHeaders()
                }
            }
            responseText = response.bodyAsText()
            if (response.status.isSuccess()) {
                val body: JsonObject = json.decodeFromString(responseText)
                var torrentJson = body["arguments"]?.jsonObject
                if (torrentJson != null) {
                    torrentJson = if (torrentJson.contains("torrent-duplicate")) {
                        torrentJson["torrent-duplicate"]!!.jsonObject
                    } else {
                        torrentJson["torrent-added"]!!.jsonObject
                    }
                    return@runBlocking Torrent(
                        torrentJson["name"]!!.jsonPrimitive.content,
                        torrentJson["hashString"]!!.jsonPrimitive.content,
                        currentUnixSeconds()
                    )
                }
            } else
                throw Exception("Torrent add request was not successful.")
        } catch (ex: Exception) {
            Log.e("TransmissionClient for: $url", ex) { "Could not add torrent! Response: \n$responseText" }
            ex.printStackTrace()
        }
        return@runBlocking null
    }

    override fun deleteTorrent(hash: String, deleteFiles: Boolean): Boolean = runBlocking {
        try {
            val requestBody = json.encodeToString(
                Transmission.RemoveRequest(arguments = Transmission.RemoveRequestArgs(listOf(hash), deleteFiles))
            )
            val response = attemptRequest {
                httpClient.post(url) {
                    setBody(requestBody)
                    contentType(ContentType.Application.Json)
                    setTransmissionHeaders()
                }
            }
            if (response.status.isSuccess()) {
                val body: JsonObject = json.decodeFromString(response.bodyAsText())
                return@runBlocking body.contains("result") && body["result"]!!.jsonPrimitive.content.equals("success", true)
            }
        } catch (ex: Exception) {
            Log.e("TransmissionClient for: $url", ex) { "Could not delete torrent!" }
        }
        return@runBlocking false
    }

    fun stopTorrent(hash: String): Boolean = runBlocking {
        try {
            val requestBody = json.encodeToString(
                Transmission.StopRequest(arguments = Transmission.StopRequestArgs(listOf(hash)))
            )
            val response = attemptRequest {
                httpClient.post(url) {
                    setBody(requestBody)
                    contentType(ContentType.Application.Json)
                    setTransmissionHeaders()
                }
            }
            if (response.status.isSuccess()) {
                val body: JsonObject = json.decodeFromString(response.bodyAsText())
                return@runBlocking body.contains("result") && body["result"]!!.jsonPrimitive.content.equals("success", true)
            }
        } catch (ex: Exception) {
            Log.e("TransmissionClient for: $url", ex) { "Could not delete torrent!" }
        }
        return@runBlocking false
    }

    private fun HttpRequestBuilder.setTransmissionHeaders() {
        basicAuth(user, pass)
        if (!sessionID.isNullOrBlank()) {
            headers {
                append(sessionIDToken, sessionID!!)
            }
        }
    }
}
