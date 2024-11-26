package moe.styx.downloader

import moe.styx.common.data.DownloadableOption
import moe.styx.common.data.DownloaderTarget
import moe.styx.common.data.SourceType
import moe.styx.common.extension.equalsAny
import moe.styx.db.tables.MediaEntryTable
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.parsing.parseEpisodeAndVersion
import moe.styx.downloader.utils.RegexCollection
import org.jetbrains.exposed.sql.selectAll
import java.io.File
import kotlin.math.abs

val specialNumbers = listOf("2.0", "5.1", "7.1", "6.0", "5.0")

fun DownloadableOption.episodeWanted(toMatch: String, parentDir: String?, parent: DownloaderTarget, rss: Boolean = false): ParseResult {
    if (!this.matches(toMatch, parentDir, rss)) {
        return ParseResult.FAILED(ParseDenyReason.NoOptionMatched)
    }
    // Ignore stuff like "One Piece - Egghead SP1" or "One Piece E1078.5"
    if (downloaderConfig.ignoreSpecialsAndPoint5) {
        val matches = RegexCollection.specialEpisodeRegex.findAll(toMatch)
        for (match in matches) {
            val group = match.groups["num"]?.value ?: continue
            if (!group.trim().equalsAny(specialNumbers)) {
                return ParseResult.DENIED(ParseDenyReason.IsSpecialOrFiller)
            }
        }
    }

    val (episode, version) = parseEpisodeAndVersion(toMatch, episodeOffset)
        ?: return ParseResult.FAILED(ParseDenyReason.InvalidEpisodeNumber)

    // Another specials check, just to be safe
    if (downloaderConfig.ignoreSpecialsAndPoint5 && episode.toIntOrNull() == null)
        return ParseResult.DENIED(ParseDenyReason.IsSpecialOrFiller)

    // Check if we already have the episode in the database
    val dbEpisode = runCatching {
        dbClient.transaction {
            MediaEntryTable.query { selectAll().where { mediaID eq parent.mediaID }.toList() }
        }.find {
            it.entryNumber.toDoubleOrNull() == episode.toDoubleOrNull()
        }
    }.onFailure {
        it.printStackTrace()
        return ParseResult.DENIED(ParseDenyReason.DatabaseConnectionFailed)
    }.getOrNull()

    // Check what "option" the episode in the database corresponds to (if same and not a different version return false)
    val existingOptionVal = listOf(parent).matchesAny(dbEpisode?.originalName, dbEpisode?.originalParentFolder, false)?.second?.priority ?: -1
    if (dbEpisode != null && existingOptionVal == priority) {
        val existingData = parseEpisodeAndVersion(dbEpisode.originalName ?: "", episodeOffset)
        if (existingData != null && version <= existingData.second && File(dbEpisode.filePath).exists())
            return ParseResult.DENIED(ParseDenyReason.SameVersionPresent)
    }
    // Return false if we already have a better version of this episode
    if (priority < existingOptionVal)
        return ParseResult.DENIED(ParseDenyReason.BetterVersionPresent)
    // Return false if a "worse" version is required to exist (for muxing purposes perhaps) but doesn't yet
    val diff = abs(existingOptionVal - priority)
    if ((diff > 1 || existingOptionVal < 0) && waitForPrevious)
        return ParseResult.DENIED(ParseDenyReason.WaitingForPreviousOption)

    // Yay we need this file
    return ParseResult.OK(parent, this, parentDir)
}

fun List<DownloaderTarget>.episodeWanted(toMatch: String, parentDir: String?, rss: Boolean = false): ParseResult {
    val (target, option) = matchesAny(toMatch, parentDir, rss) ?: return ParseResult.FAILED(ParseDenyReason.NoOptionMatched)
    return option.episodeWanted(toMatch, parentDir, target, rss)
}

fun List<DownloaderTarget>.matchesAny(toMatch: String?, parentDir: String?, rss: Boolean = false): Pair<DownloaderTarget, DownloadableOption>? {
    if (toMatch == null)
        return null
    for (target in this) {
        val match = target.options.find { it.matches(toMatch, parentDir, rss) }
        if (match != null)
            return target to match
    }
    return null
}

fun DownloadableOption.matches(toMatch: String, parentDir: String?, rss: Boolean = false): Boolean {
    val toMatch = toMatch.replace(RegexCollection.repackRegex, "")
    var regex = this.fileRegex.toRegex(RegexOption.IGNORE_CASE)
    if (rss) {
        regex = if (!this.rssRegex.isNullOrBlank()) this.rssRegex!!.toRegex(RegexOption.IGNORE_CASE) else
            this.fileRegex.replace("\\.mkv", "").replace(".mkv", "").toRegex(RegexOption.IGNORE_CASE)
    }
    val nameMatches = regex.find(if (!(toMatch.endsWith(".mkv") || toMatch.endsWith(".mka")) && !rss) "$toMatch.mkv" else toMatch) != null
    val parentMatches =
        if (this.ignoreParentFolder || this.source != SourceType.FTP || parentDir == null)
            true
        else
            this.sourcePath?.contains(parentDir, true) == true

    return nameMatches && parentMatches
}