package moe.styx.downloader.rss.sabnzb

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import moe.styx.common.extension.currentUnixSeconds
import moe.styx.common.extension.eqI
import moe.styx.common.http.httpClient
import moe.styx.common.json
import moe.styx.common.util.launchGlobal
import moe.styx.downloader.BuildConfig
import moe.styx.downloader.downloaderConfig
import moe.styx.downloader.rss.FeedItem
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.extractFilename
import moe.styx.downloader.utils.removeKeysFromURL
import java.net.URLDecoder
import kotlin.time.Duration.Companion.seconds

class SABnzbdClient(private var url: String, val apiKey: String) {
    private val itemsToDownload = mutableListOf<FeedItem>()
    private val downloadingItems = mutableListOf<NZBQueueItem>()
    val successItems = mutableListOf<NZBQueueItem>()
    val potentiallyDead = mutableListOf<NZBQueueItem>()


    init {
        url = url.removeSuffix("/")
        if (!url.contains("/sabnzbd")) {
            url = "$url/sabnzbd/api"
        } else if (!url.endsWith("api")) {
            url = "$url/api"
        }
    }

    private fun startQueue() {
        // Check download queue
        launchGlobal {
            while (isActive) {
                val now = currentUnixSeconds()
                val remoteFromToBeDownloaded = mutableListOf<FeedItem>()
                for (item in itemsToDownload) {
                    // Only try downloading items published over 3 minutes ago to perhaps avoid NZBs that haven't properly propagated yet.
                    if (item.getUnixPubTime() > (now - 180))
                        continue
                    runCatching {
                        if (addNZBByURL(item.getNZBURL()!!, item)) {
                            Log.i("SABnzbdClient for: $url") { "Download started: ${item.title}" }
                            remoteFromToBeDownloaded.add(item)
                        } else {
                            Log.e("SABnzbdClient for: $url") { "Failed to add NZB to client: ${item.title}" }
                        }
                    }.onFailure { Log.e("SABnzbdClient for: $url", it) { "Failed to add NZB to client: ${item.title}" } }
                    delay(5.seconds)
                }
                itemsToDownload.removeIf { remoteFromToBeDownloaded.find { item -> item == it } != null }
                remoteFromToBeDownloaded.clear()
                delay(5.seconds)
            }
        }
        // Check if downloading items are doing fine and try retrying if failed
        launchGlobal {
            delay(5.seconds)
            while (isActive) {
                var queued = fetchQueue(category = "styx")
                var downloadHistory = fetchHistory(category = "styx")
                var queuedItems = downloadingItems.map { item -> item to findQueueSlot(item, queued) }

                // Retry failed ones, if they've been sitting for 5 minutes or longer
                val now = currentUnixSeconds()
                val cutoff = if (BuildConfig.IS_DEV) 30 else 300
                val toRetry = queuedItems
                    .filter { it.second == null }
                    .map { it.first to findHistorySlot(it.first, downloadHistory) }
                    .filter { (item, historyItem) ->
                        historyItem?.status eqI "Failed" && item.retryState == RetryState.SHOULD && item.added < (now - cutoff)
                    }
                toRetry.forEach { (item, historyItem) ->
                    Log.d("SABnzbdClient for: $url") { "Retrying NZB due to failure: ${item.feedItem.title}" }

                    if (retryID(historyItem?.nzoId ?: item.jobID)) {
                        item.retryState = RetryState.IS
                        item.lastRetryAt = currentUnixSeconds()
                    }
                    delay(1.seconds)
                }
                if (toRetry.isNotEmpty()) {
                    delay(10.seconds)
                    queued = fetchQueue(category = "styx")
                    downloadHistory = fetchHistory(category = "styx")
                    queuedItems = downloadingItems.map { item -> item to findQueueSlot(item, queued) }
                }

                // Update states
                val updatedNow = currentUnixSeconds()
                val retryGrace = if (BuildConfig.IS_DEV) 15 else 60
                val removeFromDownloading = mutableListOf<NZBQueueItem>()
                for ((item, queueItem) in queuedItems) {
                    if (queueItem != null)
                        continue

                    val downloadedItem = findHistorySlot(item, downloadHistory) ?: continue
                    if (downloadedItem.status eqI "Completed") {
                        removeFromDownloading.add(item)
                        successItems.add(item)
                        Log.i("SABnzbdClient for: $url") { "Successfully downloaded NZB for: ${item.feedItem.title}" }
                    } else if (downloadedItem.status eqI "Failed") {
                        if (item.retryState == null)
                            item.retryState = RetryState.SHOULD
                        // Move the failed ones that already got retried to the dead list
                        else if (item.retryState == RetryState.IS && (item.lastRetryAt ?: 0) < (updatedNow - retryGrace)) {
                            item.retryState = RetryState.ALREADY_DID
                            removeFromDownloading.add(item)
                            potentiallyDead.add(item)
                            Log.i("SABnzbdClient for: $url") {
                                "Potentially dead (retry failed) NZB added to ignorelist: ${item.feedItem.title}\n\tURL: ${removeKeysFromURL(item.nzbURL)}\n\tPost: ${item.feedItem.getPostURL()}"
                            }
                        }
                    }
                }
                downloadingItems.removeIf { removeFromDownloading.find { item -> it == item } != null }
                removeFromDownloading.clear()
                delay(8.seconds)
            }
        }
    }

