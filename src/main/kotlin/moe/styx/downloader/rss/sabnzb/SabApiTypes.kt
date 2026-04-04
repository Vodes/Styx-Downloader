package moe.styx.downloader.rss.sabnzb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object SabApi {
    @Serializable
    data class QueueResponse(
        val queue: Queue
    )

    @Serializable
    data class Queue(
        val status: String,
        val slots: List<QueueSlot> = emptyList()
    )

    @Serializable
    data class QueueSlot(
        @SerialName("filename") val name: String,
        @SerialName("nzo_id") val nzoId: String,
        val status: String,
        @SerialName("time_added") val timeAdded: Long? = null,
    )

    @Serializable
    data class HistoryResponse(
        val history: History
    )

    @Serializable
    data class History(
        val slots: List<HistorySlot> = emptyList()
    )

    @Serializable
    data class HistorySlot(
        val name: String = "",
        @SerialName("nzb_name") val nzbName: String = "",
        @SerialName("nzo_id") val nzoId: String,
        val status: String,
        @SerialName("time_added") val timeAdded: Long? = null,
        val retry: JsonElement? = null,
    )

    @Serializable
    data class NZBAddResponse(val status: Boolean, @SerialName("nzo_ids") val jobIDs: List<String>)
}
