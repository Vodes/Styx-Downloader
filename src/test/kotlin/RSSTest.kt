import moe.styx.common.data.DownloadableOption
import moe.styx.common.data.DownloaderTarget
import moe.styx.common.data.SourceType
import moe.styx.downloader.ftp.FTPHandler
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.rss.RSSHandler
import java.util.*
import kotlin.test.assertTrue

object RSSTest {

    fun testRSSFeed() {
        val option = DownloadableOption(0, "\\[SubsPlease\\].*(Boku no Hero Academia).*\\(1080p\\).*\\.mkv", SourceType.TORRENT)
        val target = DownloaderTarget(UUID.randomUUID().toString().uppercase(), mutableListOf(option))
        val test = RSSHandler.checkFeed("%example% subsplease hero academia", listOf(option), listOf(target))

        assertTrue("Torrent request did not match anything!") {
            test.find {
                val value = (it.second as? ParseResult.DENIED)?.parseFailReason
                value != null && value != ParseDenyReason.NoOptionMatched
            } != null
        }
    }

    fun testFTPStuff() {
        val option = DownloadableOption(
            0,
            "(Dandadan) E\\d+.* \\[1080p\\]\\[AAC\\].*\\.mkv",
            SourceType.FTP,
            sourcePath = "/FTP-Zugang Server/2024-4 Fall/Dandadan (DAN DA DAN) [JapDub,GerEngSub,CR]",
            ignoreDelay = true
        )
        val option2 = DownloadableOption(
            1,
            "(Dandadan) E\\d+.* \\[1080p\\]\\[AAC\\].*\\.mkv",
            SourceType.FTP,
            sourcePath = "/FTP-Zugang Server/2024-4 Fall/Dandadan (DAN DA DAN) [GerJapDub,GerEngSub,CR+ADN]",
            ignoreDelay = true
        )
        val target = DownloaderTarget("747EB25D-664C-4EDF-804E-647FA57F036D", mutableListOf(option, option2))
        FTPHandler.initClient()
        FTPHandler.defaultFTPClient.connect()
        FTPHandler.checkDir(option.sourcePath!!, option, listOf(target), FTPHandler.defaultFTPClient)
        FTPHandler.checkDir(option2.sourcePath!!, option, listOf(target), FTPHandler.defaultFTPClient)
        FTPHandler.defaultFTPClient.disconnect()
    }
}