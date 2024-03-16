package moe.styx.downloader.torrent.flood

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import moe.styx.common.http.httpClient
import moe.styx.common.json
import moe.styx.downloader.torrent.Torrent
import moe.styx.downloader.torrent.TorrentClient
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.setGenericJsonBody
import kotlin.random.Random

class FloodClient(private var url: String, user: String, pass: String) : TorrentClient(user, pass) {

    init {
        url = url.removeSuffix("/")
    }

    override fun authenticate(): Boolean = runBlocking {
        try {
            val response = httpClient.post("$url${FloodEndpoint.AUTH}") {
                setGenericJsonBody(mapOf("username" to user, "password" to pass))
            }
            return@runBlocking response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Log.e("FloodClient for $url", ex) { "Failed to send the auth request!" }
        }
        return@runBlocking false
    }

    override fun listTorrents(): List<Torrent> = runBlocking {
        val torrents = mutableListOf<Torrent>()

        val response = attemptRequest {
            httpClient.get("$url${FloodEndpoint.TORRENT_LIST}")
        }

        if (response.status == HttpStatusCode.OK) {
            val body: JsonObject = json.decodeFromString(response.bodyAsText())
            val torrentList = body["torrents"]!!
            torrents.addAll(
                torrentList.jsonObject.map { entry ->
                    val values = entry.value.jsonObject
                    Torrent(
                        values["name"]?.jsonPrimitive?.content!!,
                        entry.key.removeSuffix("\"").removePrefix("\""),
                        values["dateAdded"]?.jsonPrimitive?.content!!.toLong(),
                        values["tags"]!!.jsonArray.map { it.jsonPrimitive.content },
                        values["status"]!!.jsonArray.map { it.jsonPrimitive.content })
                }
            )
        }
        return@runBlocking torrents.sortedByDescending { it.added }.toList()
    }

    override fun addTorrentByURL(torrentURL: String, destinationDir: String, start: Boolean): Torrent? = runBlocking {
        try {
            val tag = "styx-${Random.nextInt(100000, 999999)}"
            val response = attemptRequest {
                httpClient.post("$url${FloodEndpoint.TORRENT_ADD_URL}") {
                    setGenericJsonBody {
                        put("urls", buildJsonArray {
                            add(torrentURL)
                        })
                        put("tags", buildJsonArray {
                            add("styx")
                            add(tag)
                        })
                        put("destination", destinationDir)
                        put("start", start)
                    }
                }
            }
            if (response.status.value in 200..203) {
                delay(1800)
                return@runBlocking listTorrents().find { it.hasTag(tag) }.apply {
                    if (this != null)
                        setTags(this.hash, "styx")
                }

            }
            return@runBlocking null
        } catch (ex: Exception) {
            Log.e("FloodClient for $url", ex) { "Failed to add torrent!" }
        }
        return@runBlocking null
    }

    override fun deleteTorrent(hash: String, deleteFiles: Boolean): Boolean = runBlocking {
        try {
            val response = attemptRequest {
                httpClient.post("$url${FloodEndpoint.TORRENT_DELETE}") {
                    setGenericJsonBody {
                        putJsonArray("hashes") {
                            add(hash)
                        }
                        put("deleteData", deleteFiles)
                    }
                }
            }
            return@runBlocking response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Log.e("FloodClient for $url", ex, printStack = true) { "Failed to delete torrent!" }
        }
        return@runBlocking false
    }

    private fun setTags(hash: String, vararg tags: String): Boolean = runBlocking {
        try {
            val tagList = if (tags.find { it.isNotBlank() } != null) tags.asList() else listOf<String>()
            val response = attemptRequest {
                httpClient.patch("$url${FloodEndpoint.TORRENT_TAG}") {
                    setGenericJsonBody(mapOf("hashes" to listOf(hash), "tags" to tagList))
                }
            }
            return@runBlocking response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Log.e("FloodClient for $url", ex, printStack = true) { "Failed to set tags!" }
        }
        return@runBlocking false
    }

}

enum class FloodEndpoint(private val endpoint: String) {
    AUTH("/api/auth/authenticate"),

    TORRENT_TAG("/api/torrents/tags"),
    TORRENT_LIST("/api/torrents"),
    TORRENT_STOP("/api/torrents/stop"),
    TORRENT_START("/api/torrents/start"),
    TORRENT_DELETE("/api/torrents/delete"),
    TORRENT_ADD_URL("/api/torrents/add-urls"),
    TORRENT_ADD_FILE("/api/torrents/add-files");

    override fun toString(): String {
        return this.endpoint
    }
}