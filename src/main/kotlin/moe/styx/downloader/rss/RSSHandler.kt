package moe.styx.downloader.rss

import com.apptasticsoftware.rssreader.Enclosure
import com.apptasticsoftware.rssreader.Item
import com.apptasticsoftware.rssreader.RssReader
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import moe.styx.common.data.DownloadableOption
import moe.styx.common.data.DownloaderTarget
import moe.styx.common.data.SourceType
import moe.styx.common.extension.anyEquals
import moe.styx.common.extension.eqI
import moe.styx.common.http.httpClient
import moe.styx.common.util.launchGlobal
import moe.styx.common.util.launchThreaded
import moe.styx.db.tables.DownloaderTargetsTable
import moe.styx.downloader.dbClient
import moe.styx.downloader.downloaderConfig
import moe.styx.downloader.episodeWanted
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.rss.transmission.TransmissionClient
import moe.styx.downloader.utils.*
import org.jetbrains.exposed.sql.selectAll
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object RSSHandler {
    private lateinit var torrentClient: TorrentClient
    private val reader = RssReader()
    private val alreadyAdded = mutableListOf<String>()
    val oneMinute = 1.toDuration(DurationUnit.MINUTES)

    fun start() {
        startTorrents()
        startUsenet()
    }

    private fun startUsenet() {
        val client = downloaderConfig.rssConfig.createSAB()
        if (client == null || !client.authenticate()) {
            Log.e("RSSHandler::start") { "Could not initiate the SABnzbd client." }
            return
        }
        Log.i { "Starting Usenet Handler" }
        launchGlobal {
            delay(15.toDuration(DurationUnit.SECONDS))
            while (true) {
                val targets = dbClient.transaction { DownloaderTargetsTable.query { selectAll().toList() } }
                val rssOptions = targets.getRSSOptions(SourceType.USENET)
                for ((feedURL, options) in rssOptions.iterator()) {
                    val urlNoKeys = removeKeysFromURL(feedURL)
                    val results = runCatching { checkFeed(feedURL, options, targets) }.getOrNull() ?: emptyList()
                    for ((item, _) in results.filter { it.second is ParseResult.OK }) {
                        val nzbUrl = item.getNZBURL()
                        if (nzbUrl == null || alreadyAdded.anyEquals(nzbUrl))
                            continue
                        Log.d("RSSHandler for Feed: $urlNoKeys") { "Downloading: ${item.title}" }
                        val added = client.addNZBByURL(nzbUrl)
                        if (!added) {
                            Log.e("RSSHandler for Feed: $urlNoKeys") { "Could not add NZB with URL: ${removeKeysFromURL(nzbUrl)}\nPost: ${item.getPostURL()}" }
                            delay(oneMinute)
                            continue
                        }
                        alreadyAdded.add(nzbUrl)
                        delay(8000)
                    }
                    if (feedURL.contains("animetosho"))
                        delay(10000)
                    else
                        delay(oneMinute)
                }
                delay(8.toDuration(DurationUnit.MINUTES))
            }
        }
    }

    private fun startTorrents() {
        val client = downloaderConfig.rssConfig.createTorrentClient()
        if (client == null || !client.authenticate()) {
            Log.e("RSSHandler::start") { "Could not initiate the torrent client." }
            return
        }
        Log.i { "Starting Torrent Handler" }
        torrentClient = client
        launchGlobal {
            delay(30.toDuration(DurationUnit.SECONDS))
            while (true) {
                val targets = dbClient.transaction { DownloaderTargetsTable.query { selectAll().toList() } }
                val rssOptions = targets.getRSSOptions(SourceType.TORRENT)
                for ((feedURL, options) in rssOptions.iterator()) {
                    val urlNoKeys = removeKeysFromURL(feedURL)
                    val results = runCatching { checkFeed(feedURL, options, targets) }.getOrNull() ?: emptyList()
                    for ((item, parseResult) in results.filter { it.second is ParseResult.OK }) {
                        val result = parseResult as ParseResult.OK
                        val torrentUrl = item.getTorrentURL()
                        if (alreadyAdded.anyEquals(torrentUrl))
                            continue
                        Log.d("RSSHandler for Feed: $urlNoKeys") { "Downloading: ${item.title}" }
                        val torrent = torrentClient.addTorrentByURL(
                            torrentUrl,
                            if (result.option.keepSeeding) downloaderConfig.rssConfig.defaultSeedDir else downloaderConfig.rssConfig.defaultNonSeedDir
                        )
                        if (torrent == null) {
                            Log.e("RSSHandler for Feed: $urlNoKeys") { "Could not add torrent with URL: ${removeKeysFromURL(torrentUrl)}\nPost: ${item.getPostURL()}" }
                            delay(oneMinute)
                            continue
                        }
                        alreadyAdded.add(torrentUrl)
                        if (!result.option.keepSeeding)
                            waitAndDelete(torrent)
                        delay(8000)
                    }
                    if (feedURL.contains("animetosho"))
                        delay(10000)
                    else
                        delay(oneMinute)
                }
                delay(12.toDuration(DurationUnit.MINUTES))
            }
        }
    }

    fun checkFeed(feedURL: String, options: List<DownloadableOption>, targets: List<DownloaderTarget>): List<Pair<FeedItem, ParseResult>> =
        runBlocking {
            val (resolved, query) = resolveTemplate(feedURL)
            val source = "RSSHandler for URL: $resolved"
            val response = httpClient.get(resolved) {
                url {
                    if (query.isNotBlank() && !parameters.contains("q")) {
                        parameters.append("q", query)
                    }
                }
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                Log.e(source) { "Failed to fetch feed. Error Code: ${response.status}\nResponse body: $body" }
                return@runBlocking emptyList()
            }
            val items = runCatching { reader.read(response.bodyAsChannel().toInputStream()).toList().map { FeedItem.ofItem(it) } }
                .onFailure {
                    Log.e(source, it) { "Failed to parse Feed." }
                    return@runBlocking emptyList()
                }
                .getOrNull() ?: return@runBlocking emptyList()
            val results = mutableListOf<Pair<FeedItem, ParseResult>>()
            for (option in options) {
                val parent = option parentIn targets
                for (item in items) {
                    if (!option.ignoreDelay && item.getUnixPubTime() < (Clock.System.now().epochSeconds - 1800000)) {
                        results.add(item to ParseResult.DENIED(ParseDenyReason.PostIsTooOld))
                        continue
                    }
                    if (option.source == SourceType.USENET && item.getNZBURL().isNullOrBlank()) {
                        results.add(item to ParseResult.FAILED(ParseDenyReason.NZBNotFound))
                        continue
                    } else if (option.source == SourceType.TORRENT && item.getTorrentURL().isBlank()) {
                        results.add(item to ParseResult.FAILED(ParseDenyReason.TorrentNotFound))
                        continue
                    }
                    var titleToCheck = item.title
                    if (titleToCheck.filter { it == '/' }.length >= 3) {
                        titleToCheck = titleToCheck.split("/").maxBy { it.length }
                    }
                    val parseResult = option.episodeWanted(titleToCheck, parent, true)
                    results.add(item to parseResult)
                }
            }
            return@runBlocking results.toList()
        }

    private fun waitAndDelete(torrent: Torrent) = waitAndDelete(torrentClient, torrent)

    fun waitAndDelete(client: TorrentClient, torrent: Torrent) {
        launchThreaded {
            var done = false
            while (!done) {
                delay(3000)
                runCatching {
                    val allTorrents = client.listTorrents()
                    val found = allTorrents.find { it.hash eqI torrent.hash }
                    if (found == null || found.isCompleted()) {
                        done = true
                        if (client is TransmissionClient)
                            delay(9000)
                    }
                }
                delay(1000)
            }
            (client as? TransmissionClient)?.stopTorrent(torrent.hash) ?: client.deleteTorrent(torrent.hash)
        }
    }
}

