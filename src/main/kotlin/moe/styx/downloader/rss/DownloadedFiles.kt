package moe.styx.downloader.rss

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import moe.styx.common.extension.anyEquals
import moe.styx.common.util.launchThreaded
import moe.styx.db.tables.DownloaderTargetsTable
import moe.styx.downloader.Main
import moe.styx.downloader.dbClient
import moe.styx.downloader.episodeWanted
import moe.styx.downloader.other.handleFile
import moe.styx.downloader.parsing.ParseResult
import org.jetbrains.exposed.sql.selectAll
import java.io.File

fun startCheckingLocal() = launchThreaded {
    val tempDir = File(Main.config.rssConfig.defaultNonSeedDir)
    val seedDir = File(Main.config.rssConfig.defaultSeedDir)
    val copied = mutableListOf<String>()
    while (true) {
        val targets = dbClient.transaction { DownloaderTargetsTable.query { selectAll().toList() } }
        if (tempDir.exists() && tempDir.isDirectory) {
            listFiles(tempDir).forEach {
                val parseResult = targets.episodeWanted(it.name)
                if (parseResult !is ParseResult.OK)
                    return@forEach
                handleFile(it, parseResult.target, parseResult.option)
            }
        }
        if (seedDir.exists() && seedDir.isDirectory) {
            listFiles(seedDir).forEach {
                val parseResult = targets.episodeWanted(it.name)
                if (parseResult !is ParseResult.OK || copied.anyEquals(it.name))
                    return@forEach
                val copy = it.copyTo(File(seedDir.parentFile, it.name), overwrite = true)
                copied.add(it.name)
                if (handleFile(copy, parseResult.target, parseResult.option))
                    copy.delete()
            }
        }
        if (copied.size > 30)
            copied.clear()
        delay(10000)
    }
}

fun listFiles(dir: File): List<File> {
    val cutOff = Clock.System.now().toEpochMilliseconds() - 30000
    return dir.walk().maxDepth(2).filter { it.isFile && it.lastModified() < cutOff }.toSortedSet().toList()
}