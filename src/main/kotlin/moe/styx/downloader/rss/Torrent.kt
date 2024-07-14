package moe.styx.downloader.rss

import io.ktor.client.statement.*
import io.ktor.http.*
import moe.styx.common.extension.eqI
import moe.styx.downloader.utils.Log

abstract class TorrentClient(val user: String, val pass: String) {

    abstract fun authenticate(): Boolean
    abstract fun listTorrents(): List<Torrent>
    abstract fun addTorrentByURL(torrentURL: String, destinationDir: String, start: Boolean = true): Torrent?
    abstract fun deleteTorrent(hash: String, deleteFiles: Boolean = false): Boolean

    inline fun attemptRequest(requestFunc: () -> HttpResponse): HttpResponse {
        var response = requestFunc()
        if (response.status in arrayOf(HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized, HttpStatusCode.Conflict)) {
            if (!authenticate()) {
                Log.e { "Could not reauthenticate client!" }
                return response
            }
            response = requestFunc()
        }
        return response
    }
}

data class Torrent(val name: String, val hash: String, val added: Long, val tags: List<String> = listOf(), val status: List<String> = listOf()) {
    fun isCompleted() = status.find { it.contains("complete", true) } != null
    fun hasTag(tag: String) = tags.find { it eqI tag } != null
}