    fun addDownloadToQueue(feedItem: FeedItem) {
        val isAboutToBeDownloaded = itemsToDownload.find { it == feedItem } != null
        val isQueuedOrDownloaded = downloadingItems.find { it == feedItem } != null || successItems.find { it == feedItem } != null
        val isDead = potentiallyDead.find { it == feedItem } != null
        if (isAboutToBeDownloaded || isQueuedOrDownloaded || isDead || feedItem.getNZBURL() == null)
            return

        itemsToDownload.add(feedItem)
    }

    fun authenticate(): Boolean = runBlocking {
        val response = httpClient.get(url) {
            url {
                parameters.append("apikey", apiKey)
                parameters.append("mode", "set_config")
                parameters.append("section", "categories")
                parameters.append("name", "styx")
                parameters.append("dir", downloaderConfig.rssConfig.tempDir)
            }
        }
        val result = response.status.isSuccess()
        if (!result) {
            val body = response.bodyAsText()
            Log.e("SABnzbdClient for: $url") { "Failed to authenticate and set category!\n\n$body" }
        } else
            startQueue()
        return@runBlocking result
    }

    private fun retryID(id: String): Boolean = runBlocking {
        val response = httpClient.get(url) {
            url {
                parameters.append("apikey", apiKey)
                parameters.append("mode", "retry")
                parameters.append("value", id)
            }
        }
        val result = response.status.isSuccess()
        if (!result) {
            val body = response.bodyAsText()
            Log.e("SABnzbdClient for: $url") { "Failed to request retry!\n\n$body" }
        }
        return@runBlocking result
    }

    fun fetchQueue(ids: List<String> = emptyList(), category: String? = "styx"): SabApi.Queue = runBlocking {
        val response = httpClient.get(url) {
            url {
                parameters.append("apikey", apiKey)
                parameters.append("mode", "queue")
                if (category?.isNotBlank() == true)
                    parameters.append("cat", category)
                if (ids.isNotEmpty())
                    parameters.append("nzo_ids", ids.joinToString(","))
            }
        }
        val result = response.status.isSuccess()
        if (result) {
            val queueResponse = json.decodeFromString<SabApi.QueueResponse>(response.bodyAsText())
            return@runBlocking queueResponse.queue
        }
        val body = response.bodyAsText()
        Log.e("SABnzbdClient for: $url") { "Failed to fetch queue!\n\n$body" }
        return@runBlocking SabApi.Queue("")
    }

