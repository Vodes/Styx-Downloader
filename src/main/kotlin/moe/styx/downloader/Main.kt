package moe.styx.downloader

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import moe.styx.common.config.DownloaderConfig
import moe.styx.common.config.UnifiedConfig
import moe.styx.common.util.launchGlobal
import moe.styx.db.DBClient
import moe.styx.downloader.ftp.FTPHandler
import moe.styx.downloader.other.IRCClient
import moe.styx.downloader.other.MetadataFetcher
import moe.styx.downloader.other.startBot
import moe.styx.downloader.rss.RSSHandler
import moe.styx.downloader.rss.TorrentClient
import moe.styx.downloader.rss.flood.FloodClient
import moe.styx.downloader.rss.sabnzb.SABnzbdClient
import moe.styx.downloader.rss.startCheckingLocal
import moe.styx.downloader.rss.transmission.TransmissionClient
import moe.styx.downloader.utils.Log

val dbClient by lazy {
    DBClient(
        "jdbc:postgresql://${UnifiedConfig.current.dbConfig.host()}/Styx",
        "org.postgresql.Driver",
        UnifiedConfig.current.dbConfig.user(),
        UnifiedConfig.current.dbConfig.pass(),
        10
    )
}

val downloaderConfig
    get() = UnifiedConfig.current.dlConfig

fun DownloaderConfig.createTorrentClient(): TorrentClient? {
    if (torrentConfig.clientURL.isBlank() || torrentConfig.clientUser.isBlank() || torrentConfig.clientPass.isBlank()) {
        Log.e { "No valid URL or login data were found in the torrent config." }
        return null
    }
    val client = when (torrentConfig.clientType.trim().lowercase()) {
        "flood" -> FloodClient(torrentConfig.clientURL, torrentConfig.clientUser, torrentConfig.clientPass)
        "transmission" -> TransmissionClient(torrentConfig.clientURL, torrentConfig.clientUser, torrentConfig.clientPass)
        else -> null
    }
    if (client == null)
        Log.e { "Unknown TorrentClient type!" }
    return client
}

fun DownloaderConfig.createSAB(): SABnzbdClient? {
    if (sabnzbdConfig.sabURL.isBlank() || sabnzbdConfig.sabApiKey.isBlank()) {
        Log.e { "No valid URL or apikey were found in the SABnzbd config." }
        return null
    }
    return SABnzbdClient(sabnzbdConfig.sabURL, sabnzbdConfig.sabApiKey)
}

fun main(args: Array<String>) {
    MetadataFetcher.start()
    startBot()
    FTPHandler.start()
    if (downloaderConfig.rssConfig.tempDir.isNotBlank() && downloaderConfig.rssConfig.seedDir.isNotBlank()) {
        RSSHandler.start()
        startCheckingLocal()
    }

    downloaderConfig.ircConfig.servers.forEach { (server, channels) ->
        launchGlobal {
            IRCClient(server, channels).start()
        }
    }

    runBlocking {
        while (true) {
            delay(10000)
        }
    }
}