data class FeedItem(
    val title: String,
    val author: String,
    val category: String,
    val description: String,
    val guid: String,
    val link: String,
    val pubDate: String,
    val enclosures: List<Enclosure>
) {

    fun getUnixPubTime(): Long {
        if (pubDate.isBlank())
            return 0

        val time = ZonedDateTime.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(pubDate))
        return time.toEpochSecond()
    }

    fun getPostURL(): String {
        return if (guid.isNotBlank() && RegexCollection.generalURLRegex.containsMatchIn(guid))
            RegexCollection.generalURLRegex.matchEntire(guid)?.groups?.get(0)?.value ?: ""
        else
            this.link
    }

    fun getNZBURL(): String? {
        val nzbEnclosure = enclosures.find { it.type.contains("x-nzb", true) }
        return nzbEnclosure?.url
    }

    fun getTorrentURL(): String {
        val torrentEnclosure = enclosures.find { it.type.contains("bittorrent", true) && !it.type.contains("magnet", true) }
        return if (link.contains(".torrent", true)) {
            link
        } else if (torrentEnclosure != null) {
            torrentEnclosure.url
        } else if (description.isNotBlank() && RegexCollection.torrentHrefRegex.containsMatchIn(description)) {
            RegexCollection.torrentHrefRegex.matchEntire(description)?.groups?.get("url")?.value ?: ""
        } else
            link
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FeedItem)
            return super.equals(other)
        val isU2 = link.contains("u2.dmhy.org", true)
        val sameLink = link.trim() eqI other.link.trim()
        val sameTitle = title.trim() eqI other.title.trim()

        return if (isU2)
            sameLink || sameTitle
        else
            sameLink
    }

    companion object {
        fun ofItem(item: Item): FeedItem {
            return FeedItem(
                item.title.orElse(""),
                item.author.orElse(""),
                item.categories.firstOrNull() ?: "",
                item.description.orElse(""),
                item.guid.orElse(""),
                item.link.orElse(""),
                item.pubDate.orElse(""),
                item.enclosures
            )
        }
    }
}