    fun fetchHistory(ids: List<String> = emptyList(), category: String? = "styx"): SabApi.History = runBlocking {
        val response = httpClient.get(url) {
            url {
                parameters.append("apikey", apiKey)
                parameters.append("mode", "history")
                if (category?.isNotBlank() == true)
                    parameters.append("cat", category)
                if (ids.isNotEmpty())
                    parameters.append("nzo_ids", ids.joinToString(","))
            }
        }
        val result = response.status.isSuccess()
        if (result) {
            val historyResponse = json.decodeFromString<SabApi.HistoryResponse>(response.bodyAsText())
            return@runBlocking historyResponse.history
        }
        val body = response.bodyAsText()
        Log.e("SABnzbdClient for: $url") { "Failed to fetch history!\n\n$body" }
        return@runBlocking SabApi.History()
    }

    fun deleteFromHistory(ids: List<String>, skipArchive: Boolean = false): Boolean = runBlocking {
        val response = httpClient.get(url) {
            url {
                parameters.append("apikey", apiKey)
                parameters.append("mode", "history")
                parameters.append("name", "delete")
                if (skipArchive)
                    parameters.append("archive", "0")

                parameters.append("value", ids.joinToString(","))
            }
        }
        val result = response.status.isSuccess()
        if (!result) {
            val body = response.bodyAsText()
            Log.e("SABnzbdClient for: $url") { "Failed to delete from history!\n\n$body" }
        }
        return@runBlocking result
    }

    private fun findQueueSlot(item: NZBQueueItem, queue: SabApi.Queue): SabApi.QueueSlot? {
        queue.slots.find { item.hasKnownJobID(it.nzoId) }?.let {
            item.rememberRemoteJob(it.nzoId, it.name)
            return it
        }
        if (item.retryState != RetryState.IS)
            return null

        val retriedAfter = (item.lastRetryAt ?: item.added) - 5
        val exact = queue.slots
            .filter { (it.timeAdded ?: 0) >= retriedAfter }
            .filter { normalizeSabName(it.name) in item.candidateNames() }
            .maxByOrNull { it.timeAdded ?: 0 }
        if (exact != null) {
            relinkJob(item, exact.nzoId, exact.name, "queue name match")
            return exact
        }

        val fuzzy = queue.slots
            .filter { (it.timeAdded ?: 0) >= retriedAfter }
            .filter { slot -> item.candidateNames().any { candidate -> namesLookRelated(candidate, normalizeSabName(slot.name)) } }
            .maxByOrNull { it.timeAdded ?: 0 }
        if (fuzzy != null) {
            relinkJob(item, fuzzy.nzoId, fuzzy.name, "queue fuzzy match")
        }
        return fuzzy
    }

    private fun findHistorySlot(item: NZBQueueItem, history: SabApi.History): SabApi.HistorySlot? {
        history.slots
            .filter { item.hasKnownJobID(it.nzoId) }
            .maxByOrNull { it.timeAdded ?: 0 }
            ?.let {
                item.rememberRemoteJob(it.nzoId, it.displayName())
                return it
            }
        if (item.retryState != RetryState.IS)
            return null

        val retriedAfter = (item.lastRetryAt ?: item.added) - 5
        val exact = history.slots
            .filter { (it.timeAdded ?: 0) >= retriedAfter }
            .filter { normalizeSabName(it.displayName()) in item.candidateNames() }
            .maxByOrNull { it.timeAdded ?: 0 }
        if (exact != null) {
            relinkJob(item, exact.nzoId, exact.displayName(), "history name match")
            return exact
        }

        val fuzzy = history.slots
            .filter { (it.timeAdded ?: 0) >= retriedAfter }
            .filter { slot -> item.candidateNames().any { candidate -> namesLookRelated(candidate, normalizeSabName(slot.displayName())) } }
            .maxByOrNull { it.timeAdded ?: 0 }
        if (fuzzy != null) {
            relinkJob(item, fuzzy.nzoId, fuzzy.displayName(), "history fuzzy match")
        }
        return fuzzy
    }

    private fun relinkJob(item: NZBQueueItem, newJobID: String, remoteName: String, source: String) {
        val previousJobID = item.jobID
        item.rememberRemoteJob(newJobID, remoteName)
        if (previousJobID != item.jobID) {
            Log.d("SABnzbdClient for: $url") {
                "Observed new SABnzbd job ID after retry via $source: ${item.feedItem.title} ($previousJobID -> ${item.jobID})"
            }
        }
    }

