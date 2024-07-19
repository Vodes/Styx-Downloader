package moe.styx.downloader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import moe.styx.downloader.rss.TorrentClient
import moe.styx.downloader.rss.flood.FloodClient
import moe.styx.downloader.rss.sabnzb.SABnzbdClient
import moe.styx.downloader.rss.transmission.TransmissionClient
import moe.styx.downloader.utils.Log
import net.peanuuutz.tomlkt.TomlComment
import java.io.File

@Serializable
data class Config(
    val debug: Boolean = false,
    val ignoreSpecialsAndPoint5: Boolean = true,
    val defaultFTPConnectionString: String = "",
    val httpUserAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    val tmdbToken: String = "",
    val imageBaseUrl: String = "",
    val siteBaseUrl: String = "",
    @SerialName("DatabaseConfig")
    val dbConfig: DbConfig = DbConfig(),
    @SerialName("RSSConfig")
    val rssConfig: RSSConfig = RSSConfig(),
    @SerialName("IRCConfig")
    val ircConfig: IRCConfig = IRCConfig(mapOf("irc.rizon.net" to listOf("#subsplease", "#Styx-XDCC"))),
    @SerialName("DiscordBotConfig")
    val discordBot: DiscordBotConfig = DiscordBotConfig()
)

@Serializable
data class IRCConfig(
    val servers: Map<String, List<String>>,
    val whitelistedXDCCBots: List<String> = listOf("StyxXDCC", "CR-ARUTHA|NEW")
)

@Serializable
data class RSSConfig(
    val defaultSeedDir: String = "",
    val defaultNonSeedDir: String = "",
    @TomlComment(
        """
        Do not include a query string in templates if you want to use dynamic queries in the webui!
        You can use them with %example%.
        '%example%my hero academia' would result in 'https://feed.animetosho.org/rss2?q=my+hero+academia'
        """
    )
    val feedTemplates: Map<String, String> = mapOf("example" to "https://feed.animetosho.org/rss2"),
    @SerialName("TorrentClient")
    val tcCfg: TorrentClientConfig = TorrentClientConfig(),
    @SerialName("SABnzbd")
    val sabCfg: SABnzbdConfig = SABnzbdConfig()
) {
    fun createTorrentClient(): TorrentClient? {
        if (tcCfg.url.isBlank() || tcCfg.user.isBlank() || tcCfg.pass.isBlank()) {
            Log.e { "No valid URL or login data were found in the torrent config." }
            return null
        }
        val client = when (tcCfg.type.trim().lowercase()) {
            "flood" -> FloodClient(tcCfg.url, tcCfg.user, tcCfg.pass)
            "transmission" -> TransmissionClient(tcCfg.url, tcCfg.user, tcCfg.pass)
            else -> null
        }
        if (client == null)
            Log.e { "Unknown TorrentClient type!" }
        return client
    }

    fun createSAB(): SABnzbdClient? {
        if (sabCfg.url.isBlank() || sabCfg.apikey.isBlank()) {
            Log.e { "No valid URL or apikey were found in the SABnzbd config." }
            return null
        }
        return SABnzbdClient(sabCfg.url, sabCfg.apikey)
    }
}

@Serializable
data class TorrentClientConfig(
    val type: String = "Transmission",
    val url: String = "",
    val user: String = "",
    val pass: String = ""
)

@Serializable
data class SABnzbdConfig(
    val url: String = "",
    val apikey: String = "",
)

@Serializable
data class DbConfig(
    val ip: String = "",
    val user: String = "",
    val pass: String = ""
)

@Serializable
data class DiscordBotConfig(
    val token: String = "",
    val announceServer: String = "",
    val announceChannel: String = "",
    val pingRole: String = "",
    val dmRole: String = "",
    val scheduleMessage: String = ""
) {
    fun isValid() = token.isNotBlank() && announceServer.isNotBlank() && announceChannel.isNotBlank()
}

fun getAppDir(): File {
    return if (System.getProperty("os.name").lowercase().contains("win")) {
        val mainDir = File(System.getenv("APPDATA"), "Styx")
        val dir = File(mainDir, "Downloader")
        dir.mkdirs()
        dir
    } else if (File("/.dockerenv").exists()) {
        File("/config").also { it.mkdirs() }
    } else {
        val configDir = File(System.getProperty("user.home"), ".config")
        val mainDir = File(configDir, "Styx")
        val dir = File(mainDir, "Downloader")
        dir.mkdirs()
        dir
    }
}