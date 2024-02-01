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
import moe.styx.downloader.*
import moe.styx.downloader.parsing.ParseResult
import moe.styx.types.DownloadableOption
import moe.styx.types.DownloaderTarget
import moe.styx.types.eqI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.jvm.optionals.getOrNull
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val torrentURLRegex = "href=\"(?<url>https?:\\/\\/[^ \"<>]+?\\.torrent)\"".toRegex(RegexOption.IGNORE_CASE)
private val generalURLRegex = "https?:\\/\\/.+".toRegex(RegexOption.IGNORE_CASE)

object RSSHandler {
    private lateinit var torrentClient: TorrentClient
    private val reader = RssReader()

    fun start() {
        val client = Main.config.torrentConfig.createClient()
        if (client == null || !client.authenticate()) {
            Log.e("RSSHandler::start") { "Could not initiate the torrent client." }
            return
        }
        val oneMinute = 1.toDuration(DurationUnit.MINUTES)
        launchThreaded {
            while (true) {
                val targets = getDBClient().executeGet { getTargets() }
                val rssOptions = targets.getRSSOptions()
                for ((feedURL, options) in rssOptions.iterator()) {
                    val results = checkFeed(feedURL, options, targets).filter { it.second is ParseResult.OK }
                    delay(oneMinute)
                }

                delay(12.toDuration(DurationUnit.MINUTES))
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
                val filtered = items.filter {
                    if (it.getTorrentURL().isBlank())
                        return@filter false
                    option.ignoreDelay || it.getUnixPubTime() > (Clock.System.now().epochSeconds - 1800000)
                }
                for (item in filtered) {
                    val parseResult = option.episodeWanted(item.title, parent, true)
                    results.add(item to parseResult)
                }
            }
            return@runBlocking results.toList()
        }

    fun test() = runBlocking {
        val response =
            httpClient.get("https://feed.animetosho.org/rss2?only_tor=1")
        if (!response.status.isSuccess())
            return@runBlocking

        val items = runCatching { reader.read(response.bodyAsChannel().toInputStream()).toList().map { FeedItem.ofItem(it) } }
            .onFailure { return@runBlocking }
            .getOrNull() ?: return@runBlocking
        println(items.firstOrNull()?.getTorrentURL())
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
        return if (guid.isNotBlank() && generalURLRegex.containsMatchIn(guid))
            generalURLRegex.matchEntire(guid)?.groups?.get(0)?.value ?: ""
        else
            this.link
    }

    fun getTorrentURL(): String {
        return if (link.contains(".torrent", true)) {
            link
        } else if (enclosure != null && enclosure.type.contains("torrent", true)) {
            enclosure.url
        } else if (description.isNotBlank() && torrentURLRegex.containsMatchIn(description)) {
            torrentURLRegex.matchEntire(description)?.groups?.get("url")?.value ?: ""
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