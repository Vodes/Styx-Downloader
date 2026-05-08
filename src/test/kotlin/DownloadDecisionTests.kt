import moe.styx.common.data.DownloadableOption
import moe.styx.common.data.DownloaderTarget
import moe.styx.downloader.ExistingDownloadState
import moe.styx.downloader.getDownloadDecision
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DownloadDecisionTests {
    private val target = DownloaderTarget("MEDIA")

    @Test
    fun sameOrLowerVersionOfExistingOptionIsDeniedWhenFileExists() {
        val option = DownloadableOption(priority = 1)

        val denied = assertIs<ParseResult.DENIED>(
            option.getDownloadDecision(
                version = 2,
                parent = target,
                parentDir = null,
                existingState = ExistingDownloadState(optionPriority = 1, version = 2, fileExists = true)
            )
        )

        assertEquals(ParseDenyReason.SameVersionPresent, denied.parseFailReason)
    }

    @Test
    fun sameVersionIsAcceptedWhenPreviousFileIsMissing() {
        val option = DownloadableOption(priority = 1)

        val result = option.getDownloadDecision(
            version = 2,
            parent = target,
            parentDir = null,
            existingState = ExistingDownloadState(optionPriority = 1, version = 2, fileExists = false)
        )

        assertIs<ParseResult.OK>(result)
    }

    @Test
    fun lowerPriorityThanExistingOptionIsDenied() {
        val option = DownloadableOption(priority = 0)

        val denied = assertIs<ParseResult.DENIED>(
            option.getDownloadDecision(
                version = 0,
                parent = target,
                parentDir = null,
                existingState = ExistingDownloadState(optionPriority = 1)
            )
        )

        assertEquals(ParseDenyReason.BetterVersionPresent, denied.parseFailReason)
    }

    @Test
    fun waitForPreviousDeniesWhenNoAdjacentLowerPriorityExists() {
        val option = DownloadableOption(priority = 2, waitForPrevious = true)

        val denied = assertIs<ParseResult.DENIED>(
            option.getDownloadDecision(
                version = 0,
                parent = target,
                parentDir = null,
                existingState = ExistingDownloadState(optionPriority = -1)
            )
        )

        assertEquals(ParseDenyReason.WaitingForPreviousOption, denied.parseFailReason)
    }

    @Test
    fun waitForPreviousAllowsAdjacentLowerPriorityOption() {
        val option = DownloadableOption(priority = 2, waitForPrevious = true)

        val result = option.getDownloadDecision(
            version = 0,
            parent = target,
            parentDir = null,
            existingState = ExistingDownloadState(optionPriority = 1)
        )

        assertIs<ParseResult.OK>(result)
    }

    @Test
    fun newerVersionOfSameOptionIsAccepted() {
        val option = DownloadableOption(priority = 1)

        val result = option.getDownloadDecision(
            version = 3,
            parent = target,
            parentDir = null,
            existingState = ExistingDownloadState(optionPriority = 1, version = 2, fileExists = true)
        )

        assertIs<ParseResult.OK>(result)
    }
}
