import moe.styx.common.data.DownloadableOption
import moe.styx.common.data.DownloaderTarget
import moe.styx.common.data.SourceType
import moe.styx.common.data.TokenGroup
import moe.styx.common.data.TokenMatchMethod
import moe.styx.common.data.TokenMatchType
import moe.styx.common.data.TokenTarget
import moe.styx.downloader.getExistingOptionPriority
import moe.styx.downloader.matches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadableMatchingTests {

    @Test
    fun regexRssFallbackMatchesTitleWithoutExtension() {
        val option = DownloadableOption(
            0,
            "\\[SubsPlease\\].*(Boku no Hero Academia).*\\(1080p\\).*\\.mkv",
            SourceType.TORRENT
        )

        assertTrue(option.matches("[SubsPlease] Boku no Hero Academia - 01 (1080p)", null, true))
    }

    @Test
    fun tokenGroupsMatchSceneAndFansubNames() {
        val option = DownloadableOption(
            source = SourceType.TORRENT,
            useTokens = true,
            tokenGroups = listOf(
                TokenGroup(listOf("Erai", "VARYG"), TokenMatchMethod.CONTAINS, TokenMatchType.ANY, TokenTarget.BOTH),
                TokenGroup(listOf("CR"), TokenMatchMethod.CONTAINS, TokenMatchType.ANY, TokenTarget.BOTH)
            )
        )

        assertTrue(option.matches("[Erai-raws] Dorohedoro Season 2 - 08 [1080p CR WEB-DL AVC AAC][MultiSub].mkv", null))
        assertTrue(option.matches("Dorohedoro.S02E08.1080p.CR.WEB-DL.DUAL.AAC2.0.H.264-VARYG.mkv", null))
        assertFalse(option.matches("[Erai-raws] Dorohedoro Season 2 - 08 [1080p NF WEB-DL AVC EAC3][MultiSub].mkv", null))
        assertFalse(option.matches("Dorohedoro.S02E08.1080p.NF.WEB-DL.DUAL.DDP5.1.H.264-VARYG.mkv", null))
    }

    @Test
    fun tokenTargetFilteringWorksForRssAndFiles() {
        val option = DownloadableOption(
            source = SourceType.TORRENT,
            useTokens = true,
            tokenGroups = listOf(
                TokenGroup(listOf("animetosho"), TokenMatchMethod.CONTAINS, TokenMatchType.ANY, TokenTarget.RSS),
                TokenGroup(listOf("VARYG"), TokenMatchMethod.CONTAINS, TokenMatchType.ANY, TokenTarget.FILE)
            )
        )

        assertTrue(option.matches("Dorohedoro S02E08 animetosho", null, true))
        assertFalse(option.matches("Dorohedoro S02E08", null, true))
        assertTrue(option.matches("Dorohedoro.S02E08.1080p.CR.WEB-DL.H.264-VARYG.mkv", null))
        assertFalse(option.matches("Dorohedoro.S02E08.1080p.CR.WEB-DL.H.264.mkv", null))
    }

    @Test
    fun ftpTokenMatchesStillRespectParentFolder() {
        val option = DownloadableOption(
            source = SourceType.FTP,
            sourcePath = "/FTP-Zugang Server/2024-4 Fall/Dandadan [CR]",
            useTokens = true,
            tokenGroups = listOf(TokenGroup(listOf("Dandadan"), TokenMatchMethod.CONTAINS, TokenMatchType.ANY, TokenTarget.FILE))
        )

        assertTrue(option.matches("Dandadan E01 [1080p].mkv", "Dandadan [CR]"))
        assertFalse(option.matches("Dandadan E01 [1080p].mkv", "Dandadan [ADN]"))
    }

    @Test
    fun invalidRegexTokenDoesNotMatch() {
        val option = DownloadableOption(
            source = SourceType.TORRENT,
            useTokens = true,
            tokenGroups = listOf(TokenGroup(listOf("["), TokenMatchMethod.REGEX, TokenMatchType.ANY, TokenTarget.BOTH))
        )

        assertFalse(option.matches("Anything at all", null))
    }

    @Test
    fun existingOptionPriorityUsesTheSameMatcher() {
        val lowPriority = DownloadableOption(
            priority = 0,
            source = SourceType.TORRENT,
            useTokens = true,
            tokenGroups = listOf(TokenGroup(listOf("SubsPlease"), TokenMatchMethod.CONTAINS, TokenMatchType.ANY, TokenTarget.BOTH))
        )
        val highPriority = DownloadableOption(
            priority = 1,
            source = SourceType.TORRENT,
            useTokens = true,
            tokenGroups = listOf(TokenGroup(listOf("VARYG"), TokenMatchMethod.CONTAINS, TokenMatchType.ANY, TokenTarget.BOTH))
        )
        val target = DownloaderTarget("MEDIA", mutableListOf(lowPriority, highPriority))

        assertEquals(1, listOf(target).getExistingOptionPriority("Dorohedoro.S02E08.1080p.CR.WEB-DL.H.264-VARYG.mkv", null))
        assertEquals(0, listOf(target).getExistingOptionPriority("[SubsPlease] Dorohedoro - 08 (1080p).mkv", null))
        assertEquals(-1, listOf(target).getExistingOptionPriority("No match here", null))
    }
}
