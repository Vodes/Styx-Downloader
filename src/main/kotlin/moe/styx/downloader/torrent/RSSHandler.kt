package moe.styx.downloader.torrent

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
import moe.styx.db.getTargets
import moe.styx.downloader.Main
import moe.styx.downloader.episodeWanted
import moe.styx.downloader.getDBClient
import moe.styx.downloader.httpClient
import moe.styx.downloader.other.handleFile
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.utils.*
import moe.styx.types.DownloadableOption
import moe.styx.types.DownloaderTarget
import moe.styx.types.eqI
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.jvm.optionals.getOrNull
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object RSSHandler {
    private lateinit var torrentClient: TorrentClient
    private val reader = RssReader()

    fun start() {
        val client = Main.config.torrentConfig.createClient()
        if (client == null || !client.authenticate()) {
            Log.e("RSSHandler::start") { "Could not initiate the torrent client." }
            return
        }
        torrentClient = client
        val oneMinute = 1.toDuration(DurationUnit.MINUTES)
        launchThreaded {
            delay(oneMinute)
            while (true) {
                val targets = getDBClient().executeGet { getTargets() }
                val rssOptions = targets.getRSSOptions()
                for ((feedURL, options) in rssOptions.iterator()) {
                    val results = checkFeed(feedURL, options, targets).filter { it.second is ParseResult.OK }
                    for ((item, parseResult) in results) {
                        val result = parseResult as ParseResult.OK
                        val torrent = torrentClient.addTorrentByURL(
                            item.getTorrentURL(),
                            if (result.option.keepSeeding) Main.config.torrentConfig.defaultSeedDir else Main.config.torrentConfig.defaultNonSeedDir
                        )
                        if (torrent == null) {
                            Log.e("RSSHandler for Feed: $feedURL") { "Could not add torrent with URL: ${item.getTorrentURL()}" }
                            delay(oneMinute)
                            continue
                        }
                        if (!result.option.keepSeeding)
                            waitAndDelete(torrent)
                    }
                    delay(oneMinute)
                }
                delay(12.toDuration(DurationUnit.MINUTES))
            }
        }
        launchThreaded {
            val tempDir = File(Main.config.torrentConfig.defaultNonSeedDir)
            val seedDir = File(Main.config.torrentConfig.defaultSeedDir)
            while (true) {
                val cutOff = Clock.System.now().toEpochMilliseconds() - 30000
                val targets = getDBClient().executeGet { getTargets() }
                if (tempDir.exists() && tempDir.isDirectory) {
                    (tempDir.listFiles()?.filter { it.isFile && it.lastModified() < cutOff } ?: emptyList<File>()).forEach {
                        val parseResult = targets.episodeWanted(it.name)
                        if (parseResult !is ParseResult.OK)
                            return@forEach
                        handleFile(it, parseResult.target, parseResult.option)
                    }
                }
                if (seedDir.exists() && seedDir.isDirectory) {
                    (seedDir.listFiles()?.filter { it.isFile && it.lastModified() < cutOff } ?: emptyList<File>()).forEach {
                        val parseResult = targets.episodeWanted(it.name)
                        if (parseResult !is ParseResult.OK)
                            return@forEach
                        val copy = it.copyTo(File(tempDir, it.name), overwrite = true)
                        handleFile(copy, parseResult.target, parseResult.option)
                    }
                }
                delay(10000)
            }
        }
    }

    fun checkFeed(feedURL: String, options: List<DownloadableOption>, targets: List<DownloaderTarget>): List<Pair<FeedItem, ParseResult>> =
        runBlocking {
            val source = "RSSHandler for URL: $feedURL"
            val response = httpClient.get(feedURL)
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
                val filtered = items.filter { it.getTorrentURL().isNotBlank() }
                for (item in filtered) {
                    if (!option.ignoreDelay && item.getUnixPubTime() < (Clock.System.now().epochSeconds - 1800000)) {
                        results.add(item to ParseResult.DENIED(ParseDenyReason.PostIsTooOld))
                        continue
                    }
                    val parseResult = option.episodeWanted(item.title, parent, true)
                    results.add(item to parseResult)
                }
            }
            return@runBlocking results.toList()
        }

    private fun waitAndDelete(torrent: Torrent) {
        launchThreaded {
            var done = false
            while (!done) {
                delay(3000)
                val allTorrents = torrentClient.listTorrents()
                val found = allTorrents.find { it.hash eqI torrent.hash }
                if (found == null || found.isCompleted())
                    done = true
                delay(1000)
            }
            torrentClient.deleteTorrent(torrent.hash)
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
    val enclosure: Enclosure?
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

    fun getTorrentURL(): String {
        return if (link.contains(".torrent", true)) {
            link
        } else if (enclosure != null && enclosure.type.contains("torrent", true)) {
            enclosure.url
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
                item.enclosure.getOrNull()
            )
        }
    }
}