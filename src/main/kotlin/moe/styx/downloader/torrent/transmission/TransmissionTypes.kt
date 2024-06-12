package moe.styx.downloader.torrent.transmission

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Transmission {
    @Serializable
    data class TorrentListRequest(
        val method: String = "torrent-get",
        val arguments: TorrentListRequestArgs = TorrentListRequestArgs()
    )

    @Serializable
    data class TorrentListRequestArgs(
        val fields: List<String> = listOf("addedDate", "status", "hashString", "name", "labels", "isFinished")
    )

    @Serializable
    data class TorrentAddRequest(val method: String = "torrent-add", @SerialName("arguments") val torrent: TorrentAddRequestTorrent)

    @Serializable
    data class TorrentAddRequestTorrent(
        val filename: String,
        @SerialName("download-dir") val downloadDir: String,
        val paused: Boolean,
        val labels: List<String> = listOf("styx")
    )

    @Serializable
    data class TorrentRemoveRequest(val method: String = "torrent-remove", val arguments: TorrentRemoveRequestArgs)

    @Serializable
    data class TorrentRemoveRequestArgs(@SerialName("ids") val hashes: List<String>, @SerialName("delete-local-data") val deleteFiles: Boolean)
}

