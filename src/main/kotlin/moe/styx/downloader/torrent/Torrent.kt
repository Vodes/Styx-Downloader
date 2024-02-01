package moe.styx.downloader.torrent

import io.ktor.client.statement.*
import io.ktor.http.*
import moe.styx.downloader.Log
import moe.styx.types.eqI

abstract class TorrentClient(val user: String, val pass: String) {

    abstract fun authenticate(): Boolean
    abstract fun listTorrents(): List<Torrent>
    abstract fun addTorrentByURL(torrentURL: String, destinationDir: String, start: Boolean = true): Torrent?
    abstract fun deleteTorrent(hash: String, deleteFiles: Boolean = false): Boolean

    inline fun attemptRequest(requestFunc: () -> HttpResponse): HttpResponse {
        var response = requestFunc()
        if (response.status in arrayOf(HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized)) {
            if (!authenticate()) {
                Log.e { "Could not reauthenticate client!" }
                throw Exception()
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