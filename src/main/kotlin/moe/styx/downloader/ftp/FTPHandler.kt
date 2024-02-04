package moe.styx.downloader.ftp

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import moe.styx.db.getTargets
import moe.styx.downloader.Main
import moe.styx.downloader.episodeWanted
import moe.styx.downloader.getDBClient
import moe.styx.downloader.other.handleFile
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.getFTPOptions
import moe.styx.downloader.utils.launchThreaded
import moe.styx.downloader.utils.parentIn
import moe.styx.types.DownloadableOption
import moe.styx.types.DownloaderTarget
import java.io.File
import java.time.temporal.ChronoUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object FTPHandler {
    lateinit var defaultFTPClient: FTPClient

    fun initClient(): Boolean {
        runCatching { defaultFTPClient = FTPClient.fromConnectionString(Main.config.defaultFTPConnectionString) }
            .onFailure {
                Log.e("FTPHandler::start") { "Could not initialize default FTPClient." }
                return false
            }
        return true
    }

    fun start() {
        if (!initClient())
            return

        val oneMinute = 1.toDuration(DurationUnit.MINUTES)
        val tempDir = File(Main.appDir, "Temp-FTP-Downloads")
        tempDir.mkdirs()
        launchThreaded {
            while (true) {
                val targets = getDBClient().executeGet { getTargets() }
                val ftpOptions = targets.getFTPOptions()
                for (option in ftpOptions) {
                    var client = defaultFTPClient.connect()
                    if (client == null) {
                        delay(oneMinute)
                        continue
                    }
                    if (!option.ftpConnectionString.isNullOrBlank()) {
                        client = runCatching { FTPClient.fromConnectionString(option.ftpConnectionString!!).connect()!! }.onFailure {
                            Log.e("FTPHandler::start") { "Could not initialize custom FTPClient with connectionstring: ${option.ftpConnectionString}" }
                        }.getOrNull() ?: continue
                    }
                    val results = checkDir(option.sourcePath!!, option, targets, client).filter { it.second is ParseResult.OK }
                    for ((filePair, parseResult) in results) {
                        val result = parseResult as ParseResult.OK
                        val outFile = File(tempDir, File(filePair.first).name)
                        client.downloadFile(filePair.first, outFile, filePair.second)
                        if (outFile.exists())
                            handleFile(outFile, result.target, result.option)
                        delay(10000)
                    }
                    client.disconnect()
                    delay(10000)
                }
            }
        }
    }

    fun checkDir(
        dir: String,
        option: DownloadableOption,
        targets: List<DownloaderTarget>,
        client: FTPClient
    ): List<Pair<Pair<String, Long>, ParseResult>> {
        val remoteDir = if (dir.trim().startsWith("/")) dir.trim() else "/FTP-Zugang Server/${dir.trim()}"

        val now = Clock.System.now().toJavaInstant()
        val cutOff = now.minusSeconds(20)
        val parent = option parentIn targets
        val files = client.listFiles(remoteDir).filter { it.isFile && it.isValid }

        return files.map {
            val fileStuff = ("$remoteDir/${it.name}" to it.size)
            return@map if (it.timestampInstant.isBefore(cutOff)) {
                fileStuff to ParseResult.DENIED(ParseDenyReason.FileIsTooNew)
            } else if (!option.ignoreDelay && it.timestampInstant.isBefore(now.minus(1, ChronoUnit.HOURS))) {
                fileStuff to ParseResult.DENIED(ParseDenyReason.PostIsTooOld)
            } else
                fileStuff to option.episodeWanted(it.name, parent)
        }
    }
}