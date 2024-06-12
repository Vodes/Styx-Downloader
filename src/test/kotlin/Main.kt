import moe.styx.downloader.torrent.transmission.TransmissionClient


fun main() {
    testTransmission()
}

private fun testTransmission() {
    val client = TransmissionClient(
        System.getenv("TRANSMISSION_URL"),
        System.getenv("TRANSMISSION_USER"),
        System.getenv("TRANSMISSION_PASS")
    )
    println("Auth: ${client.authenticate()}")
    println("Torrents: ${client.listTorrents()}")
    val t = client.addTorrentByURL("https://linuxmint.com/torrents/linuxmint-21-cinnamon-64bit.iso.torrent", "/downloads")
    if (t != null) {
        println("Torrent added: ${t.name}")
        println("Deleted: ${client.deleteTorrent(t.hash)}")
    }
}