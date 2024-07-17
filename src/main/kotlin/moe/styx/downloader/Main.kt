package moe.styx.downloader

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import moe.styx.common.http.getHttpClient
import moe.styx.common.util.launchGlobal
import moe.styx.db.DBClient
import moe.styx.downloader.ftp.FTPHandler
import moe.styx.downloader.other.IRCClient
import moe.styx.downloader.other.MetadataFetcher
import moe.styx.downloader.other.startBot
import moe.styx.downloader.rss.RSSHandler
import moe.styx.downloader.rss.startCheckingLocal
import net.peanuuutz.tomlkt.Toml
import java.io.File
import kotlin.system.exitProcess

object Main {
    var appDir: File? = null
    var configFile: File? = null
    var configLastModified = 0

    var localConfig: Config? = null

    val toml = Toml {
        ignoreUnknownKeys = true
        explicitNulls = true
    }
}

val dbClient by lazy {
    DBClient(
        "jdbc:postgresql://${downloaderConfig.dbConfig.ip}/Styx",
        "org.postgresql.Driver",
        downloaderConfig.dbConfig.user,
        downloaderConfig.dbConfig.pass,
        10
    )
}

val downloaderConfig: Config
    get() {
        if (Main.appDir == null || Main.configFile == null) {
            Main.appDir = getAppDir().also { it.mkdirs() }
            Main.configFile = File(Main.appDir, "config.toml")
        }
        if (!Main.configFile!!.exists()) {
            Main.configFile!!.writeText(Main.toml.encodeToString(Config()))
            println("Please setup your config at: ${Main.configFile!!.absolutePath}")
            exitProcess(1)
        }
        if (Main.localConfig == null || Main.configFile!!.lastModified() > Main.configLastModified) {
            Main.localConfig = Main.toml.decodeFromString(Main.configFile!!.readText())
            getHttpClient(Main.localConfig!!.httpUserAgent)
        }
        return Main.localConfig!!
    }

fun main(args: Array<String>) {
    Main.appDir = if (args.isEmpty()) getAppDir() else File(args[0]).also { it.mkdirs() }
    Main.configFile = File(Main.appDir, "config.toml")
    MetadataFetcher.start()
    startBot()
    FTPHandler.start()
    if (downloaderConfig.rssConfig.defaultSeedDir.isNotBlank() && downloaderConfig.rssConfig.defaultNonSeedDir.isNotBlank()) {
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