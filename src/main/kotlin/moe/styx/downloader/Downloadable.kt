package moe.styx.downloader

import moe.styx.db.StyxDBClient
import moe.styx.db.getEntries
import moe.styx.downloader.parsing.ParseDenyReason
import moe.styx.downloader.parsing.ParseResult
import moe.styx.downloader.parsing.parseEpisodeAndVersion
import moe.styx.types.DownloadableOption
import moe.styx.types.DownloaderTarget
import kotlin.math.abs

fun getDBClient(database: String = "Styx2"): StyxDBClient {
    return StyxDBClient(
        "com.mysql.cj.jdbc.Driver",
        "jdbc:mysql://${Main.config.dbConfig.ip}/$database?" +
                "useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=Europe/Berlin",
        Main.config.dbConfig.user,
        Main.config.dbConfig.pass
    )
}

fun DownloadableOption.episodeWanted(toMatch: String, parent: DownloaderTarget, rss: Boolean = false): ParseResult {
    if (!this.matches(toMatch, rss)) {
        return ParseResult.FAILED(ParseDenyReason.NoOptionMatched)
    }
    val (episode, version) = parseEpisodeAndVersion(toMatch, episodeOffset)
        ?: return ParseResult.FAILED(ParseDenyReason.InvalidEpisodeNumber)
    // Check if we already have the episode in the database
    val dbEpisode = getDBClient().executeGet { getEntries(mapOf("mediaID" to parent.mediaID)) }
        .find { it.entryNumber.toDoubleOrNull() == episode.toDoubleOrNull() }

    // Check what "option" the episode in the database corresponds to (if same and not a different version return false)
    val existingOptionVal = listOf(parent).matchesAny(dbEpisode?.originalName, false)?.second?.priority ?: -1
    if (dbEpisode != null && existingOptionVal == priority) {
        val existingData = parseEpisodeAndVersion(dbEpisode.originalName ?: "", episodeOffset)
        if (existingData != null && version <= existingData.second)
            return ParseResult.DENIED(ParseDenyReason.SameVersionPresent)
    }
    // Return false if we already have a better version of this episode
    if (priority < existingOptionVal)
        return ParseResult.DENIED(ParseDenyReason.BetterVersionPresent)
    // Return false if a "worse" version is required to exist (for muxing purposes perhaps) but doesn't yet
    if ((abs(existingOptionVal - priority) > 2 || existingOptionVal < 0) && waitForPrevious)
        return ParseResult.DENIED(ParseDenyReason.WaitingForPreviousOption)

    // Yay we need this file
    return ParseResult.OK(parent, this)
}

fun List<DownloaderTarget>.episodeWanted(toMatch: String, rss: Boolean = false): ParseResult {
    val (target, option) = matchesAny(toMatch, rss) ?: return ParseResult.FAILED(ParseDenyReason.NoOptionMatched)
    return option.episodeWanted(toMatch, target, rss)
}

fun List<DownloaderTarget>.matchesAny(toMatch: String?, rss: Boolean = false): Pair<DownloaderTarget, DownloadableOption>? {
    if (toMatch == null)
        return null
    for (target in this) {
        val match = target.options.find { it.matches(toMatch, rss) }
        if (match != null)
            return target to match
    }
    return null
}

fun DownloadableOption.matches(toMatch: String, rss: Boolean = false): Boolean {
    var regex = this.fileRegex.toRegex(RegexOption.IGNORE_CASE)
    if (rss) {
        regex = if (!this.rssRegex.isNullOrBlank()) this.rssRegex!!.toRegex(RegexOption.IGNORE_CASE) else
            this.fileRegex.replace("\\.mkv", "").replace(".mkv", "").toRegex(RegexOption.IGNORE_CASE)
    }
    return regex.find(if (!(toMatch.endsWith(".mkv") || toMatch.endsWith(".mka")) && !rss) "$toMatch.mkv" else toMatch) != null
}