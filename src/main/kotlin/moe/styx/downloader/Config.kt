package moe.styx.downloader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import moe.styx.downloader.torrent.TorrentClient
import moe.styx.downloader.torrent.flood.FloodClient
import moe.styx.types.eqI
import java.io.File

@Serializable
data class Config(
    val debug: Boolean = false,
    val defaultFTPConnectionString: String = "",
    val httpUserAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    @SerialName("DatabaseConfig")
    val dbConfig: DbConfig = DbConfig(),
    @SerialName("TorrentConfig")
    val torrentConfig: TorrentConfig = TorrentConfig(),
    @SerialName("IRCConfig")
    val ircConfig: IRCConfig = IRCConfig(mapOf("irc.rizon.net" to listOf("#subsplease", "#Styx-XDCC")))
)

@Serializable
data class IRCConfig(
    val servers: Map<String, List<String>>,
    val whitelistedXDCCBots: List<String> = listOf("StyxXDCC", "CR-ARUTHA|NEW")
)

@Serializable
data class TorrentConfig(
    val type: String = "Flood",
    val url: String = "",
    val user: String = "",
    val pass: String = ""
) {
    fun createClient(): TorrentClient? {
        if (url.isBlank() || user.isBlank() || pass.isBlank()) {
            Log.e { "No valid URL or login data was found in the torrent config." }
            return null
        }
        if (type eqI "Flood") {
            return FloodClient(url, user, pass)
        }
        Log.e { "Unknown TorrentClient type!" }
        return null
    }
}

@Serializable
data class DbConfig(
    val ip: String = "",
    val user: String = "",
    val pass: String = ""
)

fun getAppDir(): File {
    return if (System.getProperty("os.name").lowercase().contains("win")) {
        val mainDir = File(System.getenv("APPDATA"), "Styx")
        val dir = File(mainDir, "Downloader")
        dir.mkdirs()
        dir
    } else {
        val configDir = File(System.getProperty("user.home"), ".config")
        val mainDir = File(configDir, "Styx")
        val dir = File(mainDir, "Downloader")
        dir.mkdirs()
        dir
    }
}