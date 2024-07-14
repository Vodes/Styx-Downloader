import moe.styx.common.data.DownloadableOption
import moe.styx.common.data.DownloaderTarget
import moe.styx.common.data.SourceType
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.rss.RSSHandler
import java.util.*
import kotlin.test.assertTrue

object RSSTest {

    fun testRSSFeed() {
        val option = DownloadableOption(0, "\\[SubsPlease\\].*(Boku no Hero Academia).*\\(1080p\\).*\\.mkv", SourceType.TORRENT)
        val target = DownloaderTarget(UUID.randomUUID().toString().uppercase(), mutableListOf(option))
        val test = RSSHandler.checkFeed("%example% subsplease hero academia", listOf(option), listOf(target))

        assertTrue("Torrent request did not match anything!") {
            test.find {
                val value = (it.second as? ParseResult.DENIED)?.parseFailReason
                value != null && value != ParseDenyReason.NoOptionMatched
            } != null
        }
    }
}