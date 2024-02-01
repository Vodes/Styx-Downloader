package moe.styx.downloader

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import moe.styx.types.DownloadableOption
import moe.styx.types.DownloaderTarget
import moe.styx.types.SourceType
import moe.styx.types.json
import java.io.File
import kotlin.system.exitProcess

lateinit var httpClient: HttpClient
val targets = mutableListOf<DownloaderTarget>()

private fun addTestTargets() {
    targets.add(
        DownloaderTarget(
            "46E72D70-10B9-4ECA-BA5D-A4D2E04162A5", mutableListOf(
                DownloadableOption(
                    0,
                    "(Vinland Saga S2).*1080p.*AAC.*\\[GerEngSub\\].*(\\.mkv)",
                    SourceType.FTP,
                    sourcePath = "/FTP-Zugang Cloud (Dropbox)/WEB/Vinland Saga S2 [JapGerDub,GerEngSub,CR]"
                ),

                DownloadableOption(
                    2,
                    "\\[Other\\] (Vinland Saga S2).*1080p.*AAC.*.*(\\.mkv)",
                    SourceType.FTP,
                    sourcePath = "/FTP-Zugang Cloud (Dropbox)/WEB [FTP-Exclusive]/Vinland Saga (S02) [GerJapDub,GerEngSub,WebRip,Other]",
                    waitForPrevious = true
                )
            )
        )
    )
}

object Main {
    lateinit var appDir: File
    lateinit var configFile: File

    lateinit var config: Config
}

fun loadConfig(args: Array<String> = emptyArray()) {
    Main.appDir = if (args.isEmpty()) getAppDir() else File(args[0]).also { it.mkdirs() }
    Main.configFile = File(Main.appDir, "config.toml")
    if (!Main.configFile.exists()) {
        Main.configFile.writeText(toml.encodeToString(Config()))
        println("Please setup your config at: ${Main.configFile.absolutePath}")
        exitProcess(1)
    }
    Main.config = toml.decodeFromString(Main.configFile.readText())
    httpClient = HttpClient {
        install(ContentNegotiation) { json }
        install(ContentEncoding)
        install(HttpCookies)
        install(UserAgent) { agent = Main.config.httpUserAgent }
    }
}

fun main(args: Array<String>) {
    loadConfig(args)
    addTestTargets()

    println(targets.episodeWanted("[Other] Vinland Saga S2 - 03 [Foxtrot][CR-NX-Dub][WebRip 1080p Ma10 AAC-EAC3].mkv"))
//
    println(targets.episodeWanted("Vinland Saga S2E03 [1080p][AAC][JapGerDub][GerEngSub][Web-DL].mkv"))
//    RSSHandler.test()
}