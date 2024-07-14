import moe.styx.downloader.rss.RSSHandler.waitAndDelete
import moe.styx.downloader.rss.transmission.TransmissionClient
import kotlin.test.assertTrue

object TransmissionTests {

    fun testTransmission() {
        val client = TransmissionClient(
            System.getenv("TRANSMISSION_URL"),
            System.getenv("TRANSMISSION_USER"),
            System.getenv("TRANSMISSION_PASS")
        )
        assertTrue("Could not authenticate transmission client!") { client.authenticate() }
        println("Torrents: ${client.listTorrents()}")

        val t = client.addTorrentByURL("https://linuxmint.com/torrents/linuxmint-21-cinnamon-64bit.iso.torrent", "/downloads")
        assertTrue("Could not add torrent!") { t != null }

        waitAndDelete(client, t!!)
    }
}