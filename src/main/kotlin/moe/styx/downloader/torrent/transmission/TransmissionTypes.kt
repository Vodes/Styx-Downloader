package moe.styx.downloader.torrent.transmission

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Transmission {
    @Serializable
    data class ListRequest(
        val method: String = "torrent-get",
        val arguments: ListRequestArgs = ListRequestArgs()
    )

    @Serializable
    data class ListRequestArgs(
        val fields: List<String> = listOf(
            "addedDate",
            "status",
            "hashString",
            "name",
            "labels",
            "isFinished",
            "leftUntilDone",
            "percentDone",
            "percentComplete",
            "error"
        )
    )

    @Serializable
    data class AddRequest(val method: String = "torrent-add", @SerialName("arguments") val torrent: AddRequestTorrent)

    @Serializable
    data class AddRequestTorrent(
        val filename: String,
        @SerialName("download-dir") val downloadDir: String,
        val paused: Boolean,
        val labels: List<String> = listOf("styx")
    )

    @Serializable
    data class RemoveRequest(val method: String = "torrent-remove", val arguments: RemoveRequestArgs)

    @Serializable
    data class RemoveRequestArgs(@SerialName("ids") val hashes: List<String>, @SerialName("delete-local-data") val deleteFiles: Boolean)

    @Serializable
    data class StopRequest(val method: String = "torrent-stop", val arguments: StopRequestArgs)

    @Serializable
    data class StopRequestArgs(@SerialName("ids") val hashes: List<String>)
}

