package moe.styx.downloader

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import moe.styx.downloader.other.IRCClient
import moe.styx.downloader.utils.launchGlobal
import moe.styx.types.json
import net.peanuuutz.tomlkt.Toml
import java.io.File
import kotlin.system.exitProcess

lateinit var httpClient: HttpClient

object Main {
    lateinit var appDir: File
    lateinit var configFile: File

    lateinit var config: Config

    val toml = Toml {
        ignoreUnknownKeys = true
        explicitNulls = true
    }
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
    httpClient = HttpClient {
        install(ContentNegotiation) { json }
        install(ContentEncoding)
        install(HttpCookies)
        install(UserAgent) { agent = Main.config.httpUserAgent }
    }
}

fun main(args: Array<String>) {
    loadConfig(args)
//    FTPHandler.start()
//    if (Main.config.torrentConfig.defaultSeedDir.isNotBlank() && Main.config.torrentConfig.defaultNonSeedDir.isNotBlank())
//        RSSHandler.start()

    Main.config.ircConfig.servers.forEach { server, channels ->
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