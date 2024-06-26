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
import moe.styx.downloader.torrent.RSSHandler
import net.peanuuutz.tomlkt.Toml
import java.io.File
import kotlin.system.exitProcess

object Main {
    lateinit var appDir: File
    lateinit var configFile: File

    lateinit var config: Config

    val toml = Toml {
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    fun isInitialized(): Boolean {
        return ::config.isInitialized
    }
}

val dbClient by lazy {
    DBClient(
        "jdbc:postgresql://${Main.config.dbConfig.ip}/Styx",
        "org.postgresql.Driver",
        Main.config.dbConfig.user,
        Main.config.dbConfig.pass,
        10
    )
}

fun loadConfig(args: Array<String> = emptyArray()) {
    Main.appDir = if (args.isEmpty()) getAppDir() else File(args[0]).also { it.mkdirs() }
    Main.configFile = File(Main.appDir, "config.toml")
    if (!Main.configFile.exists()) {
        Main.configFile.writeText(Main.toml.encodeToString(Config()))
        println("Please setup your config at: ${Main.configFile.absolutePath}")
        exitProcess(1)
    }
    Main.config = Main.toml.decodeFromString(Main.configFile.readText())
    getHttpClient(Main.config.httpUserAgent)
}

fun main(args: Array<String>) {
    loadConfig(args)
    MetadataFetcher.start()
    startBot()
    FTPHandler.start()
    if (Main.config.torrentConfig.defaultSeedDir.isNotBlank() && Main.config.torrentConfig.defaultNonSeedDir.isNotBlank())
        RSSHandler.start()

    Main.config.ircConfig.servers.forEach { (server, channels) ->
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