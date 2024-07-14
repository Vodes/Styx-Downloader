import moe.styx.common.data.DownloadableOption
import moe.styx.common.data.DownloaderTarget
import moe.styx.common.data.SourceType
import moe.styx.downloader.rss.RSSHandler
import moe.styx.downloader.rss.sabnzb.SABnzbdClient
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

object SabTests {
    fun testSabnzbd() {
        val option = DownloadableOption(0, "\\[SubsPlease\\].*(Boku no Hero Academia).*\\(1080p\\).*\\.mkv", SourceType.USENET)
        val target = DownloaderTarget(UUID.randomUUID().toString().uppercase(), mutableListOf(option))
        val test = RSSHandler.checkFeed("%example% subsplease hero academia", listOf(option), listOf(target))

        val client = SABnzbdClient(
            System.getenv("SABNZBD_URL"),
            System.getenv("SABNZBD_KEY"),
        )
        assertTrue("Could not authenticate SABnzbd client!") { client.authenticate() }

        val nzbURL = test.find { it.first.getNZBURL() != null }?.first?.getNZBURL()
        assertNotNull(nzbURL, "Could not find entry with valid NZB enclosure!")
        assertTrue("Could not add NZB to SABnzbd!") { client.addNZBByURL(nzbURL) }
    }
}