    private fun normalizeSabName(value: String): String =
        value
            .trim()
            .removeSuffix(".nzb")
            .lowercase()
            .filter(Char::isLetterOrDigit)

    private fun namesLookRelated(left: String, right: String): Boolean {
        if (left.isBlank() || right.isBlank())
            return false
        return left == right || left.contains(right) || right.contains(left)
    }

    private fun downloadAndAddNZB(nzbURL: String, ref: FeedItem): Boolean = runBlocking {
        val sanitizedURL = removeKeysFromURL(nzbURL)
        val nzbResponse = httpClient.get(nzbURL)
        if (!nzbResponse.status.isSuccess()) {
            Log.e { "Failed to download nzb for: $sanitizedURL" }
            return@runBlocking false
        }
        val nzbName = extractFilename(nzbResponse.headers[HttpHeaders.ContentDisposition]).ifBlank {
            URLDecoder.decode(sanitizedURL.split("/").last(), Charsets.UTF_8)
        }
        val nzbFile: ByteArray = nzbResponse.body()
        val response = httpClient.submitFormWithBinaryData(url = url, formData {
            append("name", nzbFile, Headers.build {
                append(HttpHeaders.ContentType, "application/x-nzb")
                append(HttpHeaders.ContentDisposition, "filename=\"$nzbName\"")
            })
        }) {
            url {
                parameters.append("apikey", apiKey)
                parameters.append("mode", "addfile")
                parameters.append("cat", "styx")
            }
        }
        val result = response.status.isSuccess()
        if (!result) {
            val body = response.bodyAsText()
            Log.e("SABnzbdClient for: $url") { "Failed to add NZB via file!\n\n${body}" }
        } else {
            val now = currentUnixSeconds()
            val body = json.decodeFromString<SabApi.NZBAddResponse>(response.bodyAsText())
            body.jobIDs.forEach { id ->
                downloadingItems.add(NZBQueueItem(ref, nzbURL, id, now))
            }
        }
        return@runBlocking result
    }

    private fun addNZBByURL(nzbURL: String, ref: FeedItem): Boolean = runBlocking {
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
            Log.e("SABnzbdClient for: $url") { "Failed to add NZB via URL!\n\n${body}" }
        } else {
            val now = currentUnixSeconds()
            val body = json.decodeFromString<SabApi.NZBAddResponse>(response.bodyAsText())
            body.jobIDs.forEach { id ->
                downloadingItems.add(NZBQueueItem(ref, nzbURL, id, now))
            }
        }
        return@runBlocking result
    }
}

enum class RetryState {
    SHOULD,
    IS,
    ALREADY_DID
}

data class NZBQueueItem(
    val feedItem: FeedItem,
    val nzbURL: String,
    var jobID: String,
    val added: Long,
    var retryState: RetryState? = null,
    var lastRetryAt: Long? = null,
    var remoteName: String = "",
    private val knownJobIDs: MutableSet<String> = mutableSetOf(),
) {
    init {
        knownJobIDs.add(jobID)
    }

    fun hasKnownJobID(id: String): Boolean = knownJobIDs.contains(id)

    fun rememberRemoteJob(id: String, name: String = remoteName) {
        if (id.isNotBlank()) {
            jobID = id
            knownJobIDs.add(id)
        }
        if (name.isNotBlank()) {
            remoteName = name
        }
    }

    fun candidateNames(): Set<String> =
        listOf(remoteName, feedItem.title)
            .map {
                it.trim()
                    .removeSuffix(".nzb")
                    .lowercase()
                    .filter(Char::isLetterOrDigit)
            }
            .filter { it.length >= 8 }
            .toSet()

    override fun equals(other: Any?): Boolean {
        if (other is FeedItem)
            return feedItem == other
        if (other is NZBQueueItem)
            return feedItem == other.feedItem || jobID == other.jobID
        return super.equals(other)
    }
}

private fun SabApi.HistorySlot.displayName(): String = nzbName.ifBlank { name }
