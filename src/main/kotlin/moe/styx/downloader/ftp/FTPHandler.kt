package moe.styx.downloader.ftp

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import moe.styx.common.config.UnifiedConfig
import moe.styx.common.data.DownloadableOption
import moe.styx.common.data.DownloaderTarget
import moe.styx.common.util.launchGlobal
import moe.styx.db.tables.DownloaderTargetsTable
import moe.styx.downloader.dbClient
import moe.styx.downloader.downloaderConfig
import moe.styx.downloader.episodeWanted
import moe.styx.downloader.other.handleFile
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.utils.Log
import moe.styx.downloader.utils.getFTPOptions
import moe.styx.downloader.utils.parentIn
import org.jetbrains.exposed.sql.selectAll
import java.io.File
import java.time.temporal.ChronoUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object FTPHandler {
    lateinit var defaultFTPClient: FTPClient

    fun initClient(): Boolean {
        runCatching { defaultFTPClient = FTPClient.fromConnectionString(downloaderConfig.defaultFTPConnectionString) }
            .onFailure {
                Log.e("FTPHandler::start") { "Could not initialize default FTPClient." }
                return false
            }
        return true
    }

    fun start() {
        if (!initClient())
            return
        Log.i { "Starting FTP Handler" }
        val oneMinute = 1.toDuration(DurationUnit.MINUTES)
        val tempDir = File(UnifiedConfig.configFile.parentFile, "Temp-FTP-Downloads")
        tempDir.mkdirs()
        launchGlobal {
            while (true) {
                val targets = dbClient.transaction { DownloaderTargetsTable.query { selectAll().toList() } }
                val ftpOptions = targets.getFTPOptions()
                for (option in ftpOptions) {
                    var downloadedSomething = false
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
                    val results =
                        runCatching { checkDir(option.sourcePath!!, option, targets, client).filter { it.second is ParseResult.OK } }.getOrNull()
                            ?: emptyList()
                    for ((filePair, parseResult) in results) {
                        val result = parseResult as ParseResult.OK
                        Log.d("FTPHandler in dir: ${option.sourcePath}") { "Downloading: ${File(filePair.first).name}" }
                        val outFile = File(tempDir, File(filePair.first).name)
                        client.downloadFile(filePair.first, outFile, filePair.second)
                        downloadedSomething = true
                        if (outFile.exists())
                            handleFile(outFile, result.parentDir, result.target, result.option)
                        delay(10000)
                    }
                    client.disconnect()
                    if (downloadedSomething)
                        delay(10000)
                    else
                        delay(4000)
                }
                delay(6.toDuration(DurationUnit.MINUTES))
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
        val parentDir = runCatching {
            if (client.changeWorkingDirectory(remoteDir))
                client.printWorkingDirectory()
            else
                null
        }.onFailure {
            Log.e(exception = it) { "Could not verify last dir of: $remoteDir" }
        }.getOrNull()?.substringAfterLast("/")?.apply { trim() }

        val now = Clock.System.now().toJavaInstant()
        val cutOff = now.minusSeconds(20)
        val parent = option parentIn targets
        val files = client.listFiles(remoteDir).filter { it.isFile && it.isValid }
        return files.map {
            val fileStuff = ("$remoteDir/${it.name}" to it.size)
            return@map if (it.timestampInstant.isAfter(cutOff)) {
                fileStuff to ParseResult.DENIED(ParseDenyReason.FileIsTooNew)
            } else if (!option.ignoreDelay && it.timestampInstant.isBefore(now.minus(1, ChronoUnit.HOURS))) {
                fileStuff to ParseResult.DENIED(ParseDenyReason.PostIsTooOld)
            } else
                fileStuff to option.episodeWanted(it.name, parentDir, parent)
        }
